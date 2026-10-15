import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { safeRedirect } from '@/lib/auth'

export async function GET(request: NextRequest) {
  const params = request.nextUrl.searchParams
  const invite = params.get('invite')
  const destination = safeRedirect(params.get('redirect') || (invite ? `/invite?${new URLSearchParams({ code: invite })}` : '/'))
  const code = params.get('code')
  const supabase = await createClient()
  if (code) {
    const { data, error } = await supabase.auth.exchangeCodeForSession(code)
    if (!error && data.user) {
      const { data: memberships, error: membershipError } = await supabase.from('group_members').select('group_id').eq('user_id', data.user.id).limit(1)
      if (!membershipError) {
        const path = memberships?.length ? destination : `/onboarding?${new URLSearchParams({ redirect: destination })}`
        return NextResponse.redirect(new URL(path, request.url))
      }
    }
  }
  const query = new URLSearchParams({ redirect: destination, error: 'This sign-in link could not be used. Please request a fresh link.' })
  return NextResponse.redirect(new URL(`/login?${query}`, request.url))
}
