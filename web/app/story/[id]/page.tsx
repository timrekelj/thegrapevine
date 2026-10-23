import { notFound } from 'next/navigation'
import { requireUser } from '@/lib/auth'
import { isUuid } from '@/lib/validate'
import { loadStory, loadStoryDetails } from '@/components/story/queries'
import StoryReader from '@/components/story/StoryReader'
import './story.css'
import { connection } from 'next/server'

export default async function StoryPage({ params }: PageProps<'/story/[id]'>) {
  await connection() // fully dynamic: session + database
  const { id } = await params
  const { supabase, user } = await requireUser(`/story/${id}`)
  if (!isUuid(id)) notFound()
  const story = await loadStory(supabase, id)
  if (!story) notFound()
  const details = await loadStoryDetails(supabase, story)
  return <StoryReader initialStory={story} initialDetails={details} userId={user.id} gifsEnabled={Boolean(process.env.GIPHY_API_KEY?.trim())} />
}
