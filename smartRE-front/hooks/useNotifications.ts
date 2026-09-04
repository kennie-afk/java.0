'use client'
import { useCallback, useEffect, useRef, useState } from 'react'
import { notificationApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import type { NotificationResponse } from '@/types'

const POLL_MS = 60_000

export function useUnreadCount() {
  const { user } = useAuthStore()
  const [unread, setUnread] = useState(0)
  const [loaded, setLoaded] = useState(false)

  const refresh = useCallback(async () => {
    if (!user) return
    try {
      const { unread } = await notificationApi.unreadCount()
      setUnread(unread)
    } catch {
      setLoaded(true)
    } finally {
      setLoaded(true)
    }
  }, [user])

  useEffect(() => {
    if (!user) return
    refresh()
    const tick = () => { if (document.visibilityState === 'visible') refresh() }
    const id = setInterval(tick, POLL_MS)
    document.addEventListener('visibilitychange', tick)
    return () => { clearInterval(id); document.removeEventListener('visibilitychange', tick) }
  }, [user, refresh])

  return { unread, loaded, refresh, setUnread }
}

export function useNotificationFeed(pageSize = 20) {
  const { user } = useAuthStore()
  const [items, setItems]  = useState<NotificationResponse[]>([])
  const [page, setPage]    = useState(0)
  const [last, setLast]    = useState(true)
  const [loading, setLoad] = useState(true)
  const [more, setMore]    = useState(false)
  const requested = useRef(false)

  const load = useCallback(async (p: number, append: boolean) => {
    if (!user) return
    append ? setMore(true) : setLoad(true)
    try {
      const res = await notificationApi.feed(p, pageSize)
      setItems(prev => append ? [...prev, ...(res.content || [])] : (res.content || []))
      setLast(res.last ?? true)
      setPage(p)
    } catch {
      if (!append) setItems([])
    } finally {
      append ? setMore(false) : setLoad(false)
    }
  }, [user, pageSize])

  useEffect(() => {
    if (!user || requested.current) return
    requested.current = true
    load(0, false)
  }, [user, load])

  const loadMore = useCallback(() => { if (!last && !more) load(page + 1, true) }, [last, more, page, load])

  const markRead = useCallback(async (id: string) => {
    const before = items
    setItems(prev => prev.map(n => n.id === id ? { ...n, read: true, readAt: new Date().toISOString() } : n))
    try {
      await notificationApi.markRead(id)
      return true
    } catch {
      setItems(before)
      return false
    }
  }, [items])

  const markAllRead = useCallback(async () => {
    const before = items
    const now = new Date().toISOString()
    setItems(prev => prev.map(n => n.read ? n : { ...n, read: true, readAt: now }))
    try {
      await notificationApi.markAllRead()
      return true
    } catch {
      setItems(before)
      return false
    }
  }, [items])

  return { items, loading, more, last, loadMore, markRead, markAllRead, reload: () => load(0, false) }
}
