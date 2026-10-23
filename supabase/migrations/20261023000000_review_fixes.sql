-- Security, integrity and performance review fixes (23 October 2026).
-- Idempotent and data-preserving: safe to re-run and to apply to the hosted database.
-- No client writes stories, sources or reports directly (the edge functions do, with the
-- service role), so the member insert policies for those tables are removed.

-- ================================================================ stories: announce when published
-- submit-source inserts a flagged placeholder story before any AI work and deletes it again when
-- the link joins an existing story. Announcing on insert sent members a "new story" push for a
-- story that vanished seconds later. A story is now announced the first time it is unflagged.
do $$
begin
  if not exists (select 1 from information_schema.columns
                 where table_schema = 'public' and table_name = 'stories' and column_name = 'announced_at') then
    alter table public.stories add column announced_at timestamptz;
    -- every existing story was announced when it was inserted
    update public.stories set announced_at = created_at;
  end if;
end $$;

create or replace function public.mark_story_announced()
returns trigger language plpgsql set search_path = '' as $$
begin
  if not new.flagged and new.announced_at is null then
    new.announced_at := now();
  end if;
  return new;
end $$;

create or replace function public.notify_on_story()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if new.announced_at is null or (tg_op = 'UPDATE' and old.announced_at is not null) then
    return new;
  end if;
  insert into public.notifications (user_id, group_id, kind, story_id)
  select gm.user_id, new.group_id, 'new_story', new.id
  from public.group_members gm
  where gm.group_id = new.group_id
    and (new.created_by is null or gm.user_id <> new.created_by)
    and public.notifications_enabled(gm.user_id, new.group_id, 'new_stories');
  return new;
end $$;

drop trigger if exists stories_mark_announced on public.stories;
create trigger stories_mark_announced before insert or update of flagged on public.stories
  for each row execute function public.mark_story_announced();
drop trigger if exists on_story_created on public.stories;
create trigger on_story_created after insert or update of flagged on public.stories
  for each row execute function public.notify_on_story();

-- ================================================================ triggers
-- bump_story_activity ran as the inserting member, who has no UPDATE policy on stories, so new
-- messages silently never moved their story's last_activity_at (the front page lead slot).
create or replace function public.bump_story_activity()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.stories set last_activity_at = now() where id = new.story_id;
  return new;
end $$;

-- Replies notify only a parent in the same story whose author is still in the paper. Mentions use
-- a plain substring match: display names containing % or _ were LIKE wildcards, so a member named
-- "%" was mentioned by every message containing "@". One set-based insert instead of a loop.
create or replace function public.notify_on_message()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  g uuid;
  parent_author uuid;
begin
  select s.group_id into g from public.stories s where s.id = new.story_id;

  if new.reply_to is not null then
    select m.author_id into parent_author from public.messages m
      where m.id = new.reply_to and m.story_id = new.story_id;
    if parent_author is not null and parent_author <> new.author_id
       and exists (select 1 from public.group_members gm where gm.group_id = g and gm.user_id = parent_author)
       and public.notifications_enabled(parent_author, g, 'direct_replies') then
      insert into public.notifications (user_id, group_id, kind, story_id, message_id)
      values (parent_author, g, 'direct_reply', new.story_id, new.id);
    end if;
  end if;

  if new.body is not null and strpos(new.body, '@') > 0 then
    insert into public.notifications (user_id, group_id, kind, story_id, message_id)
    select gm.user_id, g, 'mention', new.story_id, new.id
    from public.group_members gm
    join public.profiles pr on pr.id = gm.user_id
    where gm.group_id = g and gm.user_id <> new.author_id and pr.display_name <> ''
      and strpos(lower(new.body), lower('@' || pr.display_name)) > 0
      and public.notifications_enabled(gm.user_id, g, 'mentions');
  end if;

  return new;
end $$;

-- A reply must quote a message from the same story: otherwise a member could reply to a message
-- in another paper and notify its author. (A trigger, since a messages policy cannot query messages.)
create or replace function public.check_reply_same_story()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if new.reply_to is not null and not exists (
       select 1 from public.messages m where m.id = new.reply_to and m.story_id = new.story_id) then
    raise exception 'A reply must quote a message in the same story' using errcode = '23514';
  end if;
  return new;
end $$;

drop trigger if exists messages_reply_same_story on public.messages;
create trigger messages_reply_same_story before insert or update of reply_to, story_id on public.messages
  for each row execute function public.check_reply_same_story();

-- Signup metadata is client-supplied: keep the generated name within the profile limits.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  name text := left(coalesce(nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''),
                             nullif(split_part(new.email, '@', 1), ''), 'Member'), 80);
