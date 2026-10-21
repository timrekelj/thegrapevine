'use client'

import { useEffect, useRef, useState } from 'react'
import styles from './login.module.css'

// Must match `otp_length` in supabase/config.toml and the hosted project.
export const CODE_LENGTH = 6

/** One real input (so paste, autofill and screen readers just work), drawn as six cells. Submits its form when full. */
export function CodeInput({ invalid, describedBy }: { invalid: boolean; describedBy?: string }) {
  const input = useRef<HTMLInputElement>(null)
  const [code, setCode] = useState('')
  const [focused, setFocused] = useState(true)

  useEffect(() => {
    if (code.length === CODE_LENGTH) input.current?.form?.requestSubmit()
  }, [code])

  return <div className={styles.code} data-invalid={invalid}>
    <input ref={input} id="code" name="code" value={code} autoFocus required
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

/** A new code can be requested once the countdown from the last send runs out. */
export function ResendButton({ seconds }: { seconds: number }) {
  const [left, setLeft] = useState(seconds)
  useEffect(() => {
    if (left <= 0) return
    const timer = setTimeout(() => setLeft(left - 1), 1000)
    return () => clearTimeout(timer)
  }, [left])
  return <button className={`${styles.quiet} tnum`} disabled={left > 0}>
    {left > 0 ? `Send a new code in 0:${String(left).padStart(2, '0')}` : 'Send a new code'}
  </button>
}
