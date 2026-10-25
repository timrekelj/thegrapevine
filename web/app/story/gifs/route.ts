import { createClient } from '@/lib/supabase/server'
import { currentUserId } from '@/lib/auth'

export async function GET(request: Request) {
  if (!await currentUserId(await createClient())) return Response.json({ error: 'Sign in first.' }, { status: 401 })
  const key = process.env.GIPHY_API_KEY?.trim()
  if (!key) return Response.json({ error: 'GIF search is unavailable.' }, { status: 503 })
  const query = new URL(request.url).searchParams.get('q')?.trim().slice(0, 120)
  if (!query) return Response.json({ data: [] })
  const url = new URL('https://api.giphy.com/v1/gifs/search')
  url.search = new URLSearchParams({ api_key: key, q: query, limit: '12', rating: 'pg-13' }).toString()
  try {
    const result = await fetch(url, { cache: 'no-store', signal: AbortSignal.timeout(10000) })
    if (!result.ok) throw new Error('Search failed')
    const json = await result.json()
    return Response.json({ data: json.data.map((gif: { id: string; title: string; images: { fixed_height: { url: string }; fixed_height_still: { url: string } } }) => ({ id: gif.id, title: gif.title || 'Animated reaction', url: gif.images.fixed_height.url, still: gif.images.fixed_height_still.url })) })
  } catch {
    return Response.json({ error: 'Could not search GIFs. Try again.' }, { status: 502 })
  }
}
