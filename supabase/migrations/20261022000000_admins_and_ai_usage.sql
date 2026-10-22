-- Admins and AI usage accounting (22 October 2026).
-- Admin reads go only through the security-definer functions below. No table policy
-- mentions admins, so an admin's own paper in the apps behaves exactly like any member's.

-- ---------------------------------------------------------------- admins
-- Kept out of profiles, which groupmates can read. Promote an account by hand:
--   insert into public.admins (user_id) select id from auth.users where email = 'you@example.com';
create table public.admins (
  user_id uuid primary key references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table public.admins enable row level security; -- no policies: invisible through the API

create or replace function public.is_admin()
returns boolean language sql stable security definer
set search_path = ''
as $$ select exists (select 1 from public.admins a where a.user_id = auth.uid()) $$;

-- ---------------------------------------------------------------- AI usage
-- One row per model call, written by the edge functions with the service role.
-- Cost is priced at write time so later price changes never rewrite history.
create table public.ai_usage (
  id bigint generated always as identity primary key,
  created_at timestamptz not null default now(),
  request_id uuid not null,              -- one edge function invocation
  function_name text not null,           -- e.g. submit-source, report-story
  operation text not null,               -- e.g. chat/completions, embeddings, clustering
  provider text not null,
  model text not null,
  input_tokens integer not null default 0,
  output_tokens integer not null default 0,
  cost_usd numeric(14, 8) not null default 0,
  latency_ms integer,
  is_error boolean not null default false,
  user_id uuid references auth.users (id) on delete set null,
  group_id uuid references public.groups (id) on delete set null,
  story_id uuid -- no foreign key: submit-source deletes its placeholder story when a source joins another
);
create index ai_usage_created_idx on public.ai_usage (created_at desc);
create index ai_usage_group_idx on public.ai_usage (group_id, created_at desc);
create index ai_usage_request_idx on public.ai_usage (request_id);
alter table public.ai_usage enable row level security; -- no policies: service role writes, admins read via functions

-- ---------------------------------------------------------------- admin read functions
create or replace function public.admin_guard()
returns void language plpgsql stable security definer
set search_path = ''
as $$
begin
  if not public.is_admin() then
    raise exception 'Admin access required' using errcode = '42501';
  end if;
end $$;

create or replace function public.admin_overview()
returns jsonb language plpgsql stable security definer
set search_path = ''
as $$
begin
  perform public.admin_guard();
  return jsonb_build_object(
    'users', (select count(*) from public.profiles),
    'groups', (select count(*) from public.groups),
    'stories', (select count(*) from public.stories),
    'sources', (select count(*) from public.sources),
    'messages', (select count(*) from public.messages),
    'open_reports', (select count(*) from public.reports where status = 'open'),
    'cost_7d', (select coalesce(sum(cost_usd), 0) from public.ai_usage where created_at >= now() - interval '7 days'),
    'cost_30d', (select coalesce(sum(cost_usd), 0) from public.ai_usage where created_at >= now() - interval '30 days'),
    'cost_all', (select coalesce(sum(cost_usd), 0) from public.ai_usage),
    'tokens_30d', (select coalesce(sum(input_tokens + output_tokens), 0) from public.ai_usage where created_at >= now() - interval '30 days'),
    'requests_30d', (select count(distinct request_id) from public.ai_usage where created_at >= now() - interval '30 days')
  );
end $$;

create or replace function public.admin_users()
returns table (
  id uuid, display_name text, monogram text, email text, created_at timestamptz,
  last_sign_in_at timestamptz, is_admin boolean, group_count bigint, source_count bigint,
  message_count bigint, ai_cost numeric
)
language plpgsql stable security definer
set search_path = ''
as $$
begin
  perform public.admin_guard();
  return query
    select p.id, p.display_name, p.monogram, u.email::text, p.created_at, u.last_sign_in_at,
      exists (select 1 from public.admins a where a.user_id = p.id),
      (select count(*) from public.group_members gm where gm.user_id = p.id),
      (select count(*) from public.sources s where s.submitted_by = p.id),
      (select count(*) from public.messages m where m.author_id = p.id),
      (select coalesce(sum(au.cost_usd), 0) from public.ai_usage au where au.user_id = p.id)
    from public.profiles p
    left join auth.users u on u.id = p.id
    order by p.created_at desc;
end $$;

create or replace function public.admin_user(target uuid)
returns jsonb language plpgsql stable security definer
set search_path = ''
as $$
begin
  perform public.admin_guard();
  return (select jsonb_build_object(
    'id', p.id, 'display_name', p.display_name, 'monogram', p.monogram, 'email', u.email,
    'created_at', p.created_at, 'last_sign_in_at', u.last_sign_in_at,
    'is_admin', exists (select 1 from public.admins a where a.user_id = p.id),
    'ai_cost', (select coalesce(sum(au.cost_usd), 0) from public.ai_usage au where au.user_id = p.id),
    'groups', coalesce((select jsonb_agg(jsonb_build_object(
        'id', g.id, 'name', g.name, 'role', gm.role, 'joined_at', gm.joined_at, 'last_seen_at', gm.last_seen_at)
        order by gm.joined_at)
      from public.group_members gm join public.groups g on g.id = gm.group_id where gm.user_id = p.id), '[]'::jsonb),
    'sources', coalesce((select jsonb_agg(x order by x.created_at desc) from (
        select s.id, s.url, s.publisher, s.domain, s.source_title, s.story_id, st.title as story_title,
          g.name as group_name, s.created_at
        from public.sources s join public.stories st on st.id = s.story_id join public.groups g on g.id = s.group_id
        where s.submitted_by = p.id order by s.created_at desc limit 25) x), '[]'::jsonb),
    'messages', coalesce((select jsonb_agg(x order by x.created_at desc) from (
        select m.id, m.body, m.story_id, st.title as story_title, g.name as group_name, m.created_at
        from public.messages m join public.stories st on st.id = m.story_id join public.groups g on g.id = st.group_id
        where m.author_id = p.id order by m.created_at desc limit 25) x), '[]'::jsonb))
    from public.profiles p left join auth.users u on u.id = p.id
    where p.id = target);
end $$;

create or replace function public.admin_groups()
returns table (
  id uuid, name text, invite_code text, created_at timestamptz, created_by_name text,
  member_count bigint, story_count bigint, source_count bigint, message_count bigint,
  open_reports bigint, ai_tokens bigint, ai_cost numeric, last_activity_at timestamptz
)
language plpgsql stable security definer
set search_path = ''
as $$
begin
  perform public.admin_guard();
  return query
    select g.id, g.name, g.invite_code, g.created_at, p.display_name,
      (select count(*) from public.group_members gm where gm.group_id = g.id),
      (select count(*) from public.stories s where s.group_id = g.id),
      (select count(*) from public.sources s where s.group_id = g.id),
      (select count(*) from public.messages m join public.stories s on s.id = m.story_id where s.group_id = g.id),
      (select count(*) from public.reports r join public.stories s on s.id = r.story_id where s.group_id = g.id and r.status = 'open'),
      (select coalesce(sum(au.input_tokens + au.output_tokens), 0)::bigint from public.ai_usage au where au.group_id = g.id),
      (select coalesce(sum(au.cost_usd), 0) from public.ai_usage au where au.group_id = g.id),
      (select max(s.last_activity_at) from public.stories s where s.group_id = g.id)
    from public.groups g
    left join public.profiles p on p.id = g.created_by
    order by g.created_at desc;
end $$;

create or replace function public.admin_group(target uuid)
returns jsonb language plpgsql stable security definer
set search_path = ''
as $$
begin
  perform public.admin_guard();
  return (select jsonb_build_object(
    'id', g.id, 'name', g.name, 'invite_code', g.invite_code, 'created_at', g.created_at,
    'members', coalesce((select jsonb_agg(jsonb_build_object(
        'id', p.id, 'display_name', p.display_name, 'monogram', p.monogram, 'email', u.email,
        'role', gm.role, 'joined_at', gm.joined_at, 'last_seen_at', gm.last_seen_at,
        'source_count', (select count(*) from public.sources s where s.group_id = g.id and s.submitted_by = p.id),
        'message_count', (select count(*) from public.messages m join public.stories s on s.id = m.story_id
                          where s.group_id = g.id and m.author_id = p.id))
        order by gm.joined_at)
      from public.group_members gm join public.profiles p on p.id = gm.user_id left join auth.users u on u.id = p.id
      where gm.group_id = g.id), '[]'::jsonb),
    'stories', coalesce((select jsonb_agg(jsonb_build_object(
        'id', s.id, 'title', s.title, 'category', s.category, 'flagged', s.flagged,
        'created_at', s.created_at, 'last_activity_at', s.last_activity_at,
        'source_count', (select count(*) from public.sources x where x.story_id = s.id),
        'message_count', (select count(*) from public.messages x where x.story_id = s.id),
        'report_count', (select count(*) from public.reports x where x.story_id = s.id),
        'ai_cost', (select coalesce(sum(au.cost_usd), 0) from public.ai_usage au where au.story_id = s.id))
        order by s.last_activity_at desc)
      from public.stories s where s.group_id = g.id), '[]'::jsonb),
    'usage', jsonb_build_object(
      'cost', (select coalesce(sum(au.cost_usd), 0) from public.ai_usage au where au.group_id = g.id),
      'input_tokens', (select coalesce(sum(au.input_tokens), 0) from public.ai_usage au where au.group_id = g.id),
      'output_tokens', (select coalesce(sum(au.output_tokens), 0) from public.ai_usage au where au.group_id = g.id),
      'requests', (select count(distinct au.request_id) from public.ai_usage au where au.group_id = g.id)))
    from public.groups g where g.id = target);
end $$;

create or replace function public.admin_reports(only_status text default null)
returns table (
  id uuid, status text, target text, note text, created_at timestamptz, resolved_at timestamptz,
  story_id uuid, story_title text, story_flagged boolean, group_id uuid, group_name text,
  reporter_id uuid, reporter_name text
)
language plpgsql stable security definer
set search_path = ''
as $$
begin
  perform public.admin_guard();
  return query
    select r.id, r.status, r.target, r.note, r.created_at, r.resolved_at,
      s.id, s.title, s.flagged, g.id, g.name, p.id, p.display_name
    from public.reports r
    join public.stories s on s.id = r.story_id
    join public.groups g on g.id = s.group_id
    left join public.profiles p on p.id = r.reporter_id
    where only_status is null or r.status = only_status
    order by r.created_at desc
    limit 500;
end $$;

-- One story with every generated version and every report, for checking a correction.
create or replace function public.admin_story(target uuid)
returns jsonb language plpgsql stable security definer
set search_path = ''
as $$
begin
  perform public.admin_guard();
  return (select jsonb_build_object(
    'id', s.id, 'title', s.title, 'summary', s.summary, 'category', s.category, 'flagged', s.flagged,
    'created_at', s.created_at, 'last_activity_at', s.last_activity_at, 'group_id', g.id, 'group_name', g.name,
    'ai_cost', (select coalesce(sum(au.cost_usd), 0) from public.ai_usage au where au.story_id = s.id),
    'history', coalesce((select jsonb_agg(jsonb_build_object('id', h.id, 'title', h.title, 'summary', h.summary,
        'category', h.category, 'created_at', h.created_at) order by h.created_at desc)
      from public.story_history h where h.story_id = s.id), '[]'::jsonb),
    'reports', coalesce((select jsonb_agg(jsonb_build_object('id', r.id, 'status', r.status, 'target', r.target,
        'note', r.note, 'created_at', r.created_at, 'resolved_at', r.resolved_at,
        'reporter_id', r.reporter_id, 'reporter_name', p.display_name) order by r.created_at desc)
      from public.reports r left join public.profiles p on p.id = r.reporter_id where r.story_id = s.id), '[]'::jsonb),
    'sources', coalesce((select jsonb_agg(jsonb_build_object('id', x.id, 'url', x.url, 'publisher', x.publisher,
        'domain', x.domain, 'source_title', x.source_title, 'extraction_status', x.extraction_status,
        'submitted_by_name', p.display_name, 'created_at', x.created_at) order by x.created_at)
      from public.sources x left join public.profiles p on p.id = x.submitted_by where x.story_id = s.id), '[]'::jsonb))
    from public.stories s join public.groups g on g.id = s.group_id
    where s.id = target);
end $$;

-- Usage aggregates over a window, optionally for one group.
create or replace function public.admin_ai_summary(days integer default 30, only_group uuid default null)
returns jsonb language plpgsql stable security definer
set search_path = ''
as $$
declare
  since timestamptz := now() - make_interval(days => greatest(days, 1));
begin
  perform public.admin_guard();
  return jsonb_build_object(
    'totals', (select jsonb_build_object(
        'cost', coalesce(sum(cost_usd), 0), 'input_tokens', coalesce(sum(input_tokens), 0),
        'output_tokens', coalesce(sum(output_tokens), 0), 'calls', count(*),
        'requests', count(distinct request_id), 'errors', count(*) filter (where is_error))
      from public.ai_usage where created_at >= since and (only_group is null or group_id = only_group)),
    'by_day', coalesce((select jsonb_agg(x order by x.day) from (
        select (created_at at time zone 'UTC')::date as day, sum(cost_usd) as cost,
          sum(input_tokens + output_tokens) as tokens, count(distinct request_id) as requests
        from public.ai_usage where created_at >= since and (only_group is null or group_id = only_group)
        group by 1) x), '[]'::jsonb),
    'by_model', coalesce((select jsonb_agg(x order by x.cost desc) from (
        select provider, model, sum(cost_usd) as cost, sum(input_tokens) as input_tokens,
          sum(output_tokens) as output_tokens, count(*) as calls
        from public.ai_usage where created_at >= since and (only_group is null or group_id = only_group)
        group by provider, model) x), '[]'::jsonb),
    'by_function', coalesce((select jsonb_agg(x order by x.cost desc) from (
        select function_name, sum(cost_usd) as cost, count(distinct request_id) as requests,
          sum(cost_usd) / nullif(count(distinct request_id), 0) as cost_per_request
        from public.ai_usage where created_at >= since and (only_group is null or group_id = only_group)
        group by function_name) x), '[]'::jsonb),
    'by_group', coalesce((select jsonb_agg(x order by x.cost desc) from (
        select au.group_id, g.name as group_name, sum(au.cost_usd) as cost,
          sum(au.input_tokens + au.output_tokens) as tokens, count(distinct au.request_id) as requests,
          sum(au.cost_usd) / nullif(count(distinct au.request_id), 0) as cost_per_request
        from public.ai_usage au left join public.groups g on g.id = au.group_id
        where au.created_at >= since and (only_group is null or au.group_id = only_group)
        group by au.group_id, g.name) x), '[]'::jsonb)
  );
end $$;

-- Most recent requests, newest first, each with its individual model calls.
create or replace function public.admin_ai_requests(days integer default 30, only_group uuid default null, max_rows integer default 100)
returns jsonb language plpgsql stable security definer
set search_path = ''
as $$
declare
  since timestamptz := now() - make_interval(days => greatest(days, 1));
begin
  perform public.admin_guard();
  return coalesce((select jsonb_agg(r order by r.started_at desc) from (
    select au.request_id, min(au.created_at) as started_at, min(au.function_name) as function_name,
      (array_agg(au.group_id) filter (where au.group_id is not null))[1] as group_id,
      min(g.name) as group_name,
      (array_agg(au.user_id) filter (where au.user_id is not null))[1] as user_id,
      min(p.display_name) as user_name,
      (array_agg(au.story_id) filter (where au.story_id is not null))[1] as story_id,
      sum(au.input_tokens) as input_tokens, sum(au.output_tokens) as output_tokens,
      sum(au.cost_usd) as cost, sum(au.latency_ms) as latency_ms,
      count(*) filter (where au.is_error) as errors,
      jsonb_agg(jsonb_build_object('operation', au.operation, 'provider', au.provider, 'model', au.model,
        'input_tokens', au.input_tokens, 'output_tokens', au.output_tokens, 'cost', au.cost_usd,
        'latency_ms', au.latency_ms, 'is_error', au.is_error, 'created_at', au.created_at) order by au.id) as calls
    from public.ai_usage au
    left join public.groups g on g.id = au.group_id
    left join public.profiles p on p.id = au.user_id
    where au.created_at >= since and (only_group is null or au.group_id = only_group)
    group by au.request_id
    order by min(au.created_at) desc
    limit least(greatest(max_rows, 1), 500)) r), '[]'::jsonb);
end $$;

-- ---------------------------------------------------------------- grants
do $$
declare fn text;
begin
  foreach fn in array array[
    'public.is_admin()', 'public.admin_guard()', 'public.admin_overview()', 'public.admin_users()',
    'public.admin_user(uuid)', 'public.admin_groups()', 'public.admin_group(uuid)',
    'public.admin_reports(text)', 'public.admin_story(uuid)',
    'public.admin_ai_summary(integer, uuid)', 'public.admin_ai_requests(integer, uuid, integer)'
  ] loop
    execute format('revoke all on function %s from public, anon', fn);
    execute format('grant execute on function %s to authenticated', fn);
  end loop;
end $$;
