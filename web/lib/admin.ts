import type { SupabaseClient } from '@supabase/supabase-js';

// Shapes returned by the admin_* database functions (supabase/migrations/20261022000000_admins_and_ai_usage.sql).
// Postgres numerics arrive as numbers or numeric strings, so every money/token value goes through num().

export type Overview = {
  users: number; groups: number; stories: number; sources: number; messages: number; open_reports: number;
  cost_7d: number; cost_30d: number; cost_all: number; tokens_30d: number; requests_30d: number;
};
export type AdminUserRow = {
  id: string; display_name: string; monogram: string; email: string | null; created_at: string;
  last_sign_in_at: string | null; is_admin: boolean; group_count: number; source_count: number;
  message_count: number; ai_cost: number;
};
export type AdminUser = Omit<AdminUserRow, 'group_count' | 'source_count' | 'message_count'> & {
  groups: { id: string; name: string; role: string; joined_at: string; last_seen_at: string | null }[];
  sources: { id: string; url: string; publisher: string; domain: string; source_title: string; story_id: string; story_title: string; group_name: string; created_at: string }[];
  messages: { id: string; body: string | null; story_id: string; story_title: string; group_name: string; created_at: string }[];
};
export type AdminGroupRow = {
  id: string; name: string; invite_code: string; created_at: string; created_by_name: string | null;
  member_count: number; story_count: number; source_count: number; message_count: number;
  open_reports: number; ai_tokens: number; ai_cost: number; last_activity_at: string | null;
};
export type AdminGroup = {
  id: string; name: string; invite_code: string; created_at: string;
  members: { id: string; display_name: string; monogram: string; email: string | null; role: string; joined_at: string; last_seen_at: string | null; source_count: number; message_count: number }[];
  stories: { id: string; title: string; category: string | null; flagged: boolean; created_at: string; last_activity_at: string; source_count: number; message_count: number; report_count: number; ai_cost: number }[];
  usage: { cost: number; input_tokens: number; output_tokens: number; requests: number };
};
export type AdminReport = {
  id: string; status: 'open' | 'resolved'; target: string; note: string | null; created_at: string; resolved_at: string | null;
  story_id: string; story_title: string; story_flagged: boolean; group_id: string; group_name: string;
  reporter_id: string | null; reporter_name: string | null;
};
export type DaySpend = { day: string; cost: number; tokens: number; requests: number };
export type AISummary = {
  totals: { cost: number; input_tokens: number; output_tokens: number; calls: number; requests: number; errors: number };
  by_day: DaySpend[];
  by_model: { provider: string; model: string; cost: number; input_tokens: number; output_tokens: number; calls: number }[];
  by_function: { function_name: string; cost: number; requests: number; cost_per_request: number | null }[];
  by_group: { group_id: string | null; group_name: string | null; cost: number; tokens: number; requests: number; cost_per_request: number | null }[];
};
type AICall = { operation: string; provider: string; model: string; input_tokens: number; output_tokens: number; cost: number; latency_ms: number | null; is_error: boolean; created_at: string };
export type AIRequest = {
  request_id: string; started_at: string; function_name: string; group_id: string | null; group_name: string | null;
  user_id: string | null; user_name: string | null; story_id: string | null;
  input_tokens: number; output_tokens: number; cost: number; latency_ms: number | null; errors: number; calls: AICall[];
};

export const num = (value: unknown) => {
  const n = typeof value === 'number' ? value : Number(value ?? 0);
  return Number.isFinite(n) ? n : 0;
};

export function formatUsd(value: unknown) {
  const n = num(value);
  if (n === 0) return '$0.00';
  // Single model calls cost fractions of a cent; keep enough digits to compare them.
  if (Math.abs(n) < 0.01) return `$${n.toFixed(n < 0.0001 ? 6 : 4)}`;
  return `$${n.toFixed(2)}`;
}

export function formatTokens(value: unknown) {
  const n = num(value);
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1)}M`;
  if (n >= 10_000) return `${Math.round(n / 1000)}k`;
  return n.toLocaleString('en-GB');
}

export function formatDate(value: string | null | undefined, withTime = false) {
  if (!value) return '—';
  return new Date(value).toLocaleString('en-GB', withTime
    ? { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'UTC' }
    : { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
}

// One entry per UTC day in the window, oldest first, zero-filled where nothing ran.
export function fillDays(byDay: DaySpend[], days: number, now = new Date()): DaySpend[] {
  const known = new Map(byDay.map(d => [d.day.slice(0, 10), d]));
  const today = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  return Array.from({ length: days }, (_, i) => {
    const day = new Date(today - (days - 1 - i) * 86_400_000).toISOString().slice(0, 10);
    const found = known.get(day);
    return { day, cost: num(found?.cost), tokens: num(found?.tokens), requests: num(found?.requests) };
  });
}

export const WINDOWS = [7, 30, 90] as const;
export const parseWindow = (value: unknown) => WINDOWS.find(w => String(w) === value) ?? 30;

export async function adminRpc<T>(client: SupabaseClient, name: string, args?: Record<string, unknown>): Promise<T> {
  const { data, error } = await client.rpc(name, args);
  if (error) throw new Error(`Could not load admin data (${name}).`);
  return data as T;
}
export type AdminStory = {
  id: string; title: string; summary: string; category: string | null; flagged: boolean;
  created_at: string; last_activity_at: string; group_id: string; group_name: string; ai_cost: number;
  history: { id: string; title: string; summary: string; category: string | null; created_at: string }[];
  reports: { id: string; status: 'open' | 'resolved'; target: string; note: string | null; created_at: string; resolved_at: string | null; reporter_id: string | null; reporter_name: string | null }[];
  sources: { id: string; url: string; publisher: string; domain: string; source_title: string; extraction_status: string; submitted_by_name: string | null; created_at: string }[];
};
