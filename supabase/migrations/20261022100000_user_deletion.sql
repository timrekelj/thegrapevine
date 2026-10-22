-- Deleting a user (dashboard or Auth admin API) failed: groups, stories, and sources
-- referenced profiles with no delete action. A departing member's links stay in the
-- paper unattributed, like their placeholder stories; their own data still cascades.
alter table public.groups alter column created_by drop not null;
alter table public.groups drop constraint groups_created_by_fkey,
  add constraint groups_created_by_fkey foreign key (created_by)
    references public.profiles (id) on delete set null;

alter table public.stories drop constraint stories_created_by_fkey,
  add constraint stories_created_by_fkey foreign key (created_by)
    references public.profiles (id) on delete set null;

alter table public.sources alter column submitted_by drop not null;
alter table public.sources drop constraint sources_submitted_by_fkey,
  add constraint sources_submitted_by_fkey foreign key (submitted_by)
    references public.profiles (id) on delete set null;

-- Before the membership cascade: papers left empty are deleted, and an owner's papers
-- pass to the longest-standing remaining member so every paper keeps an owner.
-- Security definer because Auth deletes users as supabase_auth_admin.
create or replace function public.handle_profile_deleted()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  delete from public.groups g
  where exists (select 1 from public.group_members m where m.group_id = g.id and m.user_id = old.id)
    and not exists (select 1 from public.group_members m where m.group_id = g.id and m.user_id <> old.id);

  update public.group_members m set role = 'owner'
  from (
    select distinct on (o.group_id) o.group_id, o.user_id
    from public.group_members leaving
    join public.group_members o on o.group_id = leaving.group_id and o.user_id <> old.id
    where leaving.user_id = old.id and leaving.role = 'owner'
      and not exists (select 1 from public.group_members x
                      where x.group_id = leaving.group_id and x.role = 'owner' and x.user_id <> old.id)
    order by o.group_id, o.joined_at, o.user_id
  ) heir
  where m.group_id = heir.group_id and m.user_id = heir.user_id;

  return old;
end $$;

create trigger on_profile_deleted before delete on public.profiles
  for each row execute function public.handle_profile_deleted();
