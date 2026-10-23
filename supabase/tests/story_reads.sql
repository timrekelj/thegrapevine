-- Run against the seeded local database; all writes are rolled back.
-- mark_story_read and story_reads isolation from 20261023100000_story_reads_and_realtime.sql.
begin;
-- Tim (…001) reads a story in The Grapevine
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000001","role":"authenticated"}';
do $$
declare s uuid; first timestamptz; second timestamptz; n int; denied boolean;
begin
  select id into s from public.stories limit 1;
  first := public.mark_story_read(s);
  if first is not null then raise exception 'first read should be null'; end if;
  second := public.mark_story_read(s);
  if second is null then raise exception 'second read should return previous'; end if;
  select count(*) into n from public.story_reads; if n <> 1 then raise exception 'expected 1 own row, got %', n; end if;
  begin insert into public.story_reads (story_id, user_id) values (s, auth.uid()); denied := false;
  exception when insufficient_privilege then denied := true; end;
  if not denied then raise exception 'direct insert allowed'; end if;
  begin perform public.mark_story_read('00000000-0000-0000-0000-0000000000ff'); denied := false;
  exception when others then denied := true; end;
  if not denied then raise exception 'unknown story accepted'; end if;
  raise notice 'story_reads ok';
end $$;
-- Luka cannot see Tim's read
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000002","role":"authenticated"}';
do $$ declare n int; begin select count(*) into n from public.story_reads; if n <> 0 then raise exception 'saw another member''s reads'; end if; raise notice 'isolation ok'; end $$;
rollback;
