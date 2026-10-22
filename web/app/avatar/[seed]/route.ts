import { createAvatar } from '@dicebear/core'
import * as notionists from '@dicebear/notionists'

// Members' portraits: an ink line drawing generated from a stable seed (the member's id), so
// everyone keeps the same face across sessions and devices. Drawn here so the art library never
// ships to the browser; the result never changes for a seed, so it caches forever.
export async function GET(_request: Request, { params }: { params: Promise<{ seed: string }> }) {
  const { seed } = await params
  const svg = createAvatar(notionists, { seed: seed.slice(0, 100), scale: 135, translateY: 8 }).toString()
  return new Response(svg, {
    headers: { 'Content-Type': 'image/svg+xml', 'Cache-Control': 'public, max-age=31536000, immutable' },
  })
}
