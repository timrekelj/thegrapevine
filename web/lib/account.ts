/** Case-insensitive match against the address stored on the account. Blank never matches. */
export function sameEmail(account: string | null | undefined, typed: string | null | undefined) {
  const expected = account?.trim().toLowerCase() ?? ''
  const given = typed?.trim().toLowerCase() ?? ''
  return expected.length > 0 && expected === given
}

/**
 * What to tell someone about an in-progress or finished email change.
 * Secure email change (on in this project) confirms both the current and the new address.
 */
export function emailChangeNotice(notice: string, email?: string | null, pending?: string | null) {
  const current = email?.trim() || ''
  const next = pending?.trim() || ''
  if (next && current && next.toLowerCase() !== current.toLowerCase()) {
    const lead = notice === 'email' ? 'One confirmation arrived. ' : ''
    return `${lead}Open the links sent to ${current} and to ${next}, both in this browser. This account keeps using ${current} until both are confirmed.`
  }
  if (next) return `Open the confirmation link sent to ${next} in this browser. This account uses it only after that.`
  if (notice === 'email' && current) return `Your email is confirmed. Sign-in codes will come to ${current}.`
  if (notice === 'sent') return 'Check your inbox for a confirmation link, and open it in this browser.'
  return ''
}
