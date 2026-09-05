'use client'
import { useEffect } from 'react'
import { installGlobalErrorTracking } from '@/lib/errorLogger'

export default function ErrorTracking() {
  useEffect(() => {
    installGlobalErrorTracking()
  }, [])
  return null
}
