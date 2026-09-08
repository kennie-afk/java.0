'use client'
import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Bell, CheckCheck, Settings } from 'lucide-react'
import { useUnreadCount, useNotificationFeed } from '@/hooks/useNotifications'
import { cn, fmt } from '@/lib/utils'
import { Spinner } from '@/components/ui/Modal'
import type { NotificationResponse } from '@/types'

const MAX_IN_DROPDOWN = 6

export default function NotificationBell() {
  const [open, setOpen] = useState(false)
  const { unread, refresh, setUnread } = useUnreadCount()
  const { items, loading, markRead, markAllRead, reload } = useNotificationFeed(MAX_IN_DROPDOWN)
  const router = useRouter()

  const toggle = () => {
    const next = !open
    setOpen(next)
    if (next) { reload(); refresh() }
  }

  const onItemClick = async (n: NotificationResponse) => {
    setOpen(false)
    if (!n.read) {
      setUnread(u => Math.max(0, u - 1))
      await markRead(n.id)
    }
    if (n.actionUrl) router.push(n.actionUrl)
  }

  const onMarkAll = async () => {
    setUnread(0)
    const ok = await markAllRead()
    if (!ok) refresh()
  }

  const shown = items.slice(0, MAX_IN_DROPDOWN)

  return (
    <div className="relative">
      <button
        onClick={toggle}
        className="btn-ghost !h-9 !w-9 !px-0 relative"
        title="Notifications"
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}>
        <Bell size={17}/>
        {unread > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 rounded-full bg-red-500 text-white text-2xs font-bold leading-4 text-center tabular-nums">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)}/>
          <div className="absolute right-0 top-[calc(100%+8px)] w-[340px] sm:w-[380px] card z-50 py-0 animate-fade-in overflow-hidden">
            <div className="flex items-center justify-between px-4 py-2.5 border-b border-base">
              <p className="text-base font-semibold text-gray-900 dark:text-white">
                Notifications{unread > 0 && <span className="text-muted font-normal"> · {unread} unread</span>}
              </p>
              {unread > 0 && (
                <button onClick={onMarkAll}
                  className="flex items-center gap-1 text-xs text-gold-600 dark:text-gold-400 hover:underline">
                  <CheckCheck size={12}/>Mark all read
                </button>
              )}
            </div>

            <div className="max-h-[380px] overflow-y-auto">
              {loading ? (
                <div className="flex items-center justify-center py-10"><Spinner size={22}/></div>
              ) : shown.length === 0 ? (
                <div className="px-4 py-10 text-center">
                  <Bell size={22} className="mx-auto text-gray-300 dark:text-gray-600 mb-2"/>
                  <p className="text-base text-muted">Nothing yet</p>
                  <p className="text-xs text-muted mt-1">Updates about your listings, payments and verification land here.</p>
                </div>
              ) : shown.map(n => (
                <button key={n.id} onClick={() => onItemClick(n)}
                  className={cn(
                    'w-full text-left px-3 py-2 border-b border-base last:border-b-0 transition-colors',
                    'hover:bg-gray-50 dark:hover:bg-[#1A1A35]',
                    !n.read && 'bg-gold-50/50 dark:bg-gold-500/[0.06]')}>
                  <div className="flex items-start gap-2.5">
                    <span className={cn('mt-1.5 w-1.5 h-1.5 rounded-full shrink-0',
                      n.read ? 'bg-transparent' : 'bg-gold-500')}/>
                    <div className="min-w-0 flex-1">
                      <p className={cn('text-base leading-snug truncate',
                        n.read ? 'text-gray-600 dark:text-gray-300' : 'font-semibold text-gray-900 dark:text-white')}>
                        {n.subject || n.templateCode}
                      </p>
                      {n.body && <p className="text-xs text-muted mt-0.5 line-clamp-2">{n.body}</p>}
                      <p className="text-2xs text-muted mt-1">{fmt.ago(n.createdAt)}</p>
                    </div>
                  </div>
                </button>
              ))}
            </div>

            <div className="flex items-center justify-between px-4 py-2 border-t border-base bg-gray-50/60 dark:bg-white/[0.02]">
              <Link href="/notifications" onClick={() => setOpen(false)}
                className="text-sm font-medium text-gold-600 dark:text-gold-400 hover:underline">
                View all
              </Link>
              <Link href="/notifications#preferences" onClick={() => setOpen(false)}
                className="flex items-center gap-1 text-xs text-muted hover:text-gray-700 dark:hover:text-gray-200">
                <Settings size={12}/>Preferences
              </Link>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
