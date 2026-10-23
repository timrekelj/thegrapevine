'use client'

import Link from '@/components/print/PressLink'
import { Fragment, useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { createClient } from '@/lib/supabase/client'
import { loadStory, mergeTimeline, type Message, type Source, type Story, type StoryDetails } from './queries'
import Composer from './Composer'
import Plate from '@/components/print/Plate'
import Avatar from '@/components/print/Avatar'
import { BusyLabel } from '@/components/print/InkDots'
import ReportLink from '@/components/report/ReportLink'
import { capture, correctionsEnabled } from '@/lib/telemetry'
import { clock as time, dayline as day } from '@/lib/format'
import { safeUrl } from '@/lib/validate'

const emojis = ['👍', '❤️', '😂', '😮', '😢', '🔥', '👏', '🚀', '👀', '🎉']
// Only the emojis someone has used, in palette order.
const usedEmojis = (message: Message) => { const present = [...new Set(message.reactions.map(reaction => reaction.emoji))]; return [...emojis.filter(emoji => present.includes(emoji)), ...present.filter(emoji => !emojis.includes(emoji))] }
const publisher = (source: Source) => source.author || source.publisher || source.domain || 'Source'

// Line icons drawn like Lucide's (lock, reply, smile-plus), set in the current ink.
const Icon = ({ size, width, className, children }: { size: number; width: number; className?: string; children: ReactNode }) =>
  <svg className={className} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={width} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{children}</svg>
const LockIcon = () => <Icon size={12} width={2} className="story-icon"><rect width="18" height="11" x="3" y="11" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" /></Icon>
const ReplyIcon = () => <Icon size={18} width={1.75}><path d="M20 18v-2a4 4 0 0 0-4-4H4m5 5-5-5 5-5" /></Icon>
const ReactIcon = () => <Icon size={17} width={1.75}><path d="M13.267 2.08a10 10 0 1 0 8.653 8.653M15 10V9M9 10V9M16 5h6M19 2v6M16.472 15a6 6 0 0 1-8.943 0" /></Icon>

function SourceCard({ source }: { source: Source }) {
  const note = source.extraction_status === 'paywalled' ? 'Paywalled — headline only. The summary does not use this source.' : source.extraction_status === 'deleted' ? 'Post deleted or unavailable' : source.extraction_status === 'failed' ? "Couldn't read this page" : null
  return <div className="story-source-card">
    <Plate src={source.image_url} label={publisher(source)} className="story-source-thumb" />
    <div className="story-source-content"><b className="label">{publisher(source)}{source.author && ' · on X'}</b><div className="story-source-title">{source.extraction_status === 'failed' ? source.url : source.source_title || source.url}</div>
      <div className="meta story-source-foot"><time dateTime={source.created_at}>{time(source.created_at)}</time>{safeUrl(source.url) && <a href={safeUrl(source.url)} target="_blank" rel="noopener noreferrer">Open{source.author ? ' on X' : ''} ↗</a>}</div>
      {note && <div className="meta story-source-note">{source.extraction_status === 'paywalled' && <LockIcon />}{note}</div>}
    </div>
  </div>
}

export default function StoryReader({ initialStory, initialDetails, userId, gifsEnabled }: { initialStory: Story; initialDetails: StoryDetails; userId: string; gifsEnabled: boolean }) {
  const [story, setStory] = useState(initialStory)
  const [details, setDetails] = useState(initialDetails)
  const [lastViewed, setLastViewed] = useState<string | null>(null)
  const [reply, setReply] = useState<Message | null>(null)
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')
  const [summaryChanged, setSummaryChanged] = useState(false)
  const [picker, setPicker] = useState<string | null>(null)
  const [reportBusy, setReportBusy] = useState(false)
  const [retrying, setRetrying] = useState(false)
  // The reaction just toggled here, whose count is inked again (`.story-pop`).
  const [bumped, setBumped] = useState('')
  const [target, setTarget] = useState('summary')
  const [note, setNote] = useState('')
  const [reducedMotion, setReducedMotion] = useState(true)
  const [playingGifs, setPlayingGifs] = useState<string[]>([])
  const historyDialog = useRef<HTMLDialogElement>(null)
  const reportDialog = useRef<HTMLDialogElement>(null)
  const summary = useRef(initialStory.summary)
  const refreshing = useRef(false)
  const refreshAgain = useRef(false)
  // Read by the realtime handler, which is subscribed once per story.
  const messageIds = useRef(new Set(initialDetails.messages.map(message => message.id)))
  useEffect(() => { messageIds.current = new Set(details.messages.map(message => message.id)) }, [details.messages])
  const messages = useRef(initialDetails.messages)
  const reactionBusy = useRef(new Set<string>())
  const longPress = useRef<number | undefined>(undefined)
  const refresh = useCallback(async () => {
    // A change that lands mid-refresh may have been missed by it: run once more afterwards.
    if (refreshing.current) { refreshAgain.current = true; return }
    refreshing.current = true
    try {
      do {
        refreshAgain.current = false
        const loaded = await loadStory(createClient(), initialStory.id, messages.current)
        if (!loaded) throw new Error('Story unavailable')
        const { story: next, details: nextDetails } = loaded
        if (summary.current !== next.summary) setSummaryChanged(true)
        summary.current = next.summary
        messages.current = nextDetails.messages
        setStory(next); setDetails(nextDetails); setError('')
      } while (refreshAgain.current)
    } catch { setError('Could not update the conversation. Your current edition is still here.'); }
    finally { refreshing.current = false }
  }, [initialStory.id])

  useEffect(() => {
    capture('story_opened')
    const client = createClient()
    // The reading position is kept per member on the server, so reading here clears "new" on the
    // phone too. The first call returns the previous visit, which places the divider for this visit;
    // later calls just move the position on. Without it there is simply no divider.
    const markRead = () => client.rpc('mark_story_read', { story: initialStory.id })
    void markRead().then(({ data }) => { if (typeof data === 'string') setLastViewed(data) })
    const saveView = () => { if (document.visibilityState === 'visible') void markRead() }
    const media = window.matchMedia('(prefers-reduced-motion: reduce)')
    const motion = () => setReducedMotion(media.matches)
    motion(); media.addEventListener('change', motion)
    const channel = client.channel(`story:${initialStory.id}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'messages', filter: `story_id=eq.${initialStory.id}` }, refresh)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'stories', filter: `id=eq.${initialStory.id}` }, refresh)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'sources', filter: `story_id=eq.${initialStory.id}` }, refresh)
      // Reactions carry no story id to filter on: refresh for this story's messages. A removal only
      // carries the reaction's id, so any removal in the paper refreshes.
      .on('postgres_changes', { event: '*', schema: 'public', table: 'reactions' }, payload => {
        const messageId = (payload.new as { message_id?: string } | null)?.message_id
        if (!messageId || messageIds.current.has(messageId)) void refresh()
      })
      .subscribe()
    const poll = window.setInterval(() => { if (document.visibilityState === 'visible') { void refresh(); saveView() } }, 30000)
    return () => { saveView(); window.clearInterval(poll); media.removeEventListener('change', motion); void client.removeChannel(channel) }
  }, [initialStory.id, refresh])
  useEffect(() => {
    if (!picker) return
    const close = (event: Event) => { if (event instanceof KeyboardEvent ? event.key === 'Escape' : !(event.target as Element).closest('.story-reaction-picker, .story-add-reaction')) setPicker(null) }
    document.addEventListener('pointerdown', close); document.addEventListener('keydown', close)
    return () => { document.removeEventListener('pointerdown', close); document.removeEventListener('keydown', close) }
  }, [picker])
  useEffect(() => { if (!toast) return; const timer = setTimeout(() => setToast(''), 5000); return () => clearTimeout(timer) }, [toast])

  async function react(message: Message, emoji: string) {
    setPicker(null)
    const key = `${message.id}:${emoji}`
    setBumped(key)
    // A second tap while the first is still saving would repeat the same insert or delete.
    if (reactionBusy.current.has(key)) return
    reactionBusy.current.add(key)
    const mine = message.reactions.some(reaction => reaction.user_id === userId && reaction.emoji === emoji)
    const setReactions = (update: (reactions: Message['reactions']) => Message['reactions']) => setDetails(details => ({ ...details, messages: details.messages.map(other => other.id === message.id ? { ...other, reactions: update(other.reactions) } : other) }))
    setReactions(reactions => mine ? reactions.filter(reaction => !(reaction.user_id === userId && reaction.emoji === emoji)) : [...reactions, { id: crypto.randomUUID(), user_id: userId, emoji }])
    const client = createClient()
    // By key rather than id: an optimistic reaction has no server id yet. A duplicate insert means it is already saved.
    const { error } = mine ? await client.from('reactions').delete().eq('message_id', message.id).eq('user_id', userId).eq('emoji', emoji) : await client.from('reactions').insert({ message_id: message.id, user_id: userId, emoji })
    if (error && error.code !== '23505') { setReactions(() => message.reactions); setError('Could not save your reaction. Try again.') }
    else await refresh()
    reactionBusy.current.delete(key)
  }
  const holdToReact = (id: string) => ({
    onPointerDown: (event: React.PointerEvent) => { if (event.pointerType === 'touch') longPress.current = window.setTimeout(() => setPicker(id), 450) },
    onPointerUp: () => window.clearTimeout(longPress.current),
    onPointerMove: () => window.clearTimeout(longPress.current),
    onPointerCancel: () => window.clearTimeout(longPress.current),
  })
  async function report(event: FormEvent) {
    event.preventDefault()
    if (!correctionsEnabled()) { setError('Corrections are temporarily unavailable.'); return }
    setReportBusy(true); setError('')
    const { error } = await createClient().functions.invoke('report-story', { body: { story_id: story.id, target, note } })
    setReportBusy(false)
    if (error) {
      // The function explains rate limits and bad input in `error`; show that rather than a generic line.
      const failure = error.context instanceof Response ? await error.context.json().catch(() => null) : null
      setError(`${typeof failure?.error === 'string' ? failure.error : 'Could not send the report.'} Your note is kept; try again.`)
      return
    }
    reportDialog.current?.close(); setNote(''); setToast('Reported — it will be re-checked'); await refresh()
  }
  const person = (id: string | null) => details.people.find(person => person.id === id) || { id: id ?? 'member', display_name: 'Member', monogram: '?' }
  const timeline = mergeTimeline(details.sources, details.messages)
  const unread = lastViewed ? timeline.filter(entry => entry.kind === 'message' && Date.parse(entry.created_at) > Date.parse(lastViewed) && entry.author_id !== userId) : []
  const firstUnread = unread[0]?.id
  const lead = [...details.sources].reverse().find(source => source.image_url) || details.sources[0]
  const openHistory = () => historyDialog.current?.showModal()
  const openReport = (target: string) => { setTarget(target); reportDialog.current?.showModal() }
  // Story links open the app on phones that have it, and this page everywhere else.
  async function shareStory() {
    const url = `${window.location.origin}/story/${story.id}`
    if (navigator.share) { try { await navigator.share({ title: story.title, url }); return } catch { return } }
    await navigator.clipboard.writeText(url)
    setToast('Link copied')
  }

  const okSources = details.sources.filter(source => source.extraction_status === 'ok').length
  const isNew = (date: string) => Boolean(lastViewed && Date.parse(date) > Date.parse(lastViewed))

  return <div className="story-page">
    <header className="story-top">
      <Link href="/" className="story-back meta">← Front page</Link>
      <Link href="/" className="masthead">The Grapevine</Link>
      <button className="story-text-button meta" onClick={() => void shareStory()}>Share story</button>
    </header>
    <div className="double-rule" />

    <main className="story-layout fresh-ink">
      <article className="story-article">
        <div className="story-kicker"><span className="label">{story.category || 'Unfiled'}</span><span className="meta">Updated {time(details.history[0]?.created_at || story.last_activity_at)}</span></div>
        <h1 className="display story-title">{story.title || 'Reading this story'}</h1>
        <p className="meta story-byline">Filed from <b>{okSources}</b> of <b>{details.sources.length}</b> {details.sources.length === 1 ? 'source' : 'sources'} · <button className="story-text-button" onClick={openHistory}>What changed</button></p>
        <figure className="story-figure">
          <Plate src={lead?.image_url} label={lead ? publisher(lead) : 'The paper'} className="story-hero" eager />
          {lead && <figcaption className="meta">Pictured via {publisher(lead)}</figcaption>}
        </figure>
        <p className={`story-summary ${story.summary ? 'column dropcap' : ''}`}>{story.summary || 'The summary could not be written.'}</p>
        {!story.summary && <button className="btn ghost" onClick={() => openReport('summary')}>Retry summary</button>}
        <div className="story-correction meta">Something off? <button className="story-text-button" onClick={() => openReport('summary')}>Report the summary</button> · <button className="story-text-button" onClick={() => openReport('category')}>wrong category</button>{story.flagged && <span className="story-flag">Flagged for review</span>}</div>
      </article>

      <aside className="story-clippings" aria-label="Sources, newest first">
        <h2 className="label story-clippings-head">Clippings <span className="meta">{details.sources.length}</span></h2>
        <div className="story-clipping-list">{details.sources.map(source => <a className="story-clipping" key={source.id} href={`#source-${source.id}`}>
          <Plate src={source.image_url} label={publisher(source)} className="story-clipping-thumb" />
          <div><b className={`meta ${isNew(source.created_at) ? 'new' : ''}`}>{publisher(source)}</b><div className="story-clipping-title">{source.source_title || source.url}</div>{source.extraction_status === 'paywalled' && <small className="meta"><LockIcon /> Headline only</small>}</div>
        </a>)}</div>
      </aside>

      <section aria-labelledby="story-conversation-title" className="story-conversation">
        <h2 id="story-conversation-title" className="story-section-head">The conversation</h2>
        {error && <p role="alert" className="meta story-error">{error} <button className="story-text-button" disabled={retrying} aria-busy={retrying} onClick={() => { setRetrying(true); void refresh().finally(() => setRetrying(false)) }}><BusyLabel busy={retrying} label="Trying again…">Try again</BusyLabel></button> <ReportLink error={error} /></p>}
        {timeline.length === 0 && <p className="summary">Start the conversation.</p>}
        {timeline.map((entry, index) => {
          const previous = timeline[index - 1]
          const newDay = !previous || day(previous.created_at) !== day(entry.created_at)
          const authorId = entry.kind === 'message' ? entry.author_id : entry.submitted_by
          const author = person(authorId)
          const grouped = entry.kind === 'message' && previous?.kind === 'message' && previous.author_id === authorId && !newDay && entry.id !== firstUnread
          const parent = entry.kind === 'message' && entry.reply_to ? details.messages.find(message => message.id === entry.reply_to) : null
          return <Fragment key={entry.id}>
            {newDay && <div className="story-day label">{day(entry.created_at)}</div>}
            {entry.id === firstUnread && lastViewed && <div className="story-new-divider label">{unread.length} new since {time(lastViewed)}</div>}
            <article id={`${entry.kind}-${entry.id}`} className={`story-entry ${grouped ? 'story-grouped' : ''}`} {...(entry.kind === 'message' ? holdToReact(entry.id) : {})}>
              {!grouped ? <Avatar seed={author.id} /> : <span className="story-avatar-space" />}
              <div className="story-entry-body">
                {!grouped && <div className="story-who"><b>{authorId === userId ? 'You' : author.display_name}</b>{entry.kind === 'source' && <span className="meta">filed a source</span>}<time className="meta" dateTime={entry.created_at}>{time(entry.created_at)}</time></div>}
                {entry.kind === 'source' ? <>{entry.submitter_comment && <p>{entry.submitter_comment}</p>}<SourceCard source={entry} /></> : <>
                  {parent && <div className="story-quote"><b>{person(parent.author_id).display_name}</b> {parent.body ? `${parent.body.slice(0, 100)}${parent.body.length > 100 ? '…' : ''}` : 'Attachment'}</div>}
                  {entry.body && <p>{entry.body}</p>}
                  {entry.message_media.map(media => <div key={media.id} className="story-media">
                    {media.kind === 'video' && media.display_url ? <video controls preload="metadata" src={safeUrl(media.display_url)} aria-label={media.alt_text || 'Shared video'} /> : media.kind === 'gif' && reducedMotion && !playingGifs.includes(media.id) ? <button className="story-gif-paused meta" onClick={() => setPlayingGifs([...playingGifs, media.id])}>▶ Play GIF · {media.alt_text || 'Animated reaction'}</button> : <Plate src={media.display_url} label="Attachment unavailable" alt={media.alt_text || 'Shared image'} className="story-media-plate reveal" screen={media.kind !== 'gif'} />}
                    {media.kind === 'gif' && <div className="meta">GIF · {media.alt_text || 'Animated reaction'}{playingGifs.includes(media.id) && <> · <button className="story-text-button" onClick={() => setPlayingGifs(playingGifs.filter(id => id !== media.id))}>Pause</button></>}</div>}
                  </div>)}
                  <div className="story-message-actions"><div className="story-reactions">{usedEmojis(entry).map(emoji => {
                    const reactions = entry.reactions.filter(reaction => reaction.emoji === emoji)
                    const mine = reactions.some(reaction => reaction.user_id === userId)
                    return <button key={emoji} aria-label={`${emoji}, ${reactions.length} ${reactions.length === 1 ? 'reaction' : 'reactions'}`} aria-pressed={mine} className={mine ? 'mine' : ''} onClick={() => void react(entry, emoji)}>{emoji}<span key={reactions.length} className={`tnum ${bumped === `${entry.id}:${emoji}` ? 'story-pop' : ''}`}>{reactions.length}</span></button>
                  })}
                    <button className="story-add-reaction" aria-label="Add reaction" aria-expanded={picker === entry.id} onClick={() => setPicker(picker === entry.id ? null : entry.id)}><ReactIcon /></button>
                    {picker === entry.id && <div className="story-reaction-picker" role="menu" aria-label="Reactions">{emojis.map(emoji => {
                      const mine = entry.reactions.some(reaction => reaction.user_id === userId && reaction.emoji === emoji)
                      return <button key={emoji} role="menuitemcheckbox" aria-checked={mine} aria-label={mine ? `Remove ${emoji}` : `React ${emoji}`} className={mine ? 'mine' : ''} onClick={() => void react(entry, emoji)}>{emoji}</button>
                    })}</div>}
                  </div><button className="story-reply" aria-label={`Reply to ${author.display_name}'s message`} onClick={() => { setReply(entry); document.querySelector<HTMLInputElement>('.story-composer input[aria-label="Message"]')?.focus() }}><ReplyIcon /></button></div>
                </>}
              </div>
            </article>
          </Fragment>
        })}
        {summaryChanged && <p className="meta story-system">Summary updated · <button className="story-text-button" onClick={openHistory}>What changed</button></p>}
        <Composer story={story} userId={userId} reply={reply} clearReply={() => setReply(null)} refresh={refresh} gifsEnabled={gifsEnabled} />
      </section>
    </main>

    {toast && <div role="status" className="story-toast">{toast}</div>}
    <dialog ref={historyDialog} className="story-dialog" aria-labelledby="story-history-title">
      <header className="story-dialog-head"><h2 id="story-history-title" className="headline">What changed</h2><button className="story-dialog-close meta" onClick={() => historyDialog.current?.close()}>Close</button></header>
      {details.history.length ? details.history.map(version => <article key={version.id}><time className="meta">{day(version.created_at)} · {time(version.created_at)}</time><h3>{version.title}</h3><p className="summary">{version.summary}</p></article>) : <p>No previous summaries yet.</p>}
    </dialog>
    <dialog ref={reportDialog} className="story-dialog" aria-labelledby="story-report-title">
      <header className="story-dialog-head"><h2 id="story-report-title" className="headline">Report a correction</h2><button className="story-dialog-close meta" onClick={() => reportDialog.current?.close()}>Close</button></header>
      <form onSubmit={report}><label>What is wrong?<select value={target} onChange={event => setTarget(event.target.value)}><option value="summary">Summary</option><option value="title">Title</option><option value="category">Category</option></select></label><label>Note (optional)<textarea value={note} onChange={event => setNote(event.target.value)} /></label>{error && <p role="alert" className="meta">{error}</p>}<button className="btn" disabled={reportBusy} aria-busy={reportBusy}><BusyLabel busy={reportBusy} label="Reporting…">Report</BusyLabel></button></form>
    </dialog>
  </div>
}
