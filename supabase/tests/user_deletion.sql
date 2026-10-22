-- Run against the seeded local database; all writes are rolled back.
-- Deletes from auth.users, as the dashboard and Auth admin API do.
begin;

do $$
declare
  g constant uuid := '00000000-0000-0000-0000-0000000000a1';
  owner_id constant uuid := '00000000-0000-0000-0000-000000000001';
  solo uuid := gen_random_uuid();
  heir uuid;
begin
  if not exists (select 1 from public.sources where submitted_by = owner_id) then
    raise exception 'Seed has no sources from the owner; test would prove nothing';
  end if;

  delete from auth.users where id = owner_id;

  if not exists (select 1 from public.groups where id = g and created_by is null) then
    raise exception 'Paper was not kept after its creator was deleted';
  end if;
  if exists (select 1 from public.sources where submitted_by = owner_id) then
    raise exception 'Sources still reference the deleted member';
  end if;
  if exists (select 1 from public.group_members where user_id = owner_id) then
    raise exception 'Deleted member is still in the paper';
  end if;
  select user_id into heir from public.group_members where group_id = g and role = 'owner';
  if heir is distinct from '00000000-0000-0000-0000-000000000002' then
    raise exception 'Ownership passed to % instead of the longest-standing member', heir;
  end if;

  -- a member alone in their paper takes the paper with them
  insert into public.groups (id, name, created_by) values (solo, 'Solo', heir);
  insert into public.group_members (group_id, user_id, role) values (solo, heir, 'owner');
  delete from auth.users where id = heir;
  if exists (select 1 from public.groups where id = solo) then
    raise exception 'Empty paper was not deleted';
  end if;
  if (select count(*) from public.group_members where group_id = g and role = 'owner') <> 1 then
    raise exception 'Shared paper did not keep exactly one owner';
  end if;
end $$;

rollback;
