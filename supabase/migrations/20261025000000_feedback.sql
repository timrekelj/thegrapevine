-- In-app reports (issue #35): bugs, enhancements and feature requests become GitHub issues.
-- Idempotent and data-preserving: safe to re-run and to apply to the hosted database.
-- Deploy before the submit-feedback edge function.

-- ================================================================ screenshots
-- The repository is public and GitHub renders an issue's images from their URLs, so report
-- screenshots are public objects under unguessable names. Only formats GitHub renders.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('feedback', 'feedback', true, 10485760, array['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
on conflict (id) do update set public = excluded.public, file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- A member uploads only to their own folder, one file per random name. A public bucket serves
-- objects without a select policy, and no policy allows update or delete.
drop policy if exists "feedback uploadable by author" on storage.objects;
create policy "feedback uploadable by author" on storage.objects for insert to authenticated with check (
  bucket_id = 'feedback'
  and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|gif|webp)$'
  and substring(name from 1 for 36) = (select auth.uid())::text);

-- ================================================================ reports
-- Every report is kept here before it is filed, so one survives a GitHub outage. Only the
-- submit-feedback function (service role) reads or writes it; the public issue never names
-- the reporter. Screenshots stay with the public issue when the reporter's account is deleted.
create table if not exists public.feedback (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users (id) on delete set null,
  kind text not null check (kind in ('bug', 'enhancement', 'feature')),
  description text not null check (char_length(description) between 1 and 4000),
  title text,
  body text,
  images text[] not null default '{}',
  context jsonb not null default '{}',
  issue_number integer,
  issue_url text,
  created_at timestamptz not null default now()
);
alter table public.feedback enable row level security;
revoke all on public.feedback from anon, authenticated;
create index if not exists feedback_user_idx on public.feedback (user_id);
