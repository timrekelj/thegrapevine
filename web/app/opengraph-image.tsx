import { ogSize, renderOgCard } from '@/lib/og'

export const alt = 'The Grapevine: a private newspaper curated by friends'
export const size = ogSize
export const contentType = 'image/png'

export default function Image() {
  return renderOgCard({
    label: 'Daily paper',
    headline: 'A paper between friends.',
    summary: 'Share links with your friends. The Grapevine turns them into stories, each with its own conversation.',
  })
}
