-- members can update their own membership row (the front page records last_seen_at there)
-- note: this also permits changing role/joined_at on your own row; acceptable for the pilot
create policy "members can update own membership row" on public.group_members for update
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
