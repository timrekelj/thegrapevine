import Link from '@/components/print/PressLink'
import type { PaperMember, RankedStory } from '@/lib/types'
import Plate from '@/components/print/Plate'
import Avatar from '@/components/print/Avatar'
import LaterEdition from './later-edition'
import ShareLink from './share-link'
import { clock, dayline, plural } from '@/lib/format'
import styles from './paper.module.css'

const roman = (n: number) => [[10, 'X'], [9, 'IX'], [5, 'V'], [4, 'IV'], [1, 'I']]
  .reduce<[string, number]>(([out, left], [value, glyph]) => {
    while (left >= (value as number)) { out += glyph; left -= value as number }
    return [out, left]
  }, ['', n])[0]

function StoryMeta({ story, publishers = false }: { story: RankedStory; publishers?: boolean }) {
  return <div className={`meta tnum ${styles.meta}`}>
    <span><b>{story.sourceCount}</b> {story.sourceCount === 1 ? 'source' : 'sources'}{publishers && story.publishers.length > 0 ? `: ${story.publishers.join(', ')}` : ''}</span>
    <span><b>{story.messageCount}</b> {story.messageCount === 1 ? 'message' : 'messages'}</span>
    {story.newSources > 0 && <span className="new">{plural(story.newSources, 'new source')}</span>}
    {story.newMessages > 0 && <span className="new">{plural(story.newMessages, 'new message')}</span>}
    {story.submitters.length > 0 && <span className={styles.submitters} aria-label={`Shared by ${story.submitters.map(m => m.display_name).join(', ')}`}>
      {story.submitters.map(m => <Avatar key={m.id} seed={m.id} name={m.display_name} />)}
    </span>}
  </div>
}

const title = (story: RankedStory) => story.title || 'A link from the table'
const label = (story: RankedStory) => story.publishers[0] || 'The paper'

