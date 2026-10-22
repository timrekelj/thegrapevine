'use client'

import { useState } from 'react'
import form from '../login/login.module.css'
import styles from './onboarding.module.css'

type Action = (formData: FormData) => Promise<void>

/** The paper being made, updated as you type: masthead, monogram and a first-edition stamp. */
function Preview({ name, paper, placeholder, today }: { name: string; paper: string; placeholder: string; today: string }) {
  const initial = Array.from(name.trim())[0]?.toUpperCase() ?? '?'
  return <div className={styles.sheet} aria-hidden="true">
    <span className={`stamp ${styles.stamp}`}>First edition</span>
    <div className={styles.sheetTop}><span className="label">{today}</span><span className="av" key={initial}>{initial}</span></div>
    <p className={styles.masthead} data-empty={!paper.trim()}>{paper.trim() || placeholder}</p>
    <div className={styles.rules} />
    <div className={styles.lead}>
      <div className={`typeplate ${styles.plate}`}><span>Your first link</span></div>
      <div>
        <p className={styles.headline}>Share a link and it becomes the lead story.</p>
        <div className="meta">{name.trim() || 'You'} · 1 member</div>
      </div>
    </div>
  </div>
}

export function OnboardingForm({ id, initialName, initialPaper, initialCode, inviteFirst, today, error, startPaper, joinPaper }: {
  id: string
  initialName: string
  initialPaper: string
  initialCode: string
  inviteFirst: boolean
  today: string
  error: string
  startPaper: Action
  joinPaper: Action
}) {
  const [name, setName] = useState(initialName)
  const [paper, setPaper] = useState(initialPaper)
  const [code, setCode] = useState(initialCode)
  const [joining, setJoining] = useState(inviteFirst)
  const ready = name.trim().length > 0
  const errorMessage = error && <p id="onboarding-error" role="alert" className={form.error}>{error}</p>

  return <>
    <Preview name={name} paper={joining ? '' : paper} placeholder={joining ? 'Your friends’ paper' : 'Your paper'} today={today} />
    <form className={form.form}>
      <input type="hidden" name="id" value={id} />
      <div className={styles.fields}>
        <label htmlFor="name" className="label">Your name</label>
        <input id="name" name="name" value={name} onChange={e => setName(e.target.value)} maxLength={80} autoComplete="name"
          placeholder="What friends call you" className={form.field} />
        {!joining && <>
          <label htmlFor="paper" className="label">Name your paper</label>
          <input id="paper" name="paper" value={paper} onChange={e => setPaper(e.target.value)} maxLength={100}
            placeholder="The Sunday Club" className={form.field} aria-describedby={error ? 'onboarding-error' : undefined} />
          {errorMessage}
          <button className={form.primary} formAction={startPaper} disabled={!ready || !paper.trim()}>Start the paper</button>
        </>}
      </div>
      {joining ? <div className={styles.join}>
        <label htmlFor="code" className="label">Invite code</label>
        <input id="code" name="code" value={code} onChange={e => setCode(e.target.value)} maxLength={100} autoCapitalize="none" autoComplete="off"
          placeholder="From your friend’s invite" className={form.field} aria-describedby={error ? 'onboarding-error' : undefined} />
        {errorMessage}
        <button className={form.primary} formAction={joinPaper} disabled={!ready || !code.trim()}>Join your friends</button>
        <button type="button" className={form.quiet} onClick={() => setJoining(false)}>Start a new paper instead</button>
      </div> : <div className={styles.join}>
        <button type="button" className={form.quiet} onClick={() => setJoining(true)}>Have an invite code? Join your friends</button>
      </div>}
    </form>
  </>
}
