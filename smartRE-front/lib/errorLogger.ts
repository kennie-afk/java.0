
export type ErrorSource = 'window.onerror' | 'unhandledrejection' | 'react-error-boundary' | 'manual'

export interface ReportedError {
  message: string
  stack?: string
  source: ErrorSource
  digest?: string
  url?: string
  userAgent?: string
  timestamp: string
  extra?: Record<string, unknown>
}

function toReportedError(error: unknown, source: ErrorSource, extra?: Record<string, unknown>): ReportedError {
  const err = error instanceof Error ? error : new Error(typeof error === 'string' ? error : JSON.stringify(error))
  return {
    message: err.message,
    stack: err.stack,
    source,
    digest: (err as Error & { digest?: string }).digest,
    url: typeof window !== 'undefined' ? window.location.href : undefined,
    userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : undefined,
    timestamp: new Date().toISOString(),
    extra,
  }
}

function sink(event: ReportedError) {
  // eslint-disable-next-line no-console
  console.error(`[${event.source}]`, event.message, event)
}

export function reportError(error: unknown, source: ErrorSource = 'manual', extra?: Record<string, unknown>) {
  try {
    sink(toReportedError(error, source, extra))
  } catch {
  }
}

let installed = false

export function installGlobalErrorTracking() {
  if (installed || typeof window === 'undefined') return
  installed = true

  window.addEventListener('error', event => {
    reportError(event.error ?? event.message, 'window.onerror', {
      filename: event.filename,
      lineno: event.lineno,
      colno: event.colno,
    })
  })

  window.addEventListener('unhandledrejection', event => {
    reportError(event.reason, 'unhandledrejection')
  })
}
