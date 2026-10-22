import { Avatar, Style } from '@dicebear/core'
import disco from '@dicebear/styles/disco.json'

const style = new Style(disco)

// Process inks for the dots. No red: on this paper red only ever means "new to you".
const inks = ['#E8A317', '#C43C7A', '#2F8F5B', '#13808F', '#2357C6', '#6A3FB5', '#7FA32B']

// Members' avatars: a DiceBear "Disco" disc, a colour halftone in the same printing language as
// the photographs. Seeded with the member's id, so everyone keeps the same disc across sessions
// and devices. Drawn here so the art library never ships to the browser; the result never
// changes for a seed, so it caches forever.
export async function GET(_request: Request, { params }: { params: Promise<{ seed: string }> }) {
  const { seed } = await params
  const svg = new Avatar(style, { seed: seed.slice(0, 100), backgroundColor: inks }).toString()
  return new Response(svg, {
    headers: { 'Content-Type': 'image/svg+xml', 'Cache-Control': 'public, max-age=31536000, immutable' },
  })
}
