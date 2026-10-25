-- Run against the seeded local database (tim@ is the seeded admin); nothing is written.
begin;

do $$
declare
  fn text;
  denied boolean;
  n integer;
  calls text[] := array[
    'select public.admin_overview()', 'select * from public.admin_users()',
    'select public.admin_user(''00000000-0000-0000-0000-000000000001'')', 'select * from public.admin_groups()',
    'select public.admin_group(''00000000-0000-0000-0000-0000000000a1'')', 'select * from public.admin_reports(null)',
    'select public.admin_story(''00000000-0000-0000-0000-0000000000b1'')', 'select public.admin_ai_summary(30, null)',
    'select public.admin_ai_requests(30, null, 10)'];
begin
  -- a member who is not an admin: every admin function refuses, and the new tables are invisible
  execute 'set local role authenticated';
  perform set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
  if public.is_admin() then raise exception 'Non-admin reported as admin'; end if;
  foreach fn in array calls loop
    denied := false;
    begin execute fn; exception when insufficient_privilege then denied := true; end;
    if not denied then raise exception 'Non-admin was allowed to run: %', fn; end if;
  end loop;
  foreach fn in array array['select count(*) from public.ai_usage', 'select count(*) from public.admins'] loop
    denied := false;
    begin execute fn; exception when insufficient_privilege then denied := true; end;
    if not denied then raise exception 'Non-admin can read: %', fn; end if;
  end loop;

  -- the admin: every function answers
  perform set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
  if not public.is_admin() then raise exception 'Seeded admin not recognised'; end if;
  foreach fn in array calls loop execute fn; end loop;
  select count(*) into n from public.admin_users();
  if n < 5 then raise exception 'admin_users returned % rows', n; end if;

  -- admin status grants nothing through ordinary table access: the apps see the same paper as before
  select count(*) into n from public.groups g
    where not exists (select 1 from public.group_members m where m.group_id = g.id and m.user_id = auth.uid())
      and g.created_by <> auth.uid();
  if n <> 0 then raise exception 'Admin can read groups they are not in through table access'; end if;
  denied := false;
  begin perform count(*) from public.ai_usage; exception when insufficient_privilege then denied := true; end;
  if not denied then raise exception 'ai_usage is readable through table access'; end if;

  -- signed out: no access at all
  execute 'set local role anon';
  denied := false;
  begin perform public.admin_overview(); exception when insufficient_privilege then denied := true; end;
  if not denied then raise exception 'Anonymous caller reached admin_overview'; end if;
end $$;

rollback;
