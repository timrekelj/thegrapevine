-- Reading position per story, and realtime for the story page.
-- Idempotent and data-preserving: safe to re-run and to apply to the hosted database.

-- ================================================================ story reads
-- When each member last read each story, so every client can mark "N new since …" in the
-- conversation, and reading on one device clears it on the others.
create table if not exists public.story_reads (
  story_id uuid not null references public.stories (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  last_read_at timestamptz not null default now(),
  primary key (story_id, user_id)
);
create index if not exists story_reads_user_idx on public.story_reads (user_id);

alter table public.story_reads enable row level security;
-- Rows are written only through mark_story_read, which uses the server clock.
revoke all on public.story_reads from anon, authenticated;
grant select on public.story_reads to authenticated;
drop policy if exists "own story reads readable" on public.story_reads;
create policy "own story reads readable" on public.story_reads for select to authenticated
  using (user_id = (select auth.uid()));

-- Records that the caller is reading a story now and returns when they last read it before
-- (null on the first visit). Clients call it when a story opens, keep the returned time for the
-- divider, and call it again while reading and on leaving.
create or replace function public.mark_story_read(story uuid)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
  previous timestamptz;
begin
  if uid is null or not exists (
    select 1 from public.stories s where s.id = story and public.is_group_member(s.group_id)
  ) then
    raise exception 'Story not found' using errcode = 'P0002';
  end if;
  select r.last_read_at into previous from public.story_reads r where r.story_id = story and r.user_id = uid;
  insert into public.story_reads (story_id, user_id, last_read_at) values (story, uid, now())
    on conflict (story_id, user_id) do update set last_read_at = excluded.last_read_at;
  return previous;
end;
$$;
revoke all on function public.mark_story_read(uuid) from public, anon;
grant execute on function public.mark_story_read(uuid) to authenticated;

-- ================================================================ realtime
-- The web story page listens for changes to these tables (filtered by story where the table has
-- a story_id). Realtime applies RLS to inserts and updates, so members only hear their own papers.
-- Already enabled on the hosted project from the dashboard; this keeps it in code.
do $$
declare
  t text;
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    foreach t in array array['messages', 'stories', 'sources', 'reactions'] loop
      if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
      ) then
        execute format('alter publication supabase_realtime add table public.%I', t);
      end if;
    end loop;
  end if;
end;
$$;
