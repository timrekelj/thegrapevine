import { createServerClient } from '@supabase/ssr'
import { NextRequest, NextResponse } from 'next/server'

const PUBLIC = ['/', '/login', '/invite', '/auth/callback']

export async function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname
  let response = NextResponse.next({ request })
  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!,
    {
      cookies: {
        getAll: () => request.cookies.getAll(),
        setAll(cookiesToSet) {
          cookiesToSet.forEach(({ name, value }) => request.cookies.set(name, value))
          response = NextResponse.next({ request })
          cookiesToSet.forEach(({ name, value, options }) => response.cookies.set(name, value, options))
        },
      },
    },
  )
  // Refreshes an expiring session and verifies the token's signature against the project's cached
  // public keys, so most requests (prefetches included) skip a round trip to the auth server.
  // Pages and actions still check the user themselves; this only decides where to send signed-out visitors.
  const { data } = await supabase.auth.getClaims()
  if (!data?.claims && !PUBLIC.includes(path)) {
    const login = new URL('/login', request.url)
    login.searchParams.set('redirect', path + request.nextUrl.search)
    const redirected = NextResponse.redirect(login)
    response.cookies.getAll().forEach(cookie => redirected.cookies.set(cookie))
    return redirected
  }
  return response
}

// Skipped entirely, because they are public and need no session: build assets, the icons, demo
// pictures, avatars (generated art that reveals nothing about a member), the app-link files Apple
// and Google fetch signed out, the legal notices, and the link-preview images bots fetch signed out.
export const config = {
  matcher: ['/((?!_next/|favicon\\.ico$|icon\\.svg$|seed/|avatar/|\\.well-known/|terms$|privacy$|opengraph-image|invite/opengraph-image).*)'],
}
