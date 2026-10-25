'use client'

import { useEffect, useRef, useState, type ClipboardEvent, type DragEvent, type FormEvent } from 'react'
import Link from '@/components/print/PressLink'
import { BusyLabel } from '@/components/print/InkDots'
import { createClient } from '@/lib/supabase/client'
import { capture } from '@/lib/telemetry'
import { describeBrowser, MAX_SCREENSHOTS, REPORT_KINDS, SCREENSHOT_TYPES, screenshotProblem, type ReportKind } from '@/lib/report'
import styles from './report.module.css'

type Shot = { id: string; file: File; preview: string; path?: string }
type Filed = { issue_number: number | null; issue_url: string | null }

async function failure(error: unknown) {
  const context = error && typeof error === 'object' && 'context' in error ? error.context : null
  if (context instanceof Response) {
    const body = await context.json().catch(() => null) as { error?: unknown } | null
    if (typeof body?.error === 'string' && body.error.length <= 240) return body.error
  }
  return error instanceof Error && error.message ? error.message : 'The report could not be sent. Your words are still here.'
}

export default function ReportForm({ userId, initialKind, error: seenError, from }: {
  userId: string; initialKind: ReportKind; error?: string; from?: string
}) {
  const [kind, setKind] = useState<ReportKind>(initialKind)
  const [description, setDescription] = useState('')
  const [shots, setShots] = useState<Shot[]>([])
  const [includeError, setIncludeError] = useState(!!seenError)
  const [dragging, setDragging] = useState(false)
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState('')
  const [filed, setFiled] = useState<Filed | null>(null)
  const picker = useRef<HTMLInputElement>(null)
  const latest = useRef(shots)
  useEffect(() => { latest.current = shots }, [shots])
  useEffect(() => () => latest.current.forEach(shot => URL.revokeObjectURL(shot.preview)), [])

  function attach(files: Iterable<File>) {
    const added: Shot[] = []
    let rejected = ''
    for (const file of files) {
      const why = screenshotProblem(file)
      if (why) { rejected = why; continue }
      if (shots.length + added.length >= MAX_SCREENSHOTS) { rejected = `Attach up to ${MAX_SCREENSHOTS} screenshots.`; break }
      added.push({ id: crypto.randomUUID(), file, preview: URL.createObjectURL(file) })
    }
    setProblem(rejected)
    if (added.length) setShots(current => [...current, ...added])
  }

  function remove(id: string) {
    setShots(current => current.filter(shot => {
      if (shot.id === id) URL.revokeObjectURL(shot.preview)
      return shot.id !== id
    }))
  }

  function paste(event: ClipboardEvent) {
    const images = [...event.clipboardData.files].filter(file => file.type.startsWith('image/'))
    if (!images.length) return
    event.preventDefault()
    attach(images)
  }

  function drop(event: DragEvent) {
    event.preventDefault()
    setDragging(false)
    attach(event.dataTransfer.files)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    if (!description.trim()) { setProblem('Describe what you found or what you would like.'); return }
    setBusy(true); setProblem('')
    const client = createClient()
    try {
      // Each screenshot uploads once: a retry after a failed send reuses the paths already stored.
      const uploaded = [...shots]
      for (const [index, shot] of uploaded.entries()) {
        if (shot.path) continue
        const path = `${userId}/${crypto.randomUUID()}.${SCREENSHOT_TYPES[shot.file.type]}`
        const { error } = await client.storage.from('feedback').upload(path, shot.file, { contentType: shot.file.type })
        if (error) throw new Error('A screenshot could not be uploaded. Try again, or remove it.')
        uploaded[index] = { ...shot, path }
        setShots(current => current.map(item => item.id === shot.id ? { ...item, path } : item))
      }
      const { browser, os } = describeBrowser(navigator.userAgent)
      const { data, error } = await client.functions.invoke<Filed>('submit-feedback', {
        body: {
          kind, description: description.trim(), images: uploaded.map(shot => shot.path),
          context: {
            platform: 'web', os, device: `${browser}, ${innerWidth}×${innerHeight}`,
            screen: from, error: includeError ? seenError : undefined,
          },
        },
      })
      if (error) throw error
      capture('feedback_submitted', { kind, screenshots: uploaded.length })
      setFiled(data ?? { issue_number: null, issue_url: null })
    } catch (error) {
      setProblem(await failure(error))
    } finally { setBusy(false) }
  }

  function another() {
    shots.forEach(shot => URL.revokeObjectURL(shot.preview))
    setShots([]); setDescription(''); setIncludeError(false); setFiled(null)
  }

  if (filed) return <section className={styles.done} role="status" aria-live="polite">
    <p className="label">Received</p>
    <h1 className="headline">Thank you.</h1>
    <p className="summary">{filed.issue_url
      ? <>Your report is filed as <a href={filed.issue_url} target="_blank" rel="noreferrer">issue #{filed.issue_number}</a>. You can follow it there.</>
      : <>We have your report and will look into it.</>}</p>
    <div className={styles.actions}>
      <Link className={styles.primary} href={from && !from.startsWith('/report') ? from : '/'}>Back to the paper</Link>
      <button type="button" className={styles.quiet} onClick={another}>Send another</button>
    </div>
  </section>

  const hint = REPORT_KINDS.find(option => option.value === kind)!.hint
  return <form className={styles.form} onSubmit={submit} onPaste={paste} noValidate>
    <fieldset className={styles.kinds}>
      <legend className="label">What is it?</legend>
      {REPORT_KINDS.map(option => <label key={option.value} className={styles.kind}>
        <input type="radio" name="kind" value={option.value} checked={kind === option.value} onChange={() => setKind(option.value)} />
        <span>{option.label}</span>
      </label>)}
    </fieldset>

    <label htmlFor="description" className="label">Describe it</label>
    <textarea id="description" className={styles.field} value={description} onChange={event => setDescription(event.target.value)}
      placeholder={hint} maxLength={4000} rows={7} required aria-describedby="report-note" aria-invalid={!!problem && !description.trim()} />

    {seenError && <label className={styles.check}>
      <input type="checkbox" checked={includeError} onChange={event => setIncludeError(event.target.checked)} />
      <span>Include the error you saw: <q>{seenError}</q></span>
    </label>}

    <div className={`${styles.drop} ${dragging ? styles.dragging : ''}`}
      onDragOver={event => { event.preventDefault(); setDragging(true) }} onDragLeave={() => setDragging(false)} onDrop={drop}>
      {shots.length > 0 && <ul className={styles.shots} aria-label="Screenshots">
        {shots.map((shot, index) => <li key={shot.id}>
          {/* eslint-disable-next-line @next/next/no-img-element -- a local object URL, not an optimizable asset */}
          <img src={shot.preview} alt={`Screenshot ${index + 1}`} />
          <button type="button" onClick={() => remove(shot.id)} disabled={busy} aria-label={`Remove screenshot ${index + 1}`}>×</button>
        </li>)}
      </ul>}
      {shots.length < MAX_SCREENSHOTS && <p className="meta">
        <button type="button" className={styles.textButton} onClick={() => picker.current?.click()} disabled={busy}>Add screenshots</button>
        {' '}or drop or paste them here · up to {MAX_SCREENSHOTS}
      </p>}
      <input ref={picker} type="file" accept={Object.keys(SCREENSHOT_TYPES).join(',')} multiple hidden
        onChange={event => { attach(event.target.files ?? []); event.target.value = '' }} />
    </div>

    <p id="report-note" className="meta">Reports are posted publicly on GitHub so we can track them. Leave out anything private.</p>
    {problem && <p role="alert" className={styles.error}>{problem}</p>}
    <button className={styles.primary} disabled={busy} aria-busy={busy}><BusyLabel busy={busy} label="Sending…">Send report</BusyLabel></button>
  </form>
}
