import { createServerClient } from '@supabase/ssr'
import { NextRequest, NextResponse } from 'next/server'

export async function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname
  // Legal notices must remain available without an account or a working auth service.
  if (path === '/terms' || path === '/privacy') return NextResponse.next()
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
  const { data: { user } } = await supabase.auth.getUser()
  const isPublic = ['/login', '/invite', '/favicon.ico', '/auth/callback'].includes(path)
    || path === '/seed' || path.startsWith('/seed/') || path.startsWith('/_next/')
  if (!user && !isPublic) {
    const login = new URL('/login', request.url)
    login.searchParams.set('redirect', path + request.nextUrl.search)
    const redirected = NextResponse.redirect(login)
    response.cookies.getAll().forEach(cookie => redirected.cookies.set(cookie))
    return redirected
  }
  return response
}

// .well-known holds the app-link verification files, which Apple and Google fetch signed out.
export const config = { matcher: ['/((?!_next/|favicon\\.ico$|seed/|\\.well-known/).*)'] }
