-- Run against the seeded local database; all writes are rolled back.
-- Privileges, media and rate limits from 20261024000000_hardening.sql.
begin;

do $$
declare
  tim constant uuid := '00000000-0000-0000-0000-000000000001';
  paper constant text := '00000000-0000-0000-0000-0000000000a1';
  message constant uuid := '00000000-0000-0000-0000-0000000000df';
  n integer;
  denied boolean;
  stmt text;
begin
  insert into public.messages (id, story_id, author_id, body)
    values (message, '00000000-0000-0000-0000-0000000000b1', tim, 'media fixture');

  -- ------------------------------------------------ anon reaches no table or membership oracle
  execute 'set local role anon';
  foreach stmt in array array[
    'select count(*) from public.profiles',
    'select count(*) from public.groups',
    'select count(*) from public.stories',
    'select public.is_group_member(''00000000-0000-0000-0000-0000000000a1'')'
  ] loop
    denied := false;
    begin execute stmt; exception when insufficient_privilege then denied := true; end;
    if not denied then raise exception 'anon was allowed: %', stmt; end if;
  end loop;

  -- ------------------------------------------------ members: no admin tables, no TRUNCATE, no rate-limit writes
  execute 'set local role authenticated';
  perform set_config('request.jwt.claim.sub', tim::text, true);
  foreach stmt in array array[
    'select count(*) from public.admins',
    'select count(*) from public.ai_usage',
    'select count(*) from public.rate_limit_events',
    'truncate public.messages',
    'select public.consume_rate_limit(auth.uid(), ''submit-source'', 100, 10, 100)',
    -- external media other than an https GIPHY GIF
    format('insert into public.message_media (message_id, kind, url) values (%L, ''gif'', ''https://tracker.example/p.gif'')', message),
    format('insert into public.message_media (message_id, kind, url) values (%L, ''gif'', ''http://media.giphy.com/a.gif'')', message),
    format('insert into public.message_media (message_id, kind, url) values (%L, ''image'', ''https://media.giphy.com/a.gif'')', message),
    -- an upload path must be a file directly under {group}/{own uid}/
    format('insert into public.message_media (message_id, kind, url) values (%L, ''image'', %L)', message, paper || '/' || tim || '/a/b.jpg')
  ] loop
    denied := false;
    begin execute stmt; exception when insufficient_privilege or check_violation then denied := true; end;
    if not denied then raise exception 'Member was allowed: %', stmt; end if;
  end loop;

  insert into public.message_media (message_id, kind, url, position) values
    (message, 'gif', 'https://media2.giphy.com/media/abc/200.gif?cid=1', 0),
    (message, 'image', paper || '/' || tim || '/photo-0.jpg', 1);
  execute 'reset role';

  if exists (select 1 from storage.buckets where id = 'media' and 'image/svg+xml' like any (
      select replace(t, '*', '%') from unnest(allowed_mime_types) t)) then
    raise exception 'SVG uploads are allowed';
  end if;

  -- ------------------------------------------------ rate limits: per window and per day, per action
  execute 'set local role service_role';
  for i in 1..3 loop
    if not public.consume_rate_limit(tim, 'test', 3, 10, 5) then raise exception 'Under the cap was refused'; end if;
  end loop;
  if public.consume_rate_limit(tim, 'test', 3, 10, 5) then raise exception 'Window cap not enforced'; end if;
  if not public.consume_rate_limit(tim, 'other', 3, 10, 5) then raise exception 'Actions share a cap'; end if;
  execute 'reset role';
  update public.rate_limit_events set created_at = now() - interval '1 hour' where action = 'test';
  if not public.consume_rate_limit(tim, 'test', 3, 10, 5) then raise exception 'Window did not slide'; end if;
  if not public.consume_rate_limit(tim, 'test', 3, 10, 5) then raise exception 'Window did not slide'; end if;
  if public.consume_rate_limit(tim, 'test', 3, 10, 5) then raise exception 'Daily cap not enforced'; end if;
  update public.rate_limit_events set created_at = now() - interval '2 days' where action = 'test';
  if not public.consume_rate_limit(tim, 'test', 3, 10, 5) then raise exception 'Old events still counted'; end if;
  select count(*) into n from public.rate_limit_events where action = 'test';
  if n <> 1 then raise exception 'Expired events were not pruned (% rows)', n; end if;
end $$;

rollback;
