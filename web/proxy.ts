import { createServerClient } from '@supabase/ssr'
import { NextRequest, NextResponse } from 'next/server'

export async function proxy(request: NextRequest) {
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
  const path = request.nextUrl.pathname
  const isPublic = ['/login', '/invite', '/favicon.ico'].includes(path)
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

export const config = { matcher: ['/((?!_next/|favicon\\.ico$|seed/).*)'] }
