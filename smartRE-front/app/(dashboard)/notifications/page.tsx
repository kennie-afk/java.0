'use client'
import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Bell, CheckCheck, Mail, MessageSquare, Monitor, Inbox } from 'lucide-react'
import { notificationApi } from '@/lib/api'
import { useNotificationFeed, useUnreadCount } from '@/hooks/useNotifications'
import type { NotificationPreferenceResponse, NotificationResponse } from '@/types'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { EmptyState, PageLoader, Spinner } from '@/components/ui/Modal'
import { cn, fmt } from '@/lib/utils'
import toast from 'react-hot-toast'

const CATEGORY_LABEL: Record<string,string> = {
  VERIFICATION:'Verification', PAYMENT:'Payments', VIEWING:'Viewings',
  PROPERTY:'Listings', TENANCY:'Tenancy', MAINTENANCE:'Maintenance', ACCOUNT:'Account',
}

const CATEGORY_BLURB: Record<string,string> = {
  VERIFICATION:'Identity and land-title decisions',
  PAYMENT:'Receipts, escrow and payouts',
  VIEWING:'Viewing requests and reminders',
  PROPERTY:'Changes to your listings',
  TENANCY:'Rent, leases and statements',
  MAINTENANCE:'Repair requests and updates',
}

const CATEGORY_VARIANT: Record<string,'success'|'warning'|'info'|'gold'|'purple'|'muted'> = {
  VERIFICATION:'success', PAYMENT:'gold', VIEWING:'info',
  PROPERTY:'purple', TENANCY:'info', MAINTENANCE:'warning', ACCOUNT:'muted',
}

