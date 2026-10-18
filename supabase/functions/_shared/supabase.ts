import { createClient } from 'npm:@supabase/supabase-js@2';
import { HttpError } from './cors.ts';
import { telemetryUser } from './telemetry.ts';

export const supabase = createClient(
  Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
  { auth: { persistSession: false, autoRefreshToken: false } },
);

export async function caller(req: Request) {
  const authorization = req.headers.get('Authorization');
  if (!authorization?.match(/^Bearer \S+$/i)) throw new HttpError(401, 'Sign in first');
  const client = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_ANON_KEY')!, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data, error } = await client.auth.getUser();
  if (error || !data.user) throw new HttpError(401, 'Invalid session');
  telemetryUser(data.user.id);
  return data.user;
}

export async function requireMember(groupId: string, userId: string) {
  const { data, error } = await supabase.from('group_members').select('user_id')
    .eq('group_id', groupId).eq('user_id', userId).maybeSingle();
  if (error) throw error;
  if (!data) throw new HttpError(403, 'Group membership required');
}