begin
  insert into public.profiles (id, display_name, monogram)
  values (new.id, name, upper(left(name, 1)));
  return new;
end $$;

-- ================================================================ functions
-- notifications_enabled is SECURITY DEFINER and was callable by any signed-in (or anonymous)
-- client through /rest/v1/rpc, revealing any member's preferences. Only triggers (as owner)
-- and send-notification (service role) need it.
revoke all on function public.notifications_enabled(uuid, uuid, text) from public, anon, authenticated;
grant execute on function public.notifications_enabled(uuid, uuid, text) to service_role;

-- Trigger functions are never meant to be called through the API.
revoke all on function public.bump_story_activity() from public, anon, authenticated;
revoke all on function public.mark_story_announced() from public, anon, authenticated;
revoke all on function public.check_reply_same_story() from public, anon, authenticated;
revoke all on function public.notify_on_story() from public, anon, authenticated;
revoke all on function public.notify_on_message() from public, anon, authenticated;
revoke all on function public.handle_new_user() from public, anon, authenticated;
revoke all on function public.handle_profile_deleted() from public, anon, authenticated;

-- Invite lookups compare lower(invite_code): index that expression, and reject oversized input
-- before it reaches the query.
create or replace function public.join_group_with_invite(code text)
returns uuid
language plpgsql security definer
set search_path = ''
as $$
declare
  target_group uuid;
begin
  if (select auth.uid()) is null then
    raise exception 'Sign in before joining a paper.';
  end if;
  if code is null or length(code) > 64 then
    raise exception 'This invite code is not valid.';
  end if;
  select g.id into target_group from public.groups g
    where lower(g.invite_code) = lower(trim(code))
    order by g.created_at
    limit 1;
  if target_group is null then
    raise exception 'This invite code is not valid.';
  end if;
  insert into public.group_members (group_id, user_id, role)
    values (target_group, (select auth.uid()), 'member')
    on conflict (group_id, user_id) do nothing;
  return target_group;
end;
$$;

create or replace function public.group_preview_for_invite(code text)
returns table (id uuid, name text)
language plpgsql security definer
set search_path = ''
as $$
begin
  if (select auth.uid()) is null then
    raise exception 'Sign in before viewing an invitation.';
  end if;
  if code is null or length(code) > 64 then
    return;
  end if;
  return query select g.id, g.name from public.groups g
    where lower(g.invite_code) = lower(trim(code))
    order by g.created_at
    limit 1;
end;
$$;

revoke all on function public.join_group_with_invite(text) from public, anon;
revoke all on function public.group_preview_for_invite(text) from public, anon;
grant execute on function public.join_group_with_invite(text) to authenticated;
grant execute on function public.group_preview_for_invite(text) to authenticated;

-- ================================================================ column privileges
-- Clients could choose a paper's invite_code (e.g. an upper-case copy of another paper's code,
-- which the case-insensitive lookup could resolve to instead), backdate messages, and rewrite
-- profile ids/timestamps. Each client write sends only the columns granted here.
revoke insert on public.groups from anon, authenticated;
grant insert (id, name, created_by) on public.groups to authenticated;

revoke update on public.profiles from anon, authenticated;
grant update (display_name, monogram) on public.profiles to authenticated;

revoke insert on public.group_members from anon, authenticated;
grant insert (group_id, user_id, role) on public.group_members to authenticated;

revoke insert on public.messages from anon, authenticated;
grant insert (id, story_id, author_id, body, reply_to) on public.messages to authenticated;

revoke insert on public.message_media from anon, authenticated;
grant insert (message_id, kind, url, alt_text, position) on public.message_media to authenticated;

revoke insert on public.reactions from anon, authenticated;
grant insert (message_id, user_id, emoji) on public.reactions to authenticated;

-- ================================================================ input limits
-- NOT VALID: enforced for new and updated rows only, so existing data never blocks the migration.
do $$
declare
  c record;
begin
  for c in select * from (values
    ('public.profiles', 'profiles_display_name_length', 'check (char_length(display_name) between 1 and 80)'),
    ('public.profiles', 'profiles_monogram_length', 'check (char_length(monogram) between 1 and 16)'),
    ('public.groups', 'groups_name_length', 'check (char_length(name) between 1 and 200)'),
    ('public.messages', 'messages_body_length', 'check (char_length(body) <= 20000)'),
    ('public.message_media', 'message_media_url_length', 'check (char_length(url) <= 2048)'),
    ('public.message_media', 'message_media_alt_text_length', 'check (char_length(alt_text) <= 1000)'),
    ('public.reactions', 'reactions_emoji_length', 'check (char_length(emoji) between 1 and 32)')
  ) as t (tbl, name, def) loop
    if not exists (select 1 from pg_constraint where conname = c.name and conrelid = c.tbl::regclass) then
      execute format('alter table %s add constraint %I %s not valid', c.tbl, c.name, c.def);
    end if;
  end loop;
