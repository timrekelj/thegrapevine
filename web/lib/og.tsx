import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { ImageResponse } from 'next/og'

export const ogSize = { width: 1200, height: 630 }

export async function renderOgCard({ label, headline, summary }: { label: string; headline: string; summary: string }) {
  const [display, regular, icon] = await Promise.all([
    readFile(join(process.cwd(), 'assets/BricolageGrotesque-ExtraBold.ttf')),
    readFile(join(process.cwd(), 'assets/BricolageGrotesque-Regular.ttf')),
    readFile(join(process.cwd(), 'app/icon.svg')),
  ])
  return new ImageResponse(
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', background: '#F0F4EB', color: '#15221B', fontFamily: 'Vine', padding: '48px 64px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 30, fontWeight: 800 }}><span>The Grapevine</span><span style={{ color: '#5E6B63', fontSize: 22 }}>{label}</span></div>
      <div style={{ display: 'flex', flex: 1, alignItems: 'center', gap: 48 }}>
        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, gap: 24 }}>
          <div style={{ fontWeight: 800, fontSize: 68, lineHeight: 1.04, letterSpacing: '-0.035em' }}>{headline}</div>
          <div style={{ fontSize: 26, lineHeight: 1.4, color: '#3F4C45' }}>{summary}</div>
        </div>
        {/* eslint-disable-next-line @next/next/no-img-element -- Satori renders this server-side. */}
        <img src={`data:image/svg+xml;base64,${icon.toString('base64')}`} width={270} height={270} alt="" />
      </div>
      <div style={{ display: 'flex', fontSize: 20, color: '#5E6B63' }}>Links from friends, grown into stories. · thegrapevine.si</div>
    </div>,
    { ...ogSize, fonts: [{ name: 'Vine', data: display, weight: 800, style: 'normal' }, { name: 'Vine', data: regular, weight: 400, style: 'normal' }] },
  )
}
