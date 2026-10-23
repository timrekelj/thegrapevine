import { Avatar, Style } from '@dicebear/core'
import disco from '@dicebear/styles/disco.json'
import { Resvg } from '@resvg/resvg-js'

const style = new Style(disco)

// Process inks for the dots. No red: on this paper red only ever means "new to you".
const inks = ['#E8A317', '#C43C7A', '#2F8F5B', '#13808F', '#2357C6', '#6A3FB5', '#7FA32B']

// Members' avatars: a DiceBear "Disco" disc, a colour halftone in the same printing language as
// the photographs. Seeded with the member's id, so everyone keeps the same disc across sessions
// and devices. Drawn here so the art library never ships to the browser; the result never
// changes for a seed, so it caches forever.
// `/avatar/<id>` is an SVG for the web; `/avatar/<id>.png` is a 192px PNG for the iOS and Android
// apps, whose image loaders don't draw SVG.
// Anyone may fetch these, and every new seed costs a render (about 60 ms of CPU for a PNG), so
// seeds are limited to the ids and sample names the app uses; anything else is a cheap 404.
export async function GET(_request: Request, { params }: RouteContext<'/avatar/[seed]'>) {
  const { seed: path } = await params
  const png = path.endsWith('.png')
  const seed = png ? path.slice(0, -4) : path
  if (!/^[\w-]{1,64}$/.test(seed)) return new Response('Not found', { status: 404 })
  const svg = new Avatar(style, { seed, backgroundColor: inks }).toString()
  const headers = { 'Cache-Control': 'public, max-age=31536000, immutable' }
  if (!png) return new Response(svg, { headers: { ...headers, 'Content-Type': 'image/svg+xml' } })
  const image = new Resvg(svg, { fitTo: { mode: 'width', value: 192 } }).render().asPng()
  return new Response(new Uint8Array(image), { headers: { ...headers, 'Content-Type': 'image/png' } })
}
