import Link from '@/components/print/PressLink'
import type { PaperMember, RankedStory } from '@/lib/types'
import Plate from '@/components/print/Plate'
import Avatar from '@/components/print/Avatar'
import Bunch from '@/components/print/Bunch'
import LaterEdition from './later-edition'
import ShareLink from './share-link'
import { clock, dayline, plural } from '@/lib/format'
import styles from './paper.module.css'

const title = (story: RankedStory) => story.title || 'A link from the table'

function Preview({ story, members }: { story: RankedStory; members: PaperMember[] }) {
  const latest = [...story.messages].sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at))[0]
  if (!latest) return null
  const author = members.find(member => member.id === latest.author_id)
  return <div className={styles.preview}><Avatar seed={latest.author_id} name={author?.display_name} order={members.map(member => member.id)} /><p><b>{author?.display_name || 'A member'}</b> {latest.body || 'Shared an attachment.'}</p><time className="meta">{clock(latest.created_at)}</time></div>
}

export default function FrontPage({ group, members, stories, earlier = null, loadedAt, staticPreview = false }: {
  group: { id: string; name: string; created_at: string }
  members: PaperMember[]; stories: RankedStory[]; earlier?: string | null; loadedAt: Date; staticPreview?: boolean
}) {
  const order = members.map(member => member.id)
  const [lead, ...rest] = stories
  const fresh = stories.reduce((count, story) => count + story.newSources + story.newMessages, 0)
  const latestAuthor = (story: RankedStory) => {
    const message = [...story.messages].sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at))[0]
    const source = [...story.sources].sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at))[0]
    const entry = message && (!source || message.created_at > source.created_at) ? message : source
    const id = entry && ('author_id' in entry ? entry.author_id : entry.submitted_by)
    const name = members.find(member => member.id === id)?.display_name
    return `${story.category || 'News'}${name ? ` · ${name}` : ''} · ${clock(story.last_activity_at)}`
  }
  return <main className={`fresh-ink ${styles.paper}`}>
    <header className={styles.head}>
      <div className={styles.headerRow}><h1 className={styles.nameplate}>{group.name}</h1><span className={styles.accountLinks}><Link className={styles.account} href="/report?kind=bug&from=%2F">Feedback</Link><Link className={styles.account} href="/settings">Your account <span aria-hidden="true">↗</span></Link></span></div>
      <div className={styles.dateline}><div><Link href={earlier ? `/archive?${new URLSearchParams({ before: earlier })}` : '/archive'} className={styles.date}>{dayline(loadedAt)} <span aria-hidden="true">›</span></Link><div className={styles.fresh}>{fresh ? plural(fresh, 'new contribution') : 'You’re all caught up'}</div></div>
        <details className="people-menu"><summary aria-label={`People in ${group.name}`}>{members.slice(0, 5).map(member => <Avatar key={member.id} seed={member.id} name={member.display_name} order={order} />)}<span className="sr-only">People</span></summary><div className="people-panel"><h2>People</h2><div className="member-list">{members.map(member => <div key={member.id}><Avatar seed={member.id} name={member.display_name} order={order} /><span>{member.display_name}</span></div>)}</div><Link href="/settings" className="btn ghost">Paper settings</Link></div></details>
      </div>
    </header>
    {!staticPreview && <LaterEdition key={stories.map(story => story.id).join(',')} groupId={group.id} order={stories.map(story => story.id)} />}
    {lead ? <section className={styles.fold} aria-label="Front page">
      <article className={styles.lead}><Link href={`/story/${lead.id}`}>
        <div className={styles.leadPhoto}><Plate src={lead.imageUrl} label={lead.publishers[0] || 'The paper'} className={styles.hero} eager />{lead.newSources + lead.newMessages > 0 && <span className={`stamp ${styles.badge}`}>{lead.newSources + lead.newMessages} new</span>}</div>
        <div className={styles.leadBody}><div className="meta">{latestAuthor(lead)}</div><h2 className={styles.leadHead}>{title(lead)}</h2><div className={styles.bunchCounts}><Bunch sources={lead.sources} fresh={lead.newSources} size={44} order={order} /><div><div>{plural(lead.sourceCount, 'source')}{lead.newSources > 0 && `, ${lead.newSources} new`}</div><div>{plural(lead.messageCount, 'message')}</div></div></div><Preview story={lead} members={members} /></div>
      </Link></article>
      <div className={styles.storyList}>{rest.map(story => <article key={story.id}><Link className={styles.storyRow} href={`/story/${story.id}`}><Bunch sources={story.sources} fresh={story.newSources} size={34} order={order} /><div className={styles.rowBody}><h3>{title(story)}</h3>{story.newSources + story.newMessages > 0 && <div className={styles.fresh}>{plural(story.newSources + story.newMessages, 'new contribution')}</div>}<div className="meta">{latestAuthor(story)}</div><div className={`meta ${styles.rowCounts}`}>{plural(story.sourceCount, 'source')} · {plural(story.messageCount, 'message')}</div></div><Plate src={story.imageUrl} label={story.publishers[0] || 'The paper'} className={styles.rowPhoto} /></Link></article>)}</div>
    </section> : <section className={styles.empty}><Bunch sources={[]} size={80} /><h2 className="display">Share the first link<br />to start the paper.</h2><ShareLink groupId={group.id} groupName={group.name} /></section>}
    {earlier && <Link className={styles.earlier} href={`/archive?${new URLSearchParams({ before: earlier })}`}>Earlier editions <span aria-hidden="true">→</span></Link>}
    {lead && <div className={styles.floatingShare}><ShareLink groupId={group.id} groupName={group.name} /></div>}
  </main>
}
