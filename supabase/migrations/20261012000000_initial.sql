-- The Grapevine — initial schema (12 October 2026)
-- All group content is private: every table has RLS and access is limited to group members.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- profiles
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null,
  monogram text not null, -- members are monograms, never photos
  created_at timestamptz not null default now()
);

-- auto-create a profile on signup; display name/monogram can be edited later
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer as $$
declare
  name text := coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1));
begin
  insert into public.profiles (id, display_name, monogram)
  values (new.id, name, upper(left(name, 1)));
  return new;
end $$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------- groups
create table public.groups (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  invite_code text not null unique default encode(gen_random_bytes(6), 'hex'),
  created_by uuid not null references public.profiles (id),
  created_at timestamptz not null default now()
);

create table public.group_members (
  group_id uuid not null references public.groups (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  role text not null default 'member' check (role in ('owner', 'member')),
  joined_at timestamptz not null default now(),
  primary key (group_id, user_id)
);

-- ---------------------------------------------------------------- stories
create table public.stories (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups (id) on delete cascade,
  created_by uuid references public.profiles (id),
  title text not null default '',
  summary text not null default '',
  category text,
  flagged boolean not null default false, -- a member report is awaiting re-generation
  last_activity_at timestamptz not null default now(), -- drives the lead slot on the front page
  created_at timestamptz not null default now()
);

-- every generated version is kept; nothing is silently rewritten
create table public.story_history (
  id uuid primary key default gen_random_uuid(),
  story_id uuid not null references public.stories (id) on delete cascade,
  title text not null,
  summary text not null,
  category text,
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------- sources
create table public.sources (
  id uuid primary key default gen_random_uuid(),
  story_id uuid not null references public.stories (id) on delete cascade,
  group_id uuid not null references public.groups (id) on delete cascade,
  url text not null,
  domain text not null default '',
  publisher text not null default '',
  source_title text not null default '',
  excerpt text,
  image_url text,
  author text, -- X author handle
  source_published_at timestamptz,
  submitted_by uuid not null references public.profiles (id),
  submitter_comment text, -- the member's optional comment, always attributed
  extraction_status text not null default 'ok'
    check (extraction_status in ('ok', 'failed', 'paywalled', 'deleted')),
  created_at timestamptz not null default now(),
  -- exact duplicate URLs are never added twice to the same group
  -- (the submit flow normalizes the URL — trim, lowercase scheme/host, strip trailing slash — before insert)
  unique (group_id, url)
);

create index sources_story_idx on public.sources (story_id, created_at);

-- ---------------------------------------------------------------- discussion
create table public.messages (
  id uuid primary key default gen_random_uuid(),
  story_id uuid not null references public.stories (id) on delete cascade,
  author_id uuid not null references public.profiles (id) on delete cascade,
  body text,
  reply_to uuid references public.messages (id) on delete set null, -- flat quoted replies; no nesting
  created_at timestamptz not null default now()
);

create index messages_story_idx on public.messages (story_id, created_at);

create table public.message_media (
  id uuid primary key default gen_random_uuid(),
  message_id uuid not null references public.messages (id) on delete cascade,
  kind text not null check (kind in ('image', 'video', 'gif')),
  url text not null,
  alt_text text, -- accessible descriptions, required for GIFs
  position int not null default 0
  -- limits (max 10 images per message, video up to 20 MB) are enforced at upload time
);

create index message_media_message_idx on public.message_media (message_id, position);

create table public.reactions (
  id uuid primary key default gen_random_uuid(),
  message_id uuid not null references public.messages (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  emoji text not null,
  created_at timestamptz not null default now(),
  unique (message_id, user_id, emoji)
);

-- ---------------------------------------------------------------- reports
create table public.reports (
  id uuid primary key default gen_random_uuid(),
  story_id uuid not null references public.stories (id) on delete cascade,
  reporter_id uuid not null references public.profiles (id) on delete cascade,
  target text not null check (target in ('summary', 'title', 'category')),
  note text,
  status text not null default 'open' check (status in ('open', 'resolved')),
  created_at timestamptz not null default now(),
  resolved_at timestamptz
);

-- ---------------------------------------------------------------- notifications
create table public.notification_preferences (
  user_id uuid not null references public.profiles (id) on delete cascade,
  group_id uuid not null references public.groups (id) on delete cascade,
  new_stories boolean not null default true,
  direct_replies boolean not null default true,
  mentions boolean not null default true,
  digest boolean not null default false,
  muted boolean not null default false,
  primary key (user_id, group_id)
);

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  group_id uuid not null references public.groups (id) on delete cascade,
  kind text not null check (kind in ('new_story', 'direct_reply', 'mention', 'digest')),
  story_id uuid references public.stories (id) on delete cascade,
  message_id uuid references public.messages (id) on delete cascade,
  read boolean not null default false,
  created_at timestamptz not null default now()
);

create index notifications_user_idx on public.notifications (user_id, created_at desc);

-- ---------------------------------------------------------------- helpers + triggers
create or replace function public.is_group_member(g uuid)
returns boolean language sql stable security definer
as $$ select exists (select 1 from public.group_members m where m.group_id = g and m.user_id = auth.uid()) $$;

-- preference lookup with defaults (no row = on/on/on/off, not muted)
create or replace function public.notifications_enabled(u uuid, g uuid, which text)
returns boolean language sql stable security definer
as $$
  select not coalesce((select p.muted from public.notification_preferences p where p.user_id = u and p.group_id = g), false)
     and coalesce(
       (select case which
                 when 'new_stories' then p.new_stories
                 when 'direct_replies' then p.direct_replies
                 when 'mentions' then p.mentions
                 when 'digest' then p.digest
               end
          from public.notification_preferences p where p.user_id = u and p.group_id = g),
       case which when 'digest' then false else true end)
$$;

create or replace function public.bump_story_activity()
returns trigger language plpgsql as $$
begin
  update public.stories set last_activity_at = now() where id = new.story_id;
  return new;
end $$;

create trigger sources_bump_activity after insert on public.sources
  for each row execute function public.bump_story_activity();
create trigger messages_bump_activity after insert on public.messages
  for each row execute function public.bump_story_activity();

create or replace function public.notify_on_message()
returns trigger language plpgsql security definer as $$
declare
  g uuid;
  parent_author uuid;
  m record;
begin
  select group_id into g from public.stories where id = new.story_id;

  -- direct reply: notify the parent message's author
  if new.reply_to is not null then
    select author_id into parent_author from public.messages where id = new.reply_to;
    if parent_author is not null and parent_author <> new.author_id
       and public.notifications_enabled(parent_author, g, 'direct_replies') then
      insert into public.notifications (user_id, group_id, kind, story_id, message_id)
      values (parent_author, g, 'direct_reply', new.story_id, new.id);
    end if;
  end if;

  -- mentions: notify members whose display name appears as @name
  if new.body is not null then
    for m in
      select gm.user_id, pr.display_name
      from public.group_members gm
      join public.profiles pr on pr.id = gm.user_id
      where gm.group_id = g and gm.user_id <> new.author_id
    loop
      if new.body ilike '%@' || m.display_name || '%'
         and public.notifications_enabled(m.user_id, g, 'mentions') then
        insert into public.notifications (user_id, group_id, kind, story_id, message_id)
        values (m.user_id, g, 'mention', new.story_id, new.id);
      end if;
    end loop;
  end if;

  return new;
end $$;

create trigger on_message_created after insert on public.messages
  for each row execute function public.notify_on_message();

create or replace function public.notify_on_story()
returns trigger language plpgsql security definer as $$
begin
  insert into public.notifications (user_id, group_id, kind, story_id)
  select gm.user_id, new.group_id, 'new_story', new.id
  from public.group_members gm
  where gm.group_id = new.group_id
    and (new.created_by is null or gm.user_id <> new.created_by)
    and public.notifications_enabled(gm.user_id, new.group_id, 'new_stories');
  return new;
end $$;

create trigger on_story_created after insert on public.stories
  for each row execute function public.notify_on_story();

-- ---------------------------------------------------------------- RLS
alter table public.profiles enable row level security;
alter table public.groups enable row level security;
alter table public.group_members enable row level security;
alter table public.stories enable row level security;
alter table public.story_history enable row level security;
alter table public.sources enable row level security;
alter table public.messages enable row level security;
alter table public.message_media enable row level security;
alter table public.reactions enable row level security;
alter table public.reports enable row level security;
alter table public.notification_preferences enable row level security;
alter table public.notifications enable row level security;

-- profiles: visible to groupmates, editable by self
create policy "profiles readable by groupmates" on public.profiles for select using (
  id = auth.uid() or exists (
    select 1 from public.group_members a
    join public.group_members b on a.group_id = b.group_id
    where a.user_id = auth.uid() and b.user_id = public.profiles.id));
create policy "profiles editable by self" on public.profiles for update using (id = auth.uid());

-- groups: visible to members; creation open to authenticated users
create policy "groups readable by members" on public.groups for select using (public.is_group_member(id));
create policy "groups creatable by authenticated" on public.groups for insert with check (auth.uid() = created_by);

-- group members: visible to groupmates; invite joins go through an edge function (service role)
create policy "group members readable by groupmates" on public.group_members for select using (public.is_group_member(group_id));
create policy "creator joins own group as owner" on public.group_members for insert with check (
  auth.uid() = user_id and role = 'owner'
  and exists (select 1 from public.groups g where g.id = group_id and g.created_by = auth.uid()));

-- stories: visible to members; AI fields are written by edge functions (service role)
create policy "stories readable by members" on public.stories for select using (public.is_group_member(group_id));
create policy "stories creatable by members" on public.stories for insert with check (public.is_group_member(group_id));

create policy "story history readable by members" on public.story_history for select using (
  exists (select 1 from public.stories s where s.id = story_id and public.is_group_member(s.group_id)));

-- sources: visible to members; submitted by members
create policy "sources readable by members" on public.sources for select using (public.is_group_member(group_id));
create policy "sources submittable by members" on public.sources for insert with check (
  public.is_group_member(group_id) and auth.uid() = submitted_by);

-- messages
create policy "messages readable by members" on public.messages for select using (
  exists (select 1 from public.stories s where s.id = story_id and public.is_group_member(s.group_id)));
create policy "messages creatable by members" on public.messages for insert with check (
  auth.uid() = author_id
  and exists (select 1 from public.stories s where s.id = story_id and public.is_group_member(s.group_id)));

-- media
create policy "media readable by members" on public.message_media for select using (
  exists (select 1 from public.messages m
          join public.stories s on s.id = m.story_id
          where m.id = message_id and public.is_group_member(s.group_id)));
create policy "media attachable by author" on public.message_media for insert with check (
  exists (select 1 from public.messages m
          join public.stories s on s.id = m.story_id
          where m.id = message_id and m.author_id = auth.uid() and public.is_group_member(s.group_id)));

-- reactions
create policy "reactions readable by members" on public.reactions for select using (
  exists (select 1 from public.messages m
          join public.stories s on s.id = m.story_id
          where m.id = message_id and public.is_group_member(s.group_id)));
create policy "reactions creatable by members" on public.reactions for insert with check (
  auth.uid() = user_id
  and exists (select 1 from public.messages m
              join public.stories s on s.id = m.story_id
              where m.id = message_id and public.is_group_member(s.group_id)));
create policy "reactions removable by self" on public.reactions for delete using (auth.uid() = user_id);

-- reports
create policy "reports readable by members" on public.reports for select using (
  exists (select 1 from public.stories s where s.id = story_id and public.is_group_member(s.group_id)));
create policy "reports creatable by members" on public.reports for insert with check (
  auth.uid() = reporter_id
  and exists (select 1 from public.stories s where s.id = story_id and public.is_group_member(s.group_id)));

-- notification preferences: own row only
create policy "preferences are private" on public.notification_preferences for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- notifications: own only; rows are created by triggers (security definer)
create policy "notifications are private" on public.notifications for select using (auth.uid() = user_id);
create policy "notifications markable read by self" on public.notifications for update using (auth.uid() = user_id);

-- ---------------------------------------------------------------- storage
insert into storage.buckets (id, name, public) values ('media', 'media', false);

-- path convention: {group_id}/{uploader_id}/{filename}
create policy "group media readable by members" on storage.objects for select using (
  bucket_id = 'media'
  and name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}/'
  and public.is_group_member(substring(name from 1 for 36)::uuid));

create policy "group media uploadable by members" on storage.objects for insert with check (
  bucket_id = 'media'
  and name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}/'
  and public.is_group_member(substring(name from 1 for 36)::uuid)
  and substring(name from 38 for 36) = auth.uid()::text);
