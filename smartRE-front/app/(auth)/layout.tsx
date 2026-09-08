'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { ShieldCheck, Smartphone, Calendar } from 'lucide-react'
import Logo from '@/components/brand/Logo'

const benefits = [
  { icon: ShieldCheck, text: 'Every seller and title verified before you pay' },
  { icon: Smartphone,  text: 'Pay safely through M-Pesa escrow' },
  { icon: Calendar,    text: 'Schedule viewings and track offers in one place' },
]

export default function AuthLayout({ children }:{ children:React.ReactNode }) {
  const path = usePathname()
  const isLogin = path === '/login'
  const showTabs = path === '/login' || path === '/register'

  return (
    <div className="min-h-screen bg-surface-2 flex flex-col items-center justify-center p-4">
      <div className="w-full max-w-md">
        <Link href="/" className="flex items-center justify-center gap-2.5 mb-4">
          <Logo size={26} idSuffix="auth"/>
        </Link>

        {showTabs && (
          <div className="flex bg-gray-100 dark:bg-[#2E2518] rounded-lg p-1 mb-4">
            <Link href="/login" className={`flex-1 text-center py-2 rounded-lg text-sm font-semibold transition-colors ${isLogin ? 'bg-white dark:bg-[#201911] text-gray-900 dark:text-white shadow-sm' : 'text-muted hover:text-gray-700 dark:hover:text-gray-200'}`}>
              Sign in
            </Link>
            <Link href="/register" className={`flex-1 text-center py-2 rounded-lg text-sm font-semibold transition-colors ${!isLogin ? 'bg-white dark:bg-[#201911] text-gray-900 dark:text-white shadow-sm' : 'text-muted hover:text-gray-700 dark:hover:text-gray-200'}`}>
              Create account
            </Link>
          </div>
        )}

        {children}

        <div className="flex items-center justify-center gap-3.5 mt-6 flex-wrap">
          {benefits.map(b => (
            <span key={b.text} className="flex items-center gap-1.5 text-xs text-muted">
              <b.icon size={12} className="text-gold-500 shrink-0"/>{b.text}
            </span>
          ))}
        </div>
      </div>
    </div>
  )
}
