import { caller, supabase } from '../_shared/supabase.ts';
import { withTelemetry } from '../_shared/telemetry.ts';
import { handleDelete } from './delete.ts';

// The signed-in user, identified by their JWT. The body is ignored so a client
// cannot name a different account. Relational deletes are the foreign keys on
// auth.users / profiles; this function also removes the caller's media files.

async function mediaNames(userId: string, after: string) {
  const { data, error } = await supabase.rpc('media_object_names', { target: userId, after });
  if (error) throw error;
  return (data ?? []) as string[];
}

async function removeMedia(paths: string[]) {
  const { error } = await supabase.storage.from('media').remove(paths);
  if (error) throw error;
}

async function deleteUser(userId: string) {
  const { error } = await supabase.auth.admin.deleteUser(userId);
  if (error) throw error;
}

Deno.serve(withTelemetry('delete-account', req => handleDelete(req, {
  caller, mediaNames, removeMedia, deleteUser,
})));
