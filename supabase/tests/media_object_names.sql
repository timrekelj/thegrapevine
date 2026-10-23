-- Run against the migrated local database; all writes are rolled back.
-- media_object_names is how delete-account finds a member's files. It must
-- return only that uploader's objects, and only the service role may call it.
begin;

do $$
declare
  owner_id constant uuid := '00000000-0000-0000-0000-000000000001';
  other_id constant uuid := '00000000-0000-0000-0000-000000000002';
  group_id constant text := '00000000-0000-0000-0000-0000000000a1';
  n integer;
  denied boolean;
begin
  insert into storage.buckets (id, name, public) values ('media', 'media', false) on conflict do nothing;
  insert into storage.objects (bucket_id, name) values
    ('media', group_id || '/' || owner_id::text || '/one.jpg'),
    ('media', group_id || '/' || owner_id::text || '/two.jpg'),
    ('media', group_id || '/' || other_id::text || '/theirs.jpg'),
    ('media', group_id || '/' || other_id::text || '/' || owner_id::text || '.jpg');

  select count(*) into n from public.media_object_names(owner_id, '');
  if n <> 2 then raise exception 'Expected 2 objects for the owner, got %', n; end if;

  select count(*) into n from public.media_object_names(owner_id, group_id || '/' || owner_id::text || '/one.jpg');
  if n <> 1 then raise exception 'Cursor should skip names at or before the boundary, got %', n; end if;

  select count(*) into n from public.media_object_names(other_id, '')
    where media_object_names like '%/' || owner_id::text || '/%';
  if n <> 0 then raise exception 'Another member''s listing included the owner''s folder'; end if;

  execute 'set local role authenticated';
  perform set_config('request.jwt.claim.sub', owner_id::text, true);
  denied := false;
  begin
    perform public.media_object_names(owner_id, '');
  exception when insufficient_privilege then
    denied := true;
  end;
  if not denied then raise exception 'A signed-in member could list storage paths'; end if;

  execute 'set local role anon';
  denied := false;
  begin
    perform public.media_object_names(owner_id, '');
  exception when insufficient_privilege then
    denied := true;
  end;
  if not denied then raise exception 'An anonymous caller could list storage paths'; end if;

  execute 'set local role service_role';
  select count(*) into n from public.media_object_names(owner_id, '');
  if n <> 2 then raise exception 'Service role could not list the owner''s media, got %', n; end if;
end $$;

rollback;
