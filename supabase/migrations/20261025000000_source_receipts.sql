-- Background link filing (25 October 2026).
-- submit-source now answers as soon as a link is accepted and files it after responding. When
-- filing finishes, the submitter gets a receipt notification (and push): source_filed when the
-- link is on the paper, source_failed when it could not be organized. A receipt for a link whose
-- source was never saved has no story.
-- Idempotent: safe to re-run. Deploy before the submit-source and send-notification functions.
alter table public.notifications drop constraint if exists notifications_kind_check;
alter table public.notifications add constraint notifications_kind_check
  check (kind in ('new_story', 'direct_reply', 'mention', 'digest', 'source_filed', 'source_failed'));

-- The web share form, which has no push, listens for the receipt. Realtime applies the
-- "notifications are private" policy, so members only hear their own.
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') and not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'notifications'
  ) then
    alter publication supabase_realtime add table public.notifications;
  end if;
end;
$$;
