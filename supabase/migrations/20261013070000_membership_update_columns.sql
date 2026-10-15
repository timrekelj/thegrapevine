-- The mark-seen policy must not allow role escalation or moving into another paper.
revoke update on public.group_members from authenticated, anon;
grant update (last_seen_at) on public.group_members to authenticated;
