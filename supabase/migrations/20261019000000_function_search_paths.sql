-- SECURITY DEFINER functions must not run with a mutable search_path:
-- a hostile schema earlier in the path could shadow pg_catalog lookups.
-- All bodies already reference schema-qualified names, so pinning is safe.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  name text := coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1));
begin
  insert into public.profiles (id, display_name, monogram)
  values (new.id, name, upper(left(name, 1)));
  return new;
end $$;

create or replace function public.is_group_member(g uuid)
returns boolean language sql stable security definer set search_path = ''
as $$ select exists (select 1 from public.group_members m where m.group_id = g and m.user_id = auth.uid()) $$;

create or replace function public.notifications_enabled(u uuid, g uuid, which text)
returns boolean language sql stable security definer set search_path = ''
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

create or replace function public.notify_on_message()
returns trigger language plpgsql security definer set search_path = '' as $$
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

create or replace function public.notify_on_story()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.notifications (user_id, group_id, kind, story_id)
  select gm.user_id, new.group_id, 'new_story', new.id
  from public.group_members gm
  where gm.group_id = new.group_id
    and (new.created_by is null or gm.user_id <> new.created_by)
    and public.notifications_enabled(gm.user_id, new.group_id, 'new_stories');
  return new;
end $$;
