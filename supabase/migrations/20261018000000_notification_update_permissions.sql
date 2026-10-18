-- Members may mark their notifications read, but cannot rewrite delivery targets/content.
revoke update on public.notifications from authenticated, anon;
grant update (read) on public.notifications to authenticated;
