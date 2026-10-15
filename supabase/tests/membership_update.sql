-- Run against the seeded local database; all writes are rolled back.
begin;
set local role authenticated;
set local request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';

do $$
declare changed integer;
begin
  update public.group_members set last_seen_at = last_seen_at
    where group_id = '00000000-0000-0000-0000-0000000000a1'
      and user_id = auth.uid();
  get diagnostics changed = row_count;
  if changed <> 1 then raise exception 'Own membership mark-seen update failed'; end if;

  begin
    update public.group_members set role = 'owner' where user_id = auth.uid();
    raise exception 'Role escalation was not denied';
  exception when insufficient_privilege then null;
  end;

  begin
    update public.group_members set group_id = group_id where user_id = auth.uid();
    raise exception 'Changing group_id was not denied';
  exception when insufficient_privilege then null;
  end;
end $$;

rollback;