end $$;

-- ================================================================ RLS policies
-- Recreated with (select auth.uid()) so the uid is evaluated once per query, not once per row,
-- and scoped to the authenticated role.

-- profiles
drop policy if exists "profiles readable by groupmates" on public.profiles;
create policy "profiles readable by groupmates" on public.profiles for select to authenticated using (
  id = (select auth.uid()) or exists (
    select 1 from public.group_members a
    join public.group_members b on a.group_id = b.group_id
    where a.user_id = (select auth.uid()) and b.user_id = public.profiles.id));
drop policy if exists "profiles editable by self" on public.profiles;
create policy "profiles editable by self" on public.profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- groups
drop policy if exists "groups readable by members" on public.groups;
create policy "groups readable by members" on public.groups for select to authenticated
  using (public.is_group_member(id));
drop policy if exists "groups readable by creator" on public.groups;
create policy "groups readable by creator" on public.groups for select to authenticated
  using (created_by = (select auth.uid()));
drop policy if exists "groups creatable by authenticated" on public.groups;
create policy "groups creatable by authenticated" on public.groups for insert to authenticated
  with check (created_by = (select auth.uid()));

-- group members
drop policy if exists "group members readable by groupmates" on public.group_members;
create policy "group members readable by groupmates" on public.group_members for select to authenticated
  using (public.is_group_member(group_id));
drop policy if exists "creator joins own group as owner" on public.group_members;
create policy "creator joins own group as owner" on public.group_members for insert to authenticated with check (
  user_id = (select auth.uid()) and role = 'owner'
  and exists (select 1 from public.groups g where g.id = group_id and g.created_by = (select auth.uid())));
drop policy if exists "members can update own membership row" on public.group_members;
create policy "members can update own membership row" on public.group_members for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- stories: written only by the edge functions (service role)
drop policy if exists "stories creatable by members" on public.stories;
drop policy if exists "stories readable by members" on public.stories;
create policy "stories readable by members" on public.stories for select to authenticated
  using (public.is_group_member(group_id));

drop policy if exists "story history readable by members" on public.story_history;
create policy "story history readable by members" on public.story_history for select to authenticated using (
  exists (select 1 from public.stories s where s.id = story_id and public.is_group_member(s.group_id)));

-- sources: submitted only through submit-source. The removed insert policy let a member file a
-- source in their own paper against another paper's story_id, with any extraction fields.
drop policy if exists "sources submittable by members" on public.sources;
drop policy if exists "sources readable by members" on public.sources;
create policy "sources readable by members" on public.sources for select to authenticated
  using (public.is_group_member(group_id));
drop policy if exists "sources deletable by submitter" on public.sources;
create policy "sources deletable by submitter" on public.sources for delete to authenticated
  using (submitted_by = (select auth.uid()) and public.is_group_member(group_id));

-- messages (a reply quoting a message from another story is rejected by messages_reply_same_story)
drop policy if exists "messages readable by members" on public.messages;
create policy "messages readable by members" on public.messages for select to authenticated using (
  exists (select 1 from public.stories s where s.id = story_id and public.is_group_member(s.group_id)));
drop policy if exists "messages creatable by members" on public.messages;
create policy "messages creatable by members" on public.messages for insert to authenticated with check (
  author_id = (select auth.uid())
  and exists (select 1 from public.stories s where s.id = story_id and public.is_group_member(s.group_id)));

-- media: either an external http(s) URL (GIFs) or an object under {group_id}/{own uid}/
drop policy if exists "media readable by members" on public.message_media;
create policy "media readable by members" on public.message_media for select to authenticated using (
  exists (select 1 from public.messages m
          join public.stories s on s.id = m.story_id
          where m.id = message_id and public.is_group_member(s.group_id)));
drop policy if exists "media attachable by author" on public.message_media;
create policy "media attachable by author" on public.message_media for insert to authenticated with check (
  exists (select 1 from public.messages m
          join public.stories s on s.id = m.story_id
          where m.id = message_id and m.author_id = (select auth.uid()) and public.is_group_member(s.group_id)
            and (url ~* '^https?://'
                 or (split_part(url, '/', 1) = s.group_id::text
                     and split_part(url, '/', 2) = (select auth.uid())::text))));

