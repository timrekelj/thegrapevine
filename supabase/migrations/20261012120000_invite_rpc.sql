-- Invite codes reveal only the paper name; all content still requires membership.
create or replace function public.join_group_with_invite(code text)
returns uuid
language plpgsql security definer
set search_path = ''
as $$
declare
  target_group uuid;
begin
  if auth.uid() is null then
    raise exception 'Sign in before joining a paper.';
  end if;
  select g.id into target_group from public.groups g
    where lower(g.invite_code) = lower(trim(code));
  if target_group is null then
    raise exception 'This invite code is not valid.';
  end if;
  insert into public.group_members (group_id, user_id, role)
    values (target_group, auth.uid(), 'member')
    on conflict (group_id, user_id) do nothing;
  return target_group;
end;
$$;

create or replace function public.group_preview_for_invite(code text)
returns table (id uuid, name text)
language plpgsql security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Sign in before viewing an invitation.';
  end if;
  return query select g.id, g.name from public.groups g
    where lower(g.invite_code) = lower(trim(code));
end;
$$;

revoke all on function public.join_group_with_invite(text) from public, anon;
revoke all on function public.group_preview_for_invite(text) from public, anon;
grant execute on function public.join_group_with_invite(text) to authenticated;
grant execute on function public.group_preview_for_invite(text) to authenticated;

-- Owner insertion checks groups through RLS before the creator is a member.
create policy "groups readable by creator" on public.groups for select
  to authenticated using (created_by = auth.uid());
