import { notFound } from 'next/navigation'
import { requireUser } from '@/lib/auth'
import { isUuid } from '@/lib/validate'
import { loadStory } from '@/components/story/queries'
import StoryReader from '@/components/story/StoryReader'
import './story.css'
import { connection } from 'next/server'

export default async function StoryPage({ params }: PageProps<'/story/[id]'>) {
  await connection() // fully dynamic: session + database
  const { id } = await params
  const { supabase, userId } = await requireUser(`/story/${id}`)
  if (!isUuid(id)) notFound()
  const loaded = await loadStory(supabase, id)
  if (!loaded) notFound()
  return <StoryReader initialStory={loaded.story} initialDetails={loaded.details} userId={userId} gifsEnabled={Boolean(process.env.GIPHY_API_KEY?.trim())} />
}
