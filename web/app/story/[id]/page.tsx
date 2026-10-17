import { notFound, redirect } from 'next/navigation'
import { createClient } from '@/lib/supabase/server'
import { loadStoryDetails, type Story } from '@/components/story/queries'
import StoryReader from '@/components/story/StoryReader'
import './story.css'
import { connection } from 'next/server'

export default async function StoryPage({ params }: { params: Promise<{ id: string }> }) {
  await connection() // fully dynamic: session + database
  const { id } = await params
  const client = await createClient()
  const { data: { user } } = await client.auth.getUser()
  if (!user) redirect(`/login?${new URLSearchParams({ redirect: `/story/${id}` })}`)
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) notFound()
  const { data, error } = await client.from('stories').select('*').eq('id', id).maybeSingle()
  if (error) throw new Error('Could not load this story. Please try again.')
  if (!data) notFound()
  const story = data as Story
  const details = await loadStoryDetails(client, story)
  return <StoryReader initialStory={story} initialDetails={details} userId={user.id} gifsEnabled={Boolean(process.env.GIPHY_API_KEY?.trim())} />
}
