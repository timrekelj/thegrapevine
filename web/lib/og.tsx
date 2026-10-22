import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { ImageResponse } from 'next/og'

// Link-preview cards for Discord, iMessage, WhatsApp, Slack, X and friends.
export const ogSize = { width: 1200, height: 630 }

const paper = '#EFEEE9'
const ink = '#4A2160'
const ink2 = '#6E5280'

export async function renderOgCard({ label, headline, summary }: { label: string; headline: string; summary: string }) {
  const [gloock, interMedium, interSemiBold, icon] = await Promise.all([
    // Kerning stripped: Satori measures words without it but draws with it, leaving gaps.
    readFile(join(process.cwd(), 'assets/Gloock-Regular-NoKern.ttf')),
    readFile(join(process.cwd(), 'assets/Inter-Medium.ttf')),
    readFile(join(process.cwd(), 'assets/Inter-SemiBold.ttf')),
    readFile(join(process.cwd(), 'app/icon.svg')),
  ])
  const iconSrc = `data:image/svg+xml;base64,${icon.toString('base64')}`

  return new ImageResponse(
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', background: paper, color: ink, fontFamily: 'Inter', fontWeight: 500, padding: '56px 72px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: `2px solid ${ink}`, paddingBottom: 20 }}>
        <div style={{ fontFamily: 'Gloock', fontSize: 44 }}>The Grapevine</div>
        <div style={{ fontSize: 22, fontWeight: 600, letterSpacing: '0.08em', textTransform: 'uppercase', color: ink2 }}>{label}</div>
      </div>
      <div style={{ display: 'flex', flex: 1, alignItems: 'center', gap: 56 }}>
        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, gap: 28 }}>
          <div style={{ fontFamily: 'Gloock', fontSize: 84, lineHeight: 1.02 }}>{headline}</div>
          <div style={{ fontSize: 32, lineHeight: 1.4, letterSpacing: '-0.01em', color: ink2 }}>{summary}</div>
        </div>
        {/* eslint-disable-next-line @next/next/no-img-element -- rendered by Satori, not the browser */}
        <img src={iconSrc} width={340} height={340} alt="" />
      </div>
      <div style={{ display: 'flex', borderTop: `1px solid ${ink}`, opacity: 0.4 }} />
    </div>,
    {
      ...ogSize,
      fonts: [
        { name: 'Gloock', data: gloock, weight: 400, style: 'normal' },
        { name: 'Inter', data: interMedium, weight: 500, style: 'normal' },
        { name: 'Inter', data: interSemiBold, weight: 600, style: 'normal' },
      ],
    },
  )
}
