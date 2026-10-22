'use client'

import Link from 'next/link'
import { Fragment, useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { createClient } from '@/lib/supabase/client'
import { loadStoryDetails, mergeTimeline, type Source, type Story, type Message } from './queries'
import Composer from './Composer'
import Plate from '@/components/print/Plate'
import { capture, correctionsEnabled } from '@/lib/telemetry'

const emojis = ['🚀', '👏', '😂', '❤️', '🔥', '👀']
const time = (date: string) => new Date(date).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Ljubljana' })
const day = (date: string) => new Date(date).toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'Europe/Ljubljana' })
const safeUrl = (url: string | null | undefined) => url && (/^https?:\/\//i.test(url) || /^\/(?!\/)/.test(url)) ? url : undefined
const publisher = (source: Source) => source.author || source.publisher || source.domain || 'Source'

const LockIcon = () => <svg className="story-icon" width="11" height="12" viewBox="0 0 11 12" aria-hidden="true"><rect x="1" y="5" width="9" height="6.5" rx="1" fill="currentColor" /><path d="M3 5V3.5a2.5 2.5 0 0 1 5 0V5" fill="none" stroke="currentColor" strokeWidth="1.3" /></svg>
const ReplyIcon = () => <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true"><path d="M7 4 2.5 8.5 7 13M3 8.5h7.5a5 5 0 0 1 5 5V15" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" /></svg>

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

export default function StoryReader({ initialStory, initialDetails, userId, gifsEnabled }: { initialStory: Story; initialDetails: Awaited<ReturnType<typeof loadStoryDetails>>; userId: string; gifsEnabled: boolean }) {
  const [story, setStory] = useState(initialStory)
  const [details, setDetails] = useState(initialDetails)
  const [lastViewed, setLastViewed] = useState<string | null>(null)
  const [reply, setReply] = useState<Message | null>(null)
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')
  const [summaryChanged, setSummaryChanged] = useState(false)
  const [reactionBusy, setReactionBusy] = useState<string | null>(null)
  const [reportBusy, setReportBusy] = useState(false)
  const [target, setTarget] = useState('summary')
  const [note, setNote] = useState('')
  const [reducedMotion, setReducedMotion] = useState(true)
  const [playingGifs, setPlayingGifs] = useState<string[]>([])
  const historyDialog = useRef<HTMLDialogElement>(null)
  const reportDialog = useRef<HTMLDialogElement>(null)
  const summary = useRef(initialStory.summary)
  const refreshing = useRef(false)
  const refresh = useCallback(async () => {
    if (refreshing.current) return
    refreshing.current = true
    try {
      const client = createClient()
      const { data, error } = await client.from('stories').select('*').eq('id', initialStory.id).single()
      if (error) throw error
      const next = data as Story
      const nextDetails = await loadStoryDetails(client, next)
      if (summary.current !== next.summary) setSummaryChanged(true)
      summary.current = next.summary
      setStory(next); setDetails(nextDetails); setError('')
    } catch { setError('Could not update the conversation. Your current edition is still here.'); }
    finally { refreshing.current = false }
  }, [initialStory.id])

  useEffect(() => {
    capture('story_opened')
    const key = `grapevine:story:${userId}:${initialStory.id}:viewed`
    // Browser-only reading history must load after hydration to keep the server markup stable.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    try { const previous = localStorage.getItem(key); if (previous && Number.isFinite(Date.parse(previous))) setLastViewed(previous); localStorage.setItem(key, new Date().toISOString()) } catch { /* Private browsing may disable local storage. */ }
    const saveView = () => { if (document.visibilityState === 'visible') { try { localStorage.setItem(key, new Date().toISOString()) } catch {} } }
    const media = window.matchMedia('(prefers-reduced-motion: reduce)')
    const motion = () => setReducedMotion(media.matches)
    motion(); media.addEventListener('change', motion)
    const client = createClient()
    const channel = client.channel(`story:${initialStory.id}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'messages', filter: `story_id=eq.${initialStory.id}` }, refresh)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'stories', filter: `id=eq.${initialStory.id}` }, refresh)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'sources', filter: `story_id=eq.${initialStory.id}` }, refresh)
      .subscribe()
    const poll = window.setInterval(() => { if (document.visibilityState === 'visible') { void refresh(); saveView() } }, 30000)
    return () => { saveView(); window.clearInterval(poll); media.removeEventListener('change', motion); void client.removeChannel(channel) }
  }, [initialStory.id, userId, refresh])
  useEffect(() => { if (!toast) return; const timer = setTimeout(() => setToast(''), 5000); return () => clearTimeout(timer) }, [toast])

  async function react(message: Message, emoji: string) {
    if (reactionBusy) return
    setReactionBusy(`${message.id}:${emoji}`)
    const client = createClient()
    const mine = message.reactions.find(reaction => reaction.user_id === userId && reaction.emoji === emoji)
    const { error } = mine ? await client.from('reactions').delete().eq('id', mine.id).eq('user_id', userId) : await client.from('reactions').insert({ message_id: message.id, user_id: userId, emoji })
    if (error) setError('Could not save your reaction. Try again.')
    else await refresh()
    setReactionBusy(null)
  }
  async function report(event: FormEvent) {
    event.preventDefault()
    if (!correctionsEnabled()) { setError('Corrections are temporarily unavailable.'); return }
    setReportBusy(true); setError('')
    const { error } = await createClient().functions.invoke('report-story', { body: { story_id: story.id, target, note } })
    setReportBusy(false)
    if (error) { setError('Could not send the report. Your note is kept; try again.'); return }
    reportDialog.current?.close(); setNote(''); setToast('Reported — it will be re-checked'); await refresh()
  }
  const person = (id: string | null) => details.people.find(person => person.id === id) || { display_name: 'Member', monogram: '?' }
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

    <main className="story-layout">
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
        {error && <p role="alert" className="meta story-error">{error} <button className="story-text-button" onClick={() => void refresh()}>Try again</button></p>}
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
            <article id={`${entry.kind}-${entry.id}`} className={`story-entry ${grouped ? 'story-grouped' : ''}`}>
              {!grouped ? <span className="av" aria-hidden="true">{author.monogram}</span> : <span className="story-avatar-space" />}
              <div className="story-entry-body">
                {!grouped && <div className="story-who"><b>{authorId === userId ? 'You' : author.display_name}</b>{entry.kind === 'source' && <span className="meta">filed a source</span>}<time className="meta" dateTime={entry.created_at}>{time(entry.created_at)}</time></div>}
                {entry.kind === 'source' ? <>{entry.submitter_comment && <p>{entry.submitter_comment}</p>}<SourceCard source={entry} /></> : <>
                  {parent && <div className="story-quote"><b>{person(parent.author_id).display_name}</b> {parent.body ? `${parent.body.slice(0, 100)}${parent.body.length > 100 ? '…' : ''}` : 'Attachment'}</div>}
                  {entry.body && <p>{entry.body}</p>}
                  {entry.message_media.sort((a, b) => a.position - b.position).map(media => <div key={media.id} className="story-media">
                    {media.kind === 'video' ? <video controls preload="metadata" src={safeUrl(media.display_url)} aria-label={media.alt_text || 'Shared video'} /> : media.kind === 'gif' && reducedMotion && !playingGifs.includes(media.id) ? <button className="story-gif-paused meta" onClick={() => setPlayingGifs([...playingGifs, media.id])}>▶ Play GIF · {media.alt_text || 'Animated reaction'}</button> : <Plate src={media.display_url} label="Attachment unavailable" alt={media.alt_text || 'Shared image'} className="story-media-plate reveal" screen={media.kind !== 'gif'} />}
                    {media.kind === 'gif' && <div className="meta">GIF · {media.alt_text || 'Animated reaction'}{playingGifs.includes(media.id) && <> · <button className="story-text-button" onClick={() => setPlayingGifs(playingGifs.filter(id => id !== media.id))}>Pause</button></>}</div>}
                  </div>)}
                  <div className="story-message-actions"><div className="story-reactions">{emojis.map(emoji => {
                    const reactions = entry.reactions.filter(reaction => reaction.emoji === emoji)
                    const mine = reactions.some(reaction => reaction.user_id === userId)
                    return <button key={emoji} aria-label={`React ${emoji}${reactions.length ? `, ${reactions.length} reactions` : ''}`} aria-pressed={mine} className={`${mine ? 'mine' : ''} ${reactions.length ? 'used' : ''}`} disabled={Boolean(reactionBusy)} onClick={() => void react(entry, emoji)}>{emoji}{reactions.length > 0 && <span className="tnum">{reactions.length}</span>}</button>
                  })}</div><button className="story-reply" aria-label={`Reply to ${author.display_name}'s message`} onClick={() => { setReply(entry); document.querySelector<HTMLInputElement>('.story-composer input[aria-label="Message"]')?.focus() }}><ReplyIcon /></button></div>
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
      <form onSubmit={report}><label>What is wrong?<select value={target} onChange={event => setTarget(event.target.value)}><option value="summary">Summary</option><option value="title">Title</option><option value="category">Category</option></select></label><label>Note (optional)<textarea value={note} onChange={event => setNote(event.target.value)} /></label>{error && <p role="alert" className="meta">{error}</p>}<button className="btn" disabled={reportBusy}>{reportBusy ? 'Reporting…' : 'Report'}</button></form>
    </dialog>
  </div>
}
