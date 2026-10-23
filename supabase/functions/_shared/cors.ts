export const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

export function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
  });
}

export class HttpError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

export function errorResponse(error: unknown) {
  if (error instanceof HttpError) return json({ error: error.message }, error.status);
  console.error('Pipeline operation failed', error instanceof Error ? error.message : 'Database error');
  return json({ error: 'Operation failed; your saved source or report has been preserved.' }, 500);
}

export function uuid(value: unknown): value is string {
  return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

export async function body(req: Request) {
  // Refuse a declared oversize body before buffering it.
  if (Number(req.headers.get('content-length')) > 12000) throw new HttpError(413, 'Request too large');
  const text = await req.text();
  if (text.length > 12000) throw new HttpError(413, 'Request too large');
  try {
    const result = JSON.parse(text);
    if (!result || typeof result !== 'object' || Array.isArray(result)) throw new Error();
    return result;
  } catch { throw new HttpError(400, 'Expected a JSON object'); }
}

export function optionalText(value: unknown) {
  if (value === undefined || value === null) return undefined;
  if (typeof value !== 'string' || value.length > 4000) {
    throw new HttpError(400, 'Comment/note must be text under 4000 characters');
  }
  return value;
}

/** Constant-time string comparison for shared secrets. */
export async function sameSecret(a: string, b: string) {
  const digest = async (value: string) => new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value)));
  const [x, y] = await Promise.all([digest(a), digest(b)]);
  let difference = 0;
  for (let i = 0; i < x.length; i++) difference |= x[i] ^ y[i];
  return difference === 0;
}
