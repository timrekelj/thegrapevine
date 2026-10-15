-- last_seen_at powers the "new to you" markers on the front page
alter table public.group_members add column if not exists last_seen_at timestamptz;

-- members can delete their own submission (the "Undo" in the Filed toast)
create policy "sources deletable by submitter" on public.sources for delete using (auth.uid() = submitted_by);
