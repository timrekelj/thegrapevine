import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { ImageResponse } from 'next/og'

// Link-preview cards for Discord, iMessage, WhatsApp, Slack, X and friends.
export const ogSize = { width: 1200, height: 630 }

const paper = '#F4F1EA'
const ink = '#161513'
const ink2 = '#4A4740'

export async function renderOgCard({ label, headline, summary }: { label: string; headline: string; summary: string }) {
  const [nameplate, display, text, mono, icon] = await Promise.all([
    readFile(join(process.cwd(), 'assets/UnifrakturMaguntia-Regular.ttf')),
    readFile(join(process.cwd(), 'assets/Newsreader-Display-SemiBold.ttf')),
    readFile(join(process.cwd(), 'assets/Newsreader-Text-Regular.ttf')),
    readFile(join(process.cwd(), 'assets/IBMPlexMono-SemiBold.ttf')),
    readFile(join(process.cwd(), 'app/icon.svg')),
  ])
  const iconSrc = `data:image/svg+xml;base64,${icon.toString('base64')}`

  return new ImageResponse(
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', background: paper, color: ink, fontFamily: 'Newsreader', padding: '40px 72px 52px' }}>
      <div style={{ display: 'flex', justifyContent: 'center', fontFamily: 'UnifrakturMaguntia', fontSize: 84, lineHeight: 1 }}>The Grapevine</div>
      <div style={{ display: 'flex', marginTop: 16, borderTop: `4px solid ${ink}` }} />
      <div style={{ display: 'flex', marginTop: 3, borderTop: `1px solid ${ink}` }} />
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: `1px solid ${ink}`, fontFamily: 'IBM Plex Mono', fontWeight: 600, fontSize: 17, letterSpacing: '0.12em', textTransform: 'uppercase' }}>
        <span>{label}</span><span>A private newspaper</span><span>thegrapevine.si</span>
      </div>
      <div style={{ display: 'flex', flex: 1, alignItems: 'center', gap: 56 }}>
        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, gap: 22 }}>
          <div style={{ fontWeight: 600, fontSize: 78, lineHeight: 1, letterSpacing: '-0.025em' }}>{headline}</div>
          <div style={{ fontSize: 31, lineHeight: 1.4, color: ink2 }}>{summary}</div>
        </div>
        {/* eslint-disable-next-line @next/next/no-img-element -- rendered by Satori, not the browser */}
        <img src={iconSrc} width={300} height={300} alt="" />
      </div>
    </div>,
    {
      ...ogSize,
      fonts: [
        { name: 'UnifrakturMaguntia', data: nameplate, weight: 400, style: 'normal' },
        { name: 'Newsreader', data: text, weight: 400, style: 'normal' },
        { name: 'Newsreader', data: display, weight: 600, style: 'normal' },
        { name: 'IBM Plex Mono', data: mono, weight: 600, style: 'normal' },
      ],
    },
  )
}
