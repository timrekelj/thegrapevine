-- Hardening review (24 October 2026).
-- Idempotent and data-preserving: safe to re-run and to apply to the hosted database.
-- Deploy before the edge functions from the same change: they call consume_rate_limit.

-- ================================================================ table privileges
-- admins and ai_usage relied on RLS alone (no policies) while anon and authenticated still held
-- every table privilege on them. Only the service role and the admin_* functions touch them.
revoke all on public.admins, public.ai_usage from anon, authenticated;
revoke all on sequence public.ai_usage_id_seq from anon, authenticated;

-- Every policy is scoped to authenticated, so anon never needed table access at all. TRUNCATE,
-- REFERENCES and TRIGGER are not subject to RLS and no client uses them.
revoke all on all tables in schema public from anon;
revoke truncate, references, trigger on all tables in schema public from authenticated;

-- is_group_member is SECURITY DEFINER and still executable by anon through PUBLIC. Policies
-- (all scoped to authenticated) are its only callers.
revoke all on function public.is_group_member(uuid) from public, anon;
grant execute on function public.is_group_member(uuid) to authenticated, service_role;

-- ================================================================ media
-- image/* admitted SVG, which can carry script and is served from the project's API origin.
update storage.buckets
  set allowed_mime_types = array[
    'image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/heic', 'image/heif', 'image/avif',
    'video/mp4', 'video/quicktime', 'video/webm', 'video/3gpp', 'video/x-m4v']
  where id = 'media';

-- Any http(s) URL could be attached, so a member could make every reader's app load a tracking
-- pixel from their own server. External media is now a GIPHY GIF (the only external source the
-- apps offer) over https; uploads are a file directly under {group_id}/{own uid}/.
drop policy if exists "media attachable by author" on public.message_media;
create policy "media attachable by author" on public.message_media for insert to authenticated with check (
  exists (select 1 from public.messages m
          join public.stories s on s.id = m.story_id
          where m.id = message_id and m.author_id = (select auth.uid()) and public.is_group_member(s.group_id)
            and ((kind = 'gif' and url ~ '^https://([a-z0-9-]+\.)*giphy\.com/')
                 or (kind in ('image', 'video') and url ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}/[^/]+$'
                     and split_part(url, '/', 1) = s.group_id::text
                     and split_part(url, '/', 2) = (select auth.uid())::text))));

-- ================================================================ rate limits
-- The edge functions capped AI spend by counting a member's recent sources/reports. Deleting a
-- source (the Undo toast) freed its slot, and concurrent requests all read the same count, so
-- the cap could be bypassed. Each costly request now records an event under a per-member lock.
create table if not exists public.rate_limit_events (
  user_id uuid not null references auth.users (id) on delete cascade,
  action text not null,
  created_at timestamptz not null default now()
);
create index if not exists rate_limit_events_idx on public.rate_limit_events (user_id, action, created_at);
alter table public.rate_limit_events enable row level security; -- no policies: service role only
revoke all on public.rate_limit_events from anon, authenticated;

-- True (and recorded) when the member is under both caps; false when either is reached.
create or replace function public.consume_rate_limit(
  target uuid, action text, per_window integer, window_minutes integer, per_day integer)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  recent integer;
  today integer;
begin
  perform pg_advisory_xact_lock(hashtextextended(target::text || '/' || action, 0));
  delete from public.rate_limit_events e
    where e.user_id = target and e.action = consume_rate_limit.action and e.created_at < now() - interval '1 day';
  select count(*) filter (where e.created_at >= now() - make_interval(mins => window_minutes)), count(*)
    into recent, today
    from public.rate_limit_events e where e.user_id = target and e.action = consume_rate_limit.action;
  if recent >= per_window or today >= per_day then
    return false;
  end if;
  insert into public.rate_limit_events (user_id, action) values (target, action);
  return true;
end;
$$;
revoke all on function public.consume_rate_limit(uuid, text, integer, integer, integer) from public, anon, authenticated;
grant execute on function public.consume_rate_limit(uuid, text, integer, integer, integer) to service_role;

-- ================================================================ indexes
-- The AI usage pages group by request within a time window (ai_usage_created_idx); nothing
-- looks a request up by id, so this index only cost a write per model call.
drop index if exists public.ai_usage_request_idx;
