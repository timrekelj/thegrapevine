'use client'

import { useEffect } from 'react'
import { usePathname } from 'next/navigation'
import { telemetryEnabled } from '@/lib/telemetry-config'
import { capture, withPosthog } from '@/lib/telemetry'

export default function Telemetry() {
  const pathname = usePathname()
  useEffect(() => {
    if (!telemetryEnabled) return
    let active = true
    let unsubscribe = () => {}
    // Loaded here rather than imported: the landing, sign-in and legal pages don't otherwise need the Supabase client.
    void import('@/lib/supabase/client').then(({ createClient }) => {
      if (!active) return
      const { data: { subscription } } = createClient().auth.onAuthStateChange((_event, session) => withPosthog(posthog => {
        if (session) posthog.identify(session.user.id.toLowerCase())
        else if (posthog._isIdentified()) posthog.reset()
      }))
      unsubscribe = () => subscription.unsubscribe()
    })
    return () => { active = false; unsubscribe() }
  }, [])
  useEffect(() => {
    capture('screen_viewed', { screen: pathname.startsWith('/story/') ? 'story' : pathname })
  }, [pathname])
  return null
}
