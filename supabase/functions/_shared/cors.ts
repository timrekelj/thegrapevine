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
  const text = await req.text();
  if (text.length > 12000) throw new HttpError(413, 'Request too large');
  try {
    const result = JSON.parse(text);
    if (!result || typeof result !== 'object' || Array.isArray(result)) throw new Error();
    return result;
  } catch { throw new HttpError(400, 'Expected a JSON object'); }
}

export function optionalText(value: unknown) {
  if (value !== undefined && (typeof value !== 'string' || value.length > 4000)) {
    throw new HttpError(400, 'Comment/note must be text under 4000 characters');
  }
  return value as string | undefined;
}
