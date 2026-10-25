'use client'

import { useEffect, useRef, useState } from 'react'
import { useFormStatus } from 'react-dom'
import { BusyLabel } from '@/components/print/InkDots'
import styles from './login.module.css'

// Must match `otp_length` in supabase/config.toml and the hosted project.
const CODE_LENGTH = 6

/** One real input (so paste, autofill and screen readers just work), drawn as six cells. Submits its form when full. */
export function CodeInput({ invalid, describedBy }: { invalid: boolean; describedBy?: string }) {
  const input = useRef<HTMLInputElement>(null)
  const [code, setCode] = useState('')
  const [focused, setFocused] = useState(true)
  const { pending } = useFormStatus()

  // A code works once: a second submit of the same code is rejected even though the first signed in.
  useEffect(() => {
    if (code.length === CODE_LENGTH && !pending) input.current?.form?.requestSubmit()
  }, [code]) // eslint-disable-line react-hooks/exhaustive-deps -- submit when the code fills, not when pending changes

  return <div className={styles.code} data-invalid={invalid}>
    <input ref={input} id="code" name="code" value={code} autoFocus required readOnly={pending}
      inputMode="numeric" autoComplete="one-time-code" pattern={`[0-9]{${CODE_LENGTH}}`} maxLength={CODE_LENGTH}
      aria-label="Sign-in code" aria-invalid={invalid} aria-describedby={describedBy}
      onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, CODE_LENGTH))}
      onFocus={() => setFocused(true)} onBlur={() => setFocused(false)} />
    <div className={styles.cells} aria-hidden="true">
      {Array.from({ length: CODE_LENGTH }, (_, i) =>
        <span key={i} className={styles.cell} data-active={focused && i === Math.min(code.length, CODE_LENGTH - 1)}>{code[i]}</span>)}
    </div>
  </div>
}

/** Disabled while the code is being checked, which also blocks Enter from submitting the form again. */
export function SignInButton({ className }: { className: string }) {
  const { pending } = useFormStatus()
  return <button className={className} disabled={pending} aria-busy={pending}><BusyLabel busy={pending} label="Signing in…">Sign in</BusyLabel></button>
}

/** A new code can be requested once the countdown from the last send runs out. */
export function ResendButton({ seconds }: { seconds: number }) {
  const [left, setLeft] = useState(seconds)
  const { pending } = useFormStatus()
  useEffect(() => {
    if (left <= 0) return
    const timer = setTimeout(() => setLeft(left - 1), 1000)
    return () => clearTimeout(timer)
  }, [left])
  return <button className={`${styles.quiet} tnum`} disabled={left > 0 || pending} aria-busy={pending}>
    <BusyLabel busy={pending} label="Sending a new code…">{left > 0 ? `Send a new code in 0:${String(left).padStart(2, '0')}` : 'Send a new code'}</BusyLabel>
  </button>
}
