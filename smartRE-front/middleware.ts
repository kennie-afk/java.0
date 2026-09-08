import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'
import { jwtVerify } from 'jose'


export const PROTECTED_PREFIXES = ['/dashboard', '/listings', '/properties/new', '/verification', '/ownership', '/viewings', '/payments', '/reviews', '/profile', '/overview', '/revenue', '/users', '/verification-queue', '/manage-listings', '/reports', ]
export const ADMIN_PREFIXES = ['/overview', '/revenue', '/users', '/verification-queue', '/manage-listings', '/reports', ]

export interface AuthClaims {
  exp?: number
  role?: string
}

export interface ResolvedAuth {
  claims: AuthClaims | null
  verified: boolean
}

export function decodeUnverified(token: string): AuthClaims | null {
  try {
    const payload = token.split('.')[1]
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    return null
  }
}

export function secretFromEnv(): Uint8Array | null {
  const raw = process.env.JWT_SECRET
  return raw ? new TextEncoder().encode(raw) : null
}

// Whether an unverified decode is allowed to stand in for a real signature check.
// Only in development, where running without a shared secret is a convenience. In
// production a missing secret is a deployment fault, and the safe reading of a token
// we cannot verify is "no session" — not "whatever the token claims about itself".
export function allowsUnverifiedFallback(): boolean {
  return process.env.NODE_ENV !== 'production'
}

export async function resolveAuth(
  token: string | undefined,
  secret: Uint8Array | null = secretFromEnv(),
  allowUnverified: boolean = allowsUnverifiedFallback()
): Promise<ResolvedAuth> {
  if (!token) return { claims: null, verified: !!secret }

  if (secret) {
    try {
      const { payload } = await jwtVerify(token, secret)
      return { claims: payload as AuthClaims, verified: true }
    } catch {
      return { claims: null, verified: true }
    }
  }

  if (!allowUnverified) return { claims: null, verified: false }

  return { claims: decodeUnverified(token), verified: false }
}

export function isExpired(claims: AuthClaims | null): boolean {
  return !claims || !claims.exp || Date.now() >= claims.exp * 1000
}

function matchesPrefix(path: string, prefixes: string[]): boolean {
  return prefixes.some(p => path === p || path.startsWith(p + '/'))
}

export async function middleware(req: NextRequest) {
  const path = req.nextUrl.pathname
  if (!matchesPrefix(path, PROTECTED_PREFIXES)) return NextResponse.next()

  const token = req.cookies.get('sre_token')?.value
  const { claims } = await resolveAuth(token)

  if (!token || isExpired(claims)) {
    const url = req.nextUrl.clone()
    url.pathname = '/login'
    url.searchParams.set('returnTo', path)
    return NextResponse.redirect(url)
  }

  if (matchesPrefix(path, ADMIN_PREFIXES) && claims?.role !== 'ADMIN') {
    const url = req.nextUrl.clone()
    url.pathname = '/'
    return NextResponse.redirect(url)
  }

  return NextResponse.next()
}

export const config = {
  matcher: ['/dashboard/:path*', '/listings/:path*', '/properties/new', '/verification/:path*', '/ownership/:path*', '/viewings/:path*', '/payments/:path*', '/reviews/:path*', '/profile/:path*', '/overview/:path*', '/revenue/:path*', '/users/:path*', '/verification-queue/:path*', '/manage-listings/:path*', '/reports/:path*'],
}
