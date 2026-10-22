import { Avatar, Style } from '@dicebear/core'
import marbles from '@dicebear/styles/marbles.json'

const style = new Style(marbles)

// Members' portraits: a DiceBear "Marbles" face, the one burst of colour in an ink-and-paper
// layout. Seeded with the member's id, so everyone keeps the same marble across sessions and
// devices. Drawn here so the art library never ships to the browser; the result never changes
// for a seed, so it caches forever.
export async function GET(_request: Request, { params }: { params: Promise<{ seed: string }> }) {
  const { seed } = await params
  const svg = new Avatar(style, { seed: seed.slice(0, 100), backgroundColor: '#00000000', scale: 1.12, translateY: -2 }).toString()
  return new Response(svg, {
    headers: { 'Content-Type': 'image/svg+xml', 'Cache-Control': 'public, max-age=31536000, immutable' },
  })
}
