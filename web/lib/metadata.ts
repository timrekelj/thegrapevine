import type { Metadata } from 'next'

const siteName = 'The Grapevine'

// A page's `openGraph` and `twitter` replace the layout's rather than merging, so every
// page that customises its preview builds the full set here.
export function previewMetadata({ title, description, image, imageAlt }: { title: string; description: string; image: string; imageAlt: string }): Metadata {
  return {
    title,
    description,
    openGraph: { type: 'website', siteName, locale: 'en_GB', title, description, images: [{ url: image, width: 1200, height: 630, alt: imageAlt }] },
    twitter: { card: 'summary_large_image', title, description, images: [image] },
  }
}

export const sitePreview = previewMetadata({
  title: siteName,
  description: 'A private newspaper curated by friends.',
  image: '/opengraph-image',
  imageAlt: 'The Grapevine: a private newspaper curated by friends',
})

export const invitePreview = previewMetadata({
  title: 'You’re invited to The Grapevine',
  description: 'A friend wants you in their paper: a private newspaper, shared and discussed by the people in it.',
  image: '/invite/opengraph-image',
  imageAlt: 'You’re invited to a paper on The Grapevine',
})
