-- Account deletion removes auth.users, and existing foreign keys cascade or
-- anonymize relational data. Storage objects are not in that graph: media lives
-- at {group_id}/{uploader_id}/{filename}. The delete-account edge function lists
-- a caller's objects through this function (service role only) and removes them
-- with the Storage API, which also deletes the underlying files.

create or replace function public.media_object_names(target uuid, after text default '')
returns setof text
language sql
stable
security definer
set search_path = ''
as $$
  select o.name
  from storage.objects o
  where o.bucket_id = 'media'
    and pg_catalog.split_part(o.name, '/', 2) = target::text
    and o.name > after
  order by o.name
  limit 1000
$$;

revoke all on function public.media_object_names(uuid, text) from public, anon, authenticated;
grant execute on function public.media_object_names(uuid, text) to service_role;
