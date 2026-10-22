import { ogSize, renderOgCard } from '@/lib/og'

export const alt = 'You’re invited to a paper on The Grapevine'
export const size = ogSize
export const contentType = 'image/png'

export default function Image() {
  return renderOgCard({
    label: 'Invitation',
    headline: 'You’re invited to join the paper.',
    summary: 'A private newspaper, shared and discussed by the people in it.',
  })
}
