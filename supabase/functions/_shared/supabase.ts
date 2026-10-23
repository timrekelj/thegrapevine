import { createClient } from 'npm:@supabase/supabase-js@2';
import { HttpError } from './cors.ts';
import { telemetryUser } from './telemetry.ts';

export const supabase = createClient(
  Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
  { auth: { persistSession: false, autoRefreshToken: false } },
);

/** The signed-in member making this request. Auth validates the token and that the user still exists. */
export async function caller(req: Request) {
  const token = req.headers.get('Authorization')?.match(/^Bearer (\S+)$/i)?.[1];
  if (!token) throw new HttpError(401, 'Sign in first');
  const { data, error } = await supabase.auth.getUser(token);
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

/**
 * Each submission and report spends AI credits, so a member's recent rows are capped.
 * Counts use the (column, created_at) indexes from the review-fixes migration.
 */
export async function rateLimit(table: 'sources' | 'reports', column: string, userId: string, max: number, minutes: number) {
  const since = new Date(Date.now() - minutes * 60_000).toISOString();
  const { count, error } = await supabase.from(table).select('id', { count: 'exact', head: true })
    .eq(column, userId).gte('created_at', since);
  if (error) throw error;
  if ((count ?? 0) >= max) throw new HttpError(429, 'Too many requests. Wait a few minutes and try again.');
}
