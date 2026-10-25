import { notFound } from 'next/navigation'
import FrontPage from '@/components/paper/FrontPage'
import type { RankedStory } from '@/lib/types'

/** A development-only fixture of the production component; no authenticated content. */
export default function DesignPreview() {
  if (process.env.NODE_ENV !== 'development') notFound()
  const members = ['Tim', 'Luka', 'Nika', 'Jure', 'Maja'].map((display_name, index) => ({ id: `00000000-0000-4000-8000-00000000000${index}`, display_name, monogram: display_name[0] }))
  const titles = ['Reusable booster lands at sea on its second try', 'Night buses to the coast stop for winter', 'Studio Kettle pushes Wayfarer to spring 2027', 'Third-tier side knock out the champions on penalties', 'Old town goes car-free on Sundays from March']
  const images = ['rocket', 'bus', 'games', 'crowd', 'city']
  const stories: RankedStory[] = titles.map((title, index) => ({
    id: `10000000-0000-4000-8000-00000000000${index}`, title, summary: 'A summary from the links your friends shared.', category: ['Space','Travel','Games','Sport','City'][index],
    last_activity_at: '2025-10-23T20:29:00Z', created_at: '2025-10-21T18:00:00Z',
    sources: Array.from({ length: index ? 2 : 4 }, (_, i) => ({ id: `${index}-${i}`, publisher: 'Orbit Weekly', domain: 'example.com', image_url: `/seed/${images[index]}.jpg`, submitted_by: members[(i+1)%5].id, created_at:'2025-10-23T19:12:00Z' })),
    messages: index ? [] : [{ id: 'message-1', story_id:'10000000-0000-4000-8000-000000000000', author_id: members[2].id, body:'This is the actual news.', created_at:'2025-10-23T20:29:00Z' }],
    sourceCount: index ? 2 : 4, messageCount: index ? 0 : 5, publishers:['Orbit Weekly'], newSources: index < 2 ? 1 : 0, newMessages:index ? 0 : 2, submitters:members.slice(1,3), imageUrl:`/seed/${images[index]}.jpg`,
  }))
  return <FrontPage staticPreview group={{id:'00000000-0000-4000-8000-000000000099', name:'The Grapevine', created_at:'2025-10-01T00:00:00Z'}} members={members} stories={stories} earlier="2025-10-20T00:00:00Z" loadedAt={new Date('2025-10-23T21:14:00Z')} />
}
