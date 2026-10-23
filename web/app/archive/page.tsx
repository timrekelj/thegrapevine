import type { Metadata } from 'next'
import Link from '@/components/print/PressLink'
import { redirect } from 'next/navigation'
import { connection } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { currentUserId } from '@/lib/auth'
import { loadArchive } from '@/lib/queries'
import { dayline, plural } from '@/lib/format'
import styles from '@/components/paper/paper.module.css'

export const metadata: Metadata = { title: 'Earlier editions · The Grapevine' }

export default async function ArchivePage({ searchParams }: PageProps<'/archive'>) {
  await connection() // fully dynamic: session + database
  const params = await searchParams
  // Any date Date.parse accepts, printed back as ISO: Postgres doesn't read every form JavaScript does.
  const requested = typeof params.before === 'string' ? new Date(params.before) : null
  const before = (requested && Number.isFinite(requested.getTime()) ? requested : new Date()).toISOString()
  const client = await createClient()
  const userId = await currentUserId(client)
  if (!userId) redirect(`/login?${new URLSearchParams({ redirect: `/archive?${new URLSearchParams({ before })}` })}`)
  const { data: membership, error: membershipError } = await client.from('group_members')
    .select('group_id,groups(name)').eq('user_id', userId).order('joined_at').limit(1).maybeSingle()
  if (membershipError) throw membershipError
  if (!membership) redirect('/onboarding')
  const name = (membership.groups as unknown as { name: string } | null)?.name ?? 'The paper'
  const { stories, earlier } = await loadArchive(client, membership.group_id, before)
  // Stories under the day they were last active, newest first.
  const days = Map.groupBy(stories, story => dayline(story.last_activity_at, true))

  return <main className={styles.paper}>
    <header className={styles.archiveHead}>
      <Link href="/" className="meta">← Front page</Link>
      <span className="masthead">{name}</span>
      <span />
    </header>
    <div className="double-rule" />
    <section className={styles.archive}>
      <h1 className={styles.sectionHead}>Earlier editions</h1>
      {stories.length === 0 && <p className="summary">Nothing older yet.</p>}
      {[...days].map(([day, stories]) => <section key={day} className={styles.archiveDay}>
        <h2 className="label">{day}</h2>
        {stories.map(story => <Link key={story.id} href={`/story/${story.id}`} className={styles.archiveStory}>
          <span className="label">{story.category || 'News'}</span>
          <h3 className="headline">{story.title || 'A link from the table'}</h3>
          <span className="meta tnum">{plural(story.sources[0]?.count ?? 0, 'source')} · {plural(story.messages[0]?.count ?? 0, 'message')}</span>
        </Link>)}
      </section>)}
      {earlier && <Link className={styles.earlier} href={`/archive?${new URLSearchParams({ before: earlier })}`}>Earlier →</Link>}
    </section>
  </main>
}
