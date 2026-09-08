'use client'
import { useEffect } from 'react'
import Link from 'next/link'
import { AlertTriangle, RotateCcw, Home } from 'lucide-react'
import { reportError } from '@/lib/errorLogger'

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    reportError(error, 'react-error-boundary', { boundary: 'root' })
  }, [error])

  const isDev = process.env.NODE_ENV === 'development'

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4 text-center bg-surface">
      <div className="w-14 h-14 rounded-lg bg-red-50 dark:bg-red-500/10 flex items-center justify-center mb-4">
        <AlertTriangle size={26} className="text-red-500"/>
      </div>
      <h1 className="font-display text-xl font-semibold text-gray-900 dark:text-white mb-2">Something went wrong</h1>
      <p className="text-sm text-muted max-w-sm mb-4">
        This wasn&apos;t supposed to happen. Try again, or head back home. Your account and listings are safe either way.
      </p>
      {isDev && (
        <div className="text-left max-w-xl w-full mb-4 p-2.5 rounded-lg bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 overflow-auto">
          <p className="text-sm font-mono font-semibold text-red-700 dark:text-red-400">{error.name}: {error.message}</p>
          {error.stack && <pre className="text-2xs font-mono text-red-600 dark:text-red-400 mt-2 whitespace-pre-wrap">{error.stack}</pre>}
        </div>
      )}
      <div className="flex gap-3">
        <Link href="/" className="btn-secondary"><Home size={15}/>Go home</Link>
        <button onClick={reset} className="btn-primary"><RotateCcw size={15}/>Try again</button>
      </div>
    </div>
  )
}
