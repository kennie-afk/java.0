'use client'
import { useEffect, useRef } from 'react'
import { useAuthStore } from '@/lib/store'
import { validateSession } from '@/lib/api'

export default function SessionValidator() {
  const user = useAuthStore(s => s.user)
  const hasHydrated = useAuthStore(s => s.hasHydrated)
  const logout = useAuthStore(s => s.logout)
  const checked = useRef(false)

  useEffect(() => {
    if (!hasHydrated || !user || checked.current) return
    checked.current = true
    validateSession().catch(err => {
      if (err.response?.status === 401 || err.response?.status === 403) logout()
    })
  }, [hasHydrated, user, logout])

  return null
}
