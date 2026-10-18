'use client'

import { useEffect } from 'react'
import { usePathname } from 'next/navigation'
import posthog from 'posthog-js'
import { createClient } from '@/lib/supabase/client'
import { telemetryEnabled } from '@/lib/telemetry-config'
import { capture } from '@/lib/telemetry'

export default function Telemetry() {
  const pathname = usePathname()
  useEffect(() => {
    if (!telemetryEnabled) return
    const { data: { subscription } } = createClient().auth.onAuthStateChange((_event, session) => {
      if (session) posthog.identify(session.user.id.toLowerCase())
      else if (posthog._isIdentified()) posthog.reset()
    })
    return () => subscription.unsubscribe()
  }, [])
  useEffect(() => {
    capture('screen_viewed', { screen: pathname.startsWith('/story/') ? 'story' : pathname })
  }, [pathname])
  return null
}
