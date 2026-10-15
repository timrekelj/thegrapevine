'use client'

import { useRef, useState, type FormEvent } from 'react'
import { createClient } from '@/lib/supabase/client'
import { validateAttachments, type Message, type Story } from './queries'

type Gif = { id: string; title: string; url: string; still: string }
export default function Composer({ story, userId, reply, clearReply, refresh, gifsEnabled }: { story: Story; userId: string; reply: Message | null; clearReply: () => void; refresh: () => Promise<void>; gifsEnabled: boolean }) {
  const [body, setBody] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [gif, setGif] = useState<Gif | null>(null)
  const [gifs, setGifs] = useState<Gif[]>([])
  const [searchOpen, setSearchOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [draftLocked, setDraftLocked] = useState(false)
  const [searching, setSearching] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const pending = useRef<{ id: string; uploaded: { kind: string; url: string; alt_text: string; position: number }[] } | null>(null)

  async function search(event: FormEvent) {
    event.preventDefault()
    setSearching(true); setError('')
    try {
      const result = await fetch(`/story/gifs?q=${encodeURIComponent(query)}`)
      const data = await result.json()
      if (!result.ok) throw new Error(data.error)
      setGifs(data.data)
    } catch (error) { setError(error instanceof Error ? error.message : 'GIF search failed.') }
    finally { setSearching(false) }
  }

  async function send(event: FormEvent) {
    event.preventDefault()
    if (busy || (!body.trim() && !files.length && !gif)) return
    const invalid = validateAttachments(files)
    if (invalid) { setError(invalid); return }
    setBusy(true); setError('')
    const client = createClient()
    try {
      if (!pending.current) {
        pending.current = { id: crypto.randomUUID(), uploaded: [] }
        setDraftLocked(true)
      }
      const draft = pending.current
      // shortcut: uploads and message rows are separate writes; add server-side finalization/cleanup before larger groups.
      for (let index = draft.uploaded.length; index < files.length; index++) {
        const file = files[index]
        const path = `${story.group_id}/${userId}/${crypto.randomUUID()}-${file.name.replace(/[^a-zA-Z0-9._-]/g, '_')}`
        const { error } = await client.storage.from('media').upload(path, file)
        if (error) throw error
        draft.uploaded.push({ kind: file.type.startsWith('video/') ? 'video' : 'image', url: path, alt_text: file.name, position: index })
      }
      // A network retry reuses the id, so a successful insert cannot become a duplicate message.
      const existing = await client.from('messages').select('id').eq('id', draft.id).maybeSingle()
      if (existing.error) throw existing.error
      if (!existing.data) {
        const { error } = await client.from('messages').insert({ id: draft.id, story_id: story.id, author_id: userId, body: body.trim() || null, reply_to: reply?.id || null })
        if (error) throw error
      }
      const media = [...draft.uploaded, ...(gif ? [{ kind: 'gif', url: gif.url, alt_text: gif.title, position: files.length }] : [])]
      if (media.length) {
        const existingMedia = await client.from('message_media').select('position').eq('message_id', draft.id)
        if (existingMedia.error) throw existingMedia.error
        const missing = media.filter(item => !existingMedia.data.some(saved => saved.position === item.position))
        if (missing.length) {
          const { error } = await client.from('message_media').insert(missing.map(item => ({ ...item, message_id: draft.id })))
          if (error) throw error
        }
      }
      pending.current = null
      setDraftLocked(false)
      setBody(''); setFiles([]); setGif(null); clearReply()
      if (input.current) input.current.value = ''
      await refresh()
    } catch (error) { setError(`${error instanceof Error ? error.message : 'Message could not be saved.'} Your draft is kept; retry Send.`) }
    finally { setBusy(false) }
  }

  return <div className="story-composer">
    {error && <p role="alert" className="meta">{error}</p>}
    {reply && <div className="meta">Replying to: {reply.body?.slice(0, 80) || 'Attachment'} <button type="button" onClick={clearReply} disabled={busy || draftLocked}>Cancel</button></div>}
    {(files.length > 0 || gif) && <div className="meta">{files.length > 0 && `${files.length} attachment${files.length === 1 ? '' : 's'}`} {gif?.title} <button onClick={() => { setFiles([]); setGif(null); if (input.current) input.current.value = '' }} disabled={busy || draftLocked}>Remove</button></div>}
    {searchOpen && <section className="story-gif-search" aria-label="GIPHY search">
      <form onSubmit={search}><input aria-label="Search GIPHY" value={query} onChange={event => setQuery(event.target.value)} /><button disabled={searching || !query.trim()}>Search</button><button type="button" onClick={() => setSearchOpen(false)}>Close</button></form>
      <div className="story-gif-results">{gifs.map(item => <button key={item.id} aria-label={item.title} onClick={() => { setGif(item); setSearchOpen(false) }}><img src={item.still} alt={item.title} /></button>)}</div>
      <small>Powered by GIPHY</small>
    </section>}
    <form onSubmit={send} className="story-composer-row">
      <input ref={input} type="file" accept="image/*,video/*" multiple hidden onChange={event => { const selected = Array.from(event.target.files || []); const invalid = validateAttachments(selected); setError(invalid || ''); if (!invalid) setFiles(selected) }} />
      <button type="button" aria-label="Attach images or video" disabled={busy || draftLocked} onClick={() => input.current?.click()}>＋</button>
      <input aria-label="Message" placeholder="Message" value={body} disabled={busy || draftLocked} onChange={event => setBody(event.target.value)} />
      {gifsEnabled && <button type="button" disabled={busy || draftLocked} onClick={() => setSearchOpen(!searchOpen)}>GIF</button>}
      <button className="btn" disabled={busy || (!body.trim() && !files.length && !gif)}>{busy ? 'Sending…' : 'Send'}</button>
    </form>
  </div>
}
