'use client'

import type { ComponentProps } from 'react'
import { useFormStatus } from 'react-dom'
import { BusyLabel } from './InkDots'

/**
 * A submit button for a server-action form. While the form is out it is disabled, so a second
 * submit cannot race the first, and the button that sent it prints ink dots over its label.
 * Several buttons may share a form (by `name`/`value`): only the one pressed shows the dots.
 */
export default function SubmitButton({ pending: pendingLabel = 'Working…', disabled, children, ...button }: ComponentProps<'button'> & { pending?: string }) {
  const { pending, data } = useFormStatus()
  const sent = pending && (button.name ? data?.get(button.name) === button.value : true)
  return <button {...button} disabled={disabled || pending} aria-busy={sent}>
    <BusyLabel busy={sent} label={pendingLabel}>{children}</BusyLabel>
  </button>
}
