'use client'

import Link from 'next/link'
import { Fragment, useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { createClient } from '@/lib/supabase/client'
import { loadStoryDetails, mergeTimeline, type Source, type Story, type Message } from './queries'
import Composer from './Composer'

const emojis = ['🚀', '👏', '😂', '❤️', '🔥', '👀']
const time = (date: string) => new Date(date).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Ljubljana' })
const day = (date: string) => new Date(date).toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'Europe/Ljubljana' })
const safeUrl = (url: string | null | undefined) => url && (/^https?:\/\//i.test(url) || /^\/(?!\/)/.test(url)) ? url : undefined
const publisher = (source: Source) => source.author || source.publisher || source.domain || 'Source'

function Plate({ url, label, className = '', alt = '' }: { url?: string | null; label: string; className?: string; alt?: string }) {
  const [failed, setFailed] = useState(false)
  return safeUrl(url) && !failed ? <div className={`riso ${className}`}><div className="plate"><div className="ht"><img src={safeUrl(url)} alt={alt} onError={() => setFailed(true)} /></div></div></div> : <div className={`noimg ${className}`}><span>{label}</span></div>
}

function SourceCard({ source }: { source: Source }) {
  const note = source.extraction_status === 'paywalled' ? 'Paywalled — headline only. The summary does not use this source.' : source.extraction_status === 'deleted' ? 'Post deleted or unavailable' : source.extraction_status === 'failed' ? "Couldn't read this page" : null
  return <div className="story-source-card">
    <Plate url={source.image_url} label={publisher(source)} className="story-source-thumb" />
    <div className="story-source-content"><b className="meta">{publisher(source)}{source.author && ' · on X'}</b><div className="story-source-title">{source.extraction_status === 'failed' ? source.url : source.source_title || source.url}</div>
      <div className="meta story-source-foot"><time dateTime={source.created_at}>{time(source.created_at)}</time>{safeUrl(source.url) && <a href={safeUrl(source.url)} target="_blank" rel="noopener noreferrer">Open{source.author ? ' on X' : ''} ↗</a>}</div>
      {note && <div className="meta story-source-note">{source.extraction_status === 'paywalled' && <span aria-label="Locked">🔒 </span>}{note}</div>}
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
    event.preventDefault(); setReportBusy(true); setError('')
    const { error } = await createClient().functions.invoke('report-story', { body: { story_id: story.id, target, note } })
    setReportBusy(false)
    if (error) { setError('Could not send the report. Your note is kept; try again.'); return }
    reportDialog.current?.close(); setNote(''); setToast('Reported — it will be re-checked'); await refresh()
  }
  const person = (id: string) => details.people.find(person => person.id === id) || { display_name: 'Member', monogram: '?' }
  const timeline = mergeTimeline(details.sources, details.messages)
  const unread = lastViewed ? timeline.filter(entry => entry.kind === 'message' && Date.parse(entry.created_at) > Date.parse(lastViewed) && entry.author_id !== userId) : []
  const firstUnread = unread[0]?.id
  const lead = [...details.sources].reverse().find(source => source.image_url) || details.sources[0]
  const openHistory = () => historyDialog.current?.showModal()
  const openReport = (target: string) => { setTarget(target); reportDialog.current?.showModal() }

  return <main className="story-reader">
    <nav className="story-back"><Link href="/">← Back to the paper</Link></nav>
    <header>
      <Plate url={lead?.image_url} label={lead ? publisher(lead) : 'The paper'} className="story-hero" />
      <div className="story-kicker"><span className="label">{story.category || 'Unfiled'}</span><span className="meta">Updated {time(details.history[0]?.created_at || story.last_activity_at)}</span></div>
      <h1 className="display">{story.title || 'Reading this story'}</h1>
      <p className="summary story-summary">{story.summary || 'Summary failed'}</p>
      {!story.summary && <button className="story-text-button" onClick={() => openReport('summary')}>Retry summary</button>}
      <p className="meta">Summary from {details.sources.filter(source => source.extraction_status === 'ok').length} of {details.sources.length} sources, newest first · <button className="story-text-button" onClick={openHistory}>What changed</button></p>
      <div className="story-clippings" aria-label="Sources, newest first">{details.sources.map(source => <a className="story-clipping" key={source.id} href={`#source-${source.id}`}>
        <Plate url={source.image_url} label={publisher(source)} className="story-clipping-thumb" />
        <div><b className={`meta ${lastViewed && Date.parse(source.created_at) > Date.parse(lastViewed) ? 'new' : ''}`}>{publisher(source)}</b><div className="story-clipping-title">{source.source_title || source.url}</div>{source.extraction_status === 'paywalled' && <small className="meta">🔒 Headline only</small>}</div>
      </a>)}</div>
      <div className="story-correction meta">Something off? <button className="story-text-button" onClick={() => openReport('summary')}>Report the summary</button> · <button className="story-text-button" onClick={() => openReport('category')}>wrong category</button>{story.flagged && <span className="story-flag">Flagged for review</span>}</div>
    </header>
    {error && <p role="alert" className="meta">{error} <button onClick={() => void refresh()}>Try again</button></p>}
    <section aria-label="Story conversation" className="story-conversation">
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
          {entry.id === firstUnread && lastViewed && <div className="story-new-divider">{unread.length} new since {time(lastViewed)}</div>}
          <article id={`${entry.kind}-${entry.id}`} className={`story-entry ${grouped ? 'story-grouped' : ''}`}>
            {!grouped ? <span className="av" aria-hidden="true">{author.monogram}</span> : <span className="story-avatar-space" />}
            <div className="story-entry-body">
              {!grouped && <div className="story-who"><b>{authorId === userId ? 'You' : author.display_name}</b><time className="meta" dateTime={entry.created_at}>{time(entry.created_at)}</time></div>}
              {entry.kind === 'source' ? <>{entry.submitter_comment && <p>{entry.submitter_comment}</p>}<SourceCard source={entry} /></> : <>
                {parent && <div className="story-quote"><b>{person(parent.author_id).display_name}</b> {parent.body ? `${parent.body.slice(0, 100)}${parent.body.length > 100 ? '…' : ''}` : 'Attachment'}</div>}
                {entry.body && <p>{entry.body}</p>}
                {entry.message_media.sort((a, b) => a.position - b.position).map(media => <div key={media.id} className="story-media">
                  {media.kind === 'video' ? <video controls preload="metadata" src={safeUrl(media.display_url)} aria-label={media.alt_text || 'Shared video'} /> : media.kind === 'gif' && reducedMotion && !playingGifs.includes(media.id) ? <button className="story-gif-paused" onClick={() => setPlayingGifs([...playingGifs, media.id])}>Play GIF · {media.alt_text || 'Animated reaction'}</button> : <Plate url={media.display_url} label="Attachment unavailable" alt={media.alt_text || 'Shared image'} className={`story-media-plate ${media.kind === 'gif' ? 'pinkdrum' : ''}`} />}
                  {media.kind === 'gif' && <div className="meta">GIF · {media.alt_text || 'Animated reaction'}{playingGifs.includes(media.id) && <button onClick={() => setPlayingGifs(playingGifs.filter(id => id !== media.id))}>Pause</button>}</div>}
                </div>)}
                <div className="story-message-actions"><div className="story-reactions">{emojis.map(emoji => {
                  const reactions = entry.reactions.filter(reaction => reaction.emoji === emoji)
                  const mine = reactions.some(reaction => reaction.user_id === userId)
                  return <button key={emoji} aria-label={`React ${emoji}${reactions.length ? `, ${reactions.length} reactions` : ''}`} aria-pressed={mine} className={mine ? 'mine' : ''} disabled={Boolean(reactionBusy)} onClick={() => void react(entry, emoji)}>{emoji}{reactions.length > 0 && ` ${reactions.length}`}</button>
                })}</div><button className="story-reply" aria-label={`Reply to ${author.display_name}'s message`} onClick={() => { setReply(entry); document.querySelector<HTMLInputElement>('.story-composer input[aria-label="Message"]')?.focus() }}>↩</button></div>
              </>}
            </div>
          </article>
        </Fragment>
      })}
      {summaryChanged && <p className="meta story-system">Summary updated · <button className="story-text-button" onClick={openHistory}>What changed</button></p>}
    </section>
    <Composer story={story} userId={userId} reply={reply} clearReply={() => setReply(null)} refresh={refresh} gifsEnabled={gifsEnabled} />
    {toast && <div role="status" className="story-toast">{toast}</div>}
    <dialog ref={historyDialog} className="story-dialog" aria-labelledby="story-history-title"><h2 id="story-history-title" className="headline">What changed</h2><button className="story-dialog-close" onClick={() => historyDialog.current?.close()}>Close</button>{details.history.length ? details.history.map(version => <article key={version.id}><time className="meta">{day(version.created_at)} · {time(version.created_at)}</time><h3>{version.title}</h3><p className="summary">{version.summary}</p></article>) : <p>No previous summaries yet.</p>}</dialog>
    <dialog ref={reportDialog} className="story-dialog" aria-labelledby="story-report-title"><h2 id="story-report-title" className="headline">Report a correction</h2><button className="story-dialog-close" onClick={() => reportDialog.current?.close()}>Close</button><form onSubmit={report}><label>What is wrong?<select value={target} onChange={event => setTarget(event.target.value)}><option value="summary">Summary</option><option value="title">Title</option><option value="category">Category</option></select></label><label>Note (optional)<textarea value={note} onChange={event => setNote(event.target.value)} /></label>{error && <p role="alert" className="meta">{error}</p>}<button className="btn" disabled={reportBusy}>{reportBusy ? 'Reporting…' : 'Report'}</button></form></dialog>
  </main>
}