export default function FrontPage({ group, members, stories, earlier = null, loadedAt }: {
  group: { id: string; name: string; created_at: string }
  members: PaperMember[]
  stories: RankedStory[]
  /** Archive cursor, when stories older than this edition exist. */
  earlier?: string | null
  loadedAt: Date
}) {
  const [lead, ...rest] = stories
  const side = rest.slice(0, 3)
  const inside = rest.slice(3)
  const categories = [...new Set(stories.map(s => s.category).filter(Boolean))]
  const fresh = stories.filter(s => s.newSources > 0 || s.newMessages > 0).length
  const founded = new Date(group.created_at)
  // Every day since the paper was founded is an edition; every year a volume.
  const edition = Math.max(1, Math.floor((loadedAt.getTime() - founded.getTime()) / 86_400_000) + 1)
  const volume = roman(Math.floor((edition - 1) / 365) + 1)
  const name = (id: string) => members.find(m => m.id === id)?.display_name || 'A member'
  const conversation = stories.flatMap(story => story.messages.map(message => ({ ...message, story })))
    .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at) || a.id.localeCompare(b.id)).slice(0, 4)

  return <main className={`fresh-ink ${styles.paper}`}>
    <header className={styles.head}>
      <h1 className={`nameplate ${styles.nameplate}`}>{group.name}</h1>
      <div className="double-rule" />
      <div className={styles.dateline}>
        <span className={styles.edition}>Vol. {volume} · No. {edition}</span>
        <time className={styles.date} dateTime={loadedAt.toISOString()}>{dayline(loadedAt, true)}</time>
        <span className={`tnum ${styles.count}`}><span>{plural(stories.length, 'story', 'stories')}{fresh > 0 && <> · <span className={styles.fresh}>{fresh} new</span></>}</span>
          <span className={styles.members} aria-label="At the table">{members.map(member => <Avatar key={member.id} seed={member.id} name={member.display_name} />)}</span>
        </span>
      </div>
      <nav className={styles.sections} aria-label="Sections">
        <div className={styles.sectionList}><b>Front page</b>{categories.map(category => <span key={category}>{category}</span>)}</div>
        <div className={styles.tools}>
          <Link href="/report?kind=bug&from=%2F" className={styles.account}>Feedback</Link>
          <Link href="/settings" className={styles.account}>Account</Link>
          <ShareLink groupId={group.id} groupName={group.name} />
        </div>
      </nav>
    </header>

    <LaterEdition key={stories.map(story => story.id).join(',')} groupId={group.id} order={stories.map(story => story.id)} />

    {lead ? <>
      <section className={`${styles.fold} ${side.length ? '' : styles.alone}`} aria-label="Front page">
        <article className={styles.lead}>
          <Link href={`/story/${lead.id}`}>
            <div className={styles.kicker}><span className="label">{lead.category || 'News'}</span>{lead.newSources > 0 && <span className="stamp rest">+{plural(lead.newSources, 'source')}</span>}</div>
            <h2 className={`display ${styles.leadHead}`}>{title(lead)}</h2>
            <figure className={styles.figure}>
              <Plate src={lead.imageUrl} label={label(lead)} className={styles.hero} eager />
              {lead.publishers.length > 0 && <figcaption className="meta">Pictured via {lead.publishers[0]}{lead.submitters[0] ? `, shared by ${lead.submitters[0].display_name}` : ''}</figcaption>}
            </figure>
            <p className={`column dropcap ${styles.leadSummary}`}>{lead.summary || 'The summary is still being prepared. Open the story to read the shared link.'}</p>
            <StoryMeta story={lead} publishers />
          </Link>
        </article>
        {side.length > 0 && <aside className={styles.side} aria-label="More stories">{side.map((story, i) => <article key={story.id}>
          <Link className={styles.sideStory} href={`/story/${story.id}`}>
            {i === 0 && <Plate src={story.imageUrl} label={label(story)} className={styles.sidePlate} inkIn />}
            <span className="label">{story.category || 'News'}</span>
            <h3 className={`headline ${i === 0 ? styles.sideFirst : ''}`}>{title(story)}</h3>
            {i === 0 && story.summary && <p className={`summary ${styles.clamp}`}>{story.summary}</p>}
            <StoryMeta story={story} />
          </Link>
        </article>)}</aside>}
      </section>

      {inside.length > 0 && <section className={styles.inside}>
        <h2 className={styles.sectionHead}>Also in this edition</h2>
        <div className={styles.ruled}><div className={styles.insideGrid}>{inside.map(story => <article key={story.id}>
          <Link className={styles.card} href={`/story/${story.id}`}>
            <Plate src={story.imageUrl} label={label(story)} className={styles.cardPlate} inkIn />
            <div className={styles.cardText}>
              <span className="label">{story.category || 'News'}</span>
              <h3 className="headline">{title(story)}</h3>
              {story.summary && <p className={`summary ${styles.clamp}`}>{story.summary}</p>}
              <StoryMeta story={story} />
            </div>
          </Link>
        </article>)}</div></div>
      </section>}
    </> : <section className={styles.empty}>
      <p className="label">Stop the presses</p>
      <h2 className="display">This edition is waiting for its first story.</h2>
      <p className="summary">Share a link and it becomes the lead. Links about the same event are filed together, and every story gets its own conversation.</p>
      <ShareLink groupId={group.id} groupName={group.name} />
      <div className={styles.ghost} aria-hidden="true">{[0, 1, 2].map(i => <div key={i}><i /><i /><i /><i /><i /></div>)}</div>
    </section>}

    {conversation.length > 0 && <section className={styles.letters}>
      <h2 className={styles.sectionHead}>Letters from the table</h2>
      <div className={styles.ruled}><div className={styles.letterGrid}>{conversation.map(message => <Link className={styles.letter} href={`/story/${message.story_id}`} key={message.id}>
        <blockquote>{message.body || 'Shared something with the table.'}</blockquote>
        <div className="meta"><b>{name(message.author_id)}</b> on {message.story.title} · <time dateTime={message.created_at}>{clock(message.created_at)}</time></div>
      </Link>)}</div></div>
    </section>}

    {earlier && <Link className={styles.earlier} href={`/archive?${new URLSearchParams({ before: earlier })}`}>Earlier editions →</Link>}

    <footer className={styles.colophon}>
      <span className="masthead">The Grapevine</span>
      <span className="meta tnum">Printed at {clock(loadedAt)} for {plural(members.length, 'reader')} · Private edition</span>
    </footer>
  </main>
}
