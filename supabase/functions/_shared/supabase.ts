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

/** The data of a successful query; a query error is thrown. */
export async function check<T>(query: PromiseLike<{ data: T; error: unknown }>) {
  const { data, error } = await query;
  if (error) throw error;
  return data;
}

export async function requireMember(groupId: string, userId: string) {
  const member = await check(supabase.from('group_members').select('user_id')
    .eq('group_id', groupId).eq('user_id', userId).maybeSingle());
  if (!member) throw new HttpError(403, 'Group membership required');
}

/**
 * Each submission and report spends AI credits, so a member's requests are capped per window
 * and per day. The database records each request under a per-member lock, so deleting a source
 * or sending requests concurrently cannot free a slot.
 */
export async function rateLimit(action: string, userId: string, perWindow: number, windowMinutes: number, perDay: number) {
  const allowed = await check(supabase.rpc('consume_rate_limit', {
    target: userId, action, per_window: perWindow, window_minutes: windowMinutes, per_day: perDay,
  }));
  if (!allowed) throw new HttpError(429, 'Too many requests. Wait a few minutes and try again.');
}