-- reactions
drop policy if exists "reactions readable by members" on public.reactions;
create policy "reactions readable by members" on public.reactions for select to authenticated using (
  exists (select 1 from public.messages m
          join public.stories s on s.id = m.story_id
          where m.id = message_id and public.is_group_member(s.group_id)));
drop policy if exists "reactions creatable by members" on public.reactions;
create policy "reactions creatable by members" on public.reactions for insert to authenticated with check (
  user_id = (select auth.uid())
  and exists (select 1 from public.messages m
              join public.stories s on s.id = m.story_id
              where m.id = message_id and public.is_group_member(s.group_id)));
drop policy if exists "reactions removable by self" on public.reactions;
create policy "reactions removable by self" on public.reactions for delete to authenticated
  using (user_id = (select auth.uid()));

-- reports: filed only through report-story, which also regenerates the story
drop policy if exists "reports creatable by members" on public.reports;
drop policy if exists "reports readable by members" on public.reports;
create policy "reports readable by members" on public.reports for select to authenticated using (
  exists (select 1 from public.stories s where s.id = story_id and public.is_group_member(s.group_id)));

-- notification preferences: own rows, for papers the member belongs to
drop policy if exists "preferences are private" on public.notification_preferences;
create policy "preferences are private" on public.notification_preferences for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()) and public.is_group_member(group_id));

-- notifications
drop policy if exists "notifications are private" on public.notifications;
create policy "notifications are private" on public.notifications for select to authenticated
  using (user_id = (select auth.uid()));
drop policy if exists "notifications markable read by self" on public.notifications;
create policy "notifications markable read by self" on public.notifications for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- ================================================================ storage
-- Media is images and video only (no HTML or other active content served from the storage
-- domain), with a per-object cap above the apps' 20 MB video limit.
update storage.buckets
  set file_size_limit = 26214400, allowed_mime_types = array['image/*', 'video/*']
  where id = 'media';

drop policy if exists "group media readable by members" on storage.objects;
create policy "group media readable by members" on storage.objects for select to authenticated using (
  bucket_id = 'media'
  and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
  and public.is_group_member(substring(name from 1 for 36)::uuid));

drop policy if exists "group media uploadable by members" on storage.objects;
create policy "group media uploadable by members" on storage.objects for insert to authenticated with check (
  bucket_id = 'media'
  and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
  and public.is_group_member(substring(name from 1 for 36)::uuid)
  and substring(name from 38 for 36) = (select auth.uid())::text);

-- ================================================================ indexes
-- Front page (stories by paper), submit-source candidates (latest 20 by activity), RLS lookups.
create index if not exists stories_group_activity_idx on public.stories (group_id, last_activity_at desc);
-- Memberships by user: sign-in routing, the profiles policy, user deletion.
create index if not exists group_members_user_idx on public.group_members (user_id, group_id);
-- Story page history; cascades when a placeholder story is deleted on every joined submission.
create index if not exists story_history_story_idx on public.story_history (story_id, created_at desc);
create index if not exists reports_story_idx on public.reports (story_id, status);
create index if not exists notifications_story_idx on public.notifications (story_id) where story_id is not null;
create index if not exists notifications_message_idx on public.notifications (message_id, user_id) where message_id is not null;
create index if not exists notifications_group_idx on public.notifications (group_id);
-- Rate limits in the edge functions, admin per-member counts, and ON DELETE actions for profiles.
create index if not exists sources_submitter_idx on public.sources (submitted_by, created_at desc);
create index if not exists reports_reporter_idx on public.reports (reporter_id, created_at desc);
create index if not exists messages_author_idx on public.messages (author_id, created_at desc);
create index if not exists messages_reply_to_idx on public.messages (reply_to) where reply_to is not null;
create index if not exists reactions_user_idx on public.reactions (user_id);
create index if not exists stories_created_by_idx on public.stories (created_by);
create index if not exists groups_created_by_idx on public.groups (created_by);
create index if not exists groups_invite_code_lower_idx on public.groups (lower(invite_code));
-- Admin usage pages: per-member and per-story cost.
create index if not exists ai_usage_user_idx on public.ai_usage (user_id, created_at desc) where user_id is not null;
create index if not exists ai_usage_story_idx on public.ai_usage (story_id) where story_id is not null;

-- Similarity is computed in submit-source over the paper's 20 most recent stories; nothing
-- queries these HNSW indexes, which only slowed every embedding write. The columns stay.
drop index if exists public.sources_embedding_idx;
drop index if exists public.stories_embedding_idx;