export default function NotificationsPage() {
  const { items, loading, more, last, loadMore, markRead, markAllRead } = useNotificationFeed(20)
  const { unread, refresh, setUnread } = useUnreadCount()
  const [prefs, setPrefs] = useState<NotificationPreferenceResponse[]>([])
  const [prefsLoading, setPrefsLoading] = useState(true)
  const [saving, setSaving] = useState<string|null>(null)
  const router = useRouter()

  useEffect(() => {
    notificationApi.preferences()
      .then(setPrefs)
      .catch(() => setPrefs([]))
      .finally(() => setPrefsLoading(false))
  }, [])

  useEffect(() => {
    if (window.location.hash === '#preferences') {
      document.getElementById('preferences')?.scrollIntoView({ behavior:'smooth' })
    }
  }, [prefsLoading])

  const openNotification = async (n: NotificationResponse) => {
    if (!n.read) {
      setUnread(u => Math.max(0, u - 1))
      await markRead(n.id)
    }
    if (n.actionUrl) router.push(n.actionUrl)
  }

  const onMarkAll = async () => {
    setUnread(0)
    const ok = await markAllRead()
    if (ok) toast.success('All caught up') 
    else { toast.error('Could not mark them read. Try again.'); refresh() }
  }

  const toggle = async (category: string, field: 'emailEnabled'|'inAppEnabled'|'smsEnabled', value: boolean) => {
    const before = prefs
    setPrefs(p => p.map(x => x.category === category ? { ...x, [field]: value } : x))
    setSaving(category + field)
    try {
      await notificationApi.updatePreference({ category, [field]: value })
    } catch {
      setPrefs(before)
      toast.error('Could not save that preference. Try again.')
    } finally {
      setSaving(null)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h2 className="font-display text-xl font-semibold text-gray-900 dark:text-white">Notifications</h2>
          <p className="text-[13px] text-muted mt-0.5">
            {unread > 0 ? `${unread} unread` : 'Everything here has been read'}
          </p>
        </div>
        {unread > 0 && (
          <Button variant="secondary" size="sm" leftIcon={<CheckCheck size={14}/>} onClick={onMarkAll}>
            Mark all read
          </Button>
        )}
      </div>

      {loading ? <PageLoader/> : items.length === 0 ? (
        <Card padding="none">
          <EmptyState
            icon={<Inbox size={24}/>}
            title="No notifications yet"
            desc="When your verification is decided, a payment lands, or a viewing is booked, it will show up here."
          />
        </Card>
      ) : (
        <Card padding="none">
          <ul className="divide-y divide-[color:var(--border)]">
            {items.map(n => (
              <li key={n.id}>
                <button
                  onClick={() => openNotification(n)}
                  className={cn(
                    'w-full text-left px-4 py-3.5 flex items-start gap-3 transition-colors',
                    'hover:bg-gray-50 dark:hover:bg-[#1A1A35]',
                    !n.read && 'bg-gold-50/50 dark:bg-gold-500/[0.06]')}>
                  <span className={cn('mt-2 w-1.5 h-1.5 rounded-full shrink-0',
                    n.read ? 'bg-transparent' : 'bg-gold-500')}/>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className={cn('text-[14px] leading-snug',
                        n.read ? 'text-gray-700 dark:text-gray-300' : 'font-semibold text-gray-900 dark:text-white')}>
                        {n.subject || n.templateCode}
                      </p>
                      <Badge variant={CATEGORY_VARIANT[n.category] ?? 'muted'} size="sm">
                        {CATEGORY_LABEL[n.category] ?? n.category}
                      </Badge>
                    </div>
                    {n.body && <p className="text-[12.5px] text-muted mt-1 whitespace-pre-line line-clamp-3">{n.body}</p>}
                    <p className="text-[11px] text-muted mt-1.5">{fmt.ago(n.createdAt)}</p>
                  </div>
                </button>
              </li>
            ))}
          </ul>
          {!last && (
            <div className="p-3 border-t border-base flex justify-center">
              <Button variant="ghost" size="sm" loading={more} onClick={loadMore}>Load more</Button>
            </div>
          )}
        </Card>
      )}

      <div id="preferences" className="scroll-mt-20">
        <h3 className="font-display text-lg font-semibold text-gray-900 dark:text-white mb-1">Preferences</h3>
        <p className="text-[13px] text-muted mb-4">
          Choose how you hear about each kind of update. Account notices like password resets are always sent.
        </p>

        {prefsLoading ? (
          <Card><div className="flex justify-center py-8"><Spinner size={24}/></div></Card>
        ) : prefs.length === 0 ? (
          <Card><p className="text-[13px] text-muted py-2">Preferences are unavailable right now.</p></Card>
        ) : (
          <Card padding="none">
            <div className="hidden sm:grid grid-cols-[1fr_auto_auto_auto] gap-4 px-4 py-2.5 border-b border-base text-[11px] font-medium text-muted uppercase tracking-wide">
              <span>Category</span>
              <span className="w-16 text-center">Email</span>
              <span className="w-16 text-center">In-app</span>
              <span className="w-16 text-center">SMS</span>
            </div>
            <ul className="divide-y divide-[color:var(--border)]">
              {prefs.map(p => (
                <li key={p.category} className="grid grid-cols-1 sm:grid-cols-[1fr_auto_auto_auto] gap-3 sm:gap-4 px-4 py-3.5 items-center">
                  <div className="min-w-0">
                    <p className="text-[13.5px] font-medium text-gray-900 dark:text-white">
                      {CATEGORY_LABEL[p.category] ?? p.category}
                    </p>
                    {CATEGORY_BLURB[p.category] && (
                      <p className="text-[11.5px] text-muted mt-0.5">{CATEGORY_BLURB[p.category]}</p>
                    )}
                  </div>
                  <Toggle icon={<Mail size={13}/>} label="Email" busy={saving === p.category+'emailEnabled'}
                    checked={p.emailEnabled} onChange={v => toggle(p.category, 'emailEnabled', v)}/>
                  <Toggle icon={<Monitor size={13}/>} label="In-app" busy={saving === p.category+'inAppEnabled'}
                    checked={p.inAppEnabled} onChange={v => toggle(p.category, 'inAppEnabled', v)}/>
                  <Toggle icon={<MessageSquare size={13}/>} label="SMS" busy={saving === p.category+'smsEnabled'}
                    checked={p.smsEnabled} onChange={v => toggle(p.category, 'smsEnabled', v)} hint="Not sending yet"/>
                </li>
              ))}
            </ul>
          </Card>
        )}
      </div>
    </div>
  )
}

function Toggle({ checked, onChange, busy, icon, label, hint }:{
  checked:boolean; onChange:(v:boolean)=>void; busy?:boolean
  icon:React.ReactNode; label:string; hint?:string
}) {
  return (
    <div className="flex items-center gap-2 sm:w-16 sm:justify-center" title={hint}>
      <span className="sm:hidden flex items-center gap-1.5 text-[12px] text-muted w-20">{icon}{label}</span>
      <button
        role="switch"
        aria-checked={checked}
        aria-label={label}
        disabled={busy}
        onClick={() => onChange(!checked)}
        className={cn(
          'relative w-9 h-5 rounded-full transition-colors shrink-0 disabled:opacity-50',
          checked ? 'bg-gold-500' : 'bg-gray-200 dark:bg-gray-700')}>
        <span className={cn(
          'absolute top-0.5 w-4 h-4 rounded-full bg-white shadow-sm transition-transform',
          checked ? 'translate-x-[18px]' : 'translate-x-0.5')}/>
      </button>
      {hint && <span className="sm:hidden text-[10.5px] text-muted">{hint}</span>}
    </div>
  )
}
