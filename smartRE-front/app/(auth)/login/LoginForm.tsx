'use client'
import { useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Eye, EyeOff, Mail, Lock, Building2, ArrowRight } from 'lucide-react'
import { authApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import toast from 'react-hot-toast'

const schema = z.object({ email: z.string().email('Invalid email'), password: z.string().min(1,'Required') })
type Form = z.infer<typeof schema>

/**
 * "Sign in as" — a role picker for demonstrating the platform.
 *
 * <p>It is **autofill, not an authentication bypass**: choosing an account fills the real
 * email and the shared demo password and submits the ordinary login form, so the request
 * that reaches the server is indistinguishable from a person typing. There is no
 * password-less path and no allowlist on the backend to get wrong.
 *
 * <p>Off unless NEXT_PUBLIC_SIGN_IN_AS is "true", and the password comes from
 * NEXT_PUBLIC_SIGN_IN_AS_PASSWORD rather than being written here — a shared credential
 * committed into a component is one grep away from being in a public repository. Both are
 * build-time values in Next, so a production build without them ships a login page that
 * has never heard of this.
 *
 * <p>Named for what it does. It is not a "demo login", because what it demonstrates is the
 * role model: the same screens answer differently for a seller, a landlord and a tenant,
 * and clicking between them is the fastest way to see that.
 */
const SIGN_IN_AS_ENABLED = process.env.NEXT_PUBLIC_SIGN_IN_AS === 'true'
const SIGN_IN_AS_PASSWORD = process.env.NEXT_PUBLIC_SIGN_IN_AS_PASSWORD ?? ''

const SIGN_IN_AS_ACCOUNTS: Array<{ email: string; role: string; description: string }> = [
  { email: 'demo.admin@smartre.test',       role: 'ADMIN',    description: 'Every listing, user and payment' },
  { email: 'demo.seller@smartre.test',      role: 'SELLER',   description: 'Lists property, sees offers and viewings' },
  { email: 'demo.buyer@smartre.test',       role: 'BUYER',    description: 'Books viewings, makes offers, pays deposits' },
  { email: 'demo.landlord@smartre.test',    role: 'LANDLORD', description: 'Units, tenancies and rent invoices' },
  { email: 'david.kimani@example.co.ke',    role: 'TENANT',   description: 'A live tenancy with invoices and receipts' },
]

export default function LoginForm() {
  const [showPwd, setShow] = useState(false)
  const { setUser } = useAuthStore()
  const router = useRouter()
  const sp = useSearchParams()
  const { register, handleSubmit, setValue, formState:{ errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) })
  const [signingInAs, setSigningInAs] = useState('')

  const signInAs = async (email: string) => {
    if (!email) return
    setSigningInAs(email)
    // Fill the visible fields too, so what is submitted is what the form shows. A picker
    // that logs someone in while the inputs sit empty looks like a trick.
    setValue('email', email)
    setValue('password', SIGN_IN_AS_PASSWORD)
    await onSubmit({ email, password: SIGN_IN_AS_PASSWORD })
    setSigningInAs('')
  }

  const onSubmit = async (d: Form) => {
    try {
      const res = await authApi.login(d)
      setUser(res)
      toast.success(`Welcome back, ${res.fullName.split(' ')[0]}!`)
      const returnTo = sp.get('returnTo')
      router.push(returnTo || (res.role === 'ADMIN' ? '/overview' : '/dashboard'))
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Invalid email or password')
    }
  }

  return (
    <>
      <Card padding="sm" className="sm:p-6">
        <div className="mb-5">
          <h1 className="font-display text-xl font-semibold text-gray-900 dark:text-white">Sign in to your account</h1>
          <p className="text-base text-muted mt-1">Continue to schedule viewings, pay securely, and manage your listings.</p>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-3.5">
          <Input label="Email address" type="email" placeholder="kennieme24@gmail.com" required
            leftIcon={<Mail size={15}/>} {...register('email')} error={errors.email?.message}/>
          <Input label="Password" type={showPwd?'text':'password'} placeholder="••••••••" required
            leftIcon={<Lock size={15}/>}
            rightIcon={<button type="button" onClick={() => setShow(s=>!s)} className="text-gray-400 hover:text-gray-600" aria-label={showPwd ? 'Hide password' : 'Show password'}>{showPwd?<EyeOff size={15}/>:<Eye size={15}/>}</button>}
            {...register('password')} error={errors.password?.message}/>
          <div className="flex justify-end -mt-1.5">
            <Link href="/forgot-password" className="text-sm font-medium text-gold-600 dark:text-gold-400 hover:underline">Forgot password?</Link>
          </div>
          <Button type="submit" fullWidth loading={isSubmitting}>Sign in</Button>

          {SIGN_IN_AS_ENABLED && SIGN_IN_AS_PASSWORD && (
            <div className="pt-3 mt-1 border-t border-gray-200 dark:border-[#3A2F1F]">
              <label htmlFor="sign-in-as" className="block text-2xs uppercase tracking-wide text-muted mb-1.5">
                Sign in as
              </label>
              <select
                id="sign-in-as"
                value={signingInAs}
                disabled={isSubmitting}
                onChange={e => signInAs(e.target.value)}
                className="w-full rounded-lg border border-gray-200 dark:border-[#3A2F1F] bg-white dark:bg-[#221F1A]
                           px-3 py-2 text-base text-gray-700 dark:text-gray-200 disabled:opacity-60"
              >
                <option value="">Choose a role…</option>
                {SIGN_IN_AS_ACCOUNTS.map(a => (
                  <option key={a.email} value={a.email}>{a.role} — {a.description}</option>
                ))}
              </select>
              <p className="text-2xs text-muted mt-1.5">
                Fills the form above and signs in normally. Available on this build only.
              </p>
            </div>
          )}
        </form>
      </Card>

      <Link href="/register?role=SELLER" className="mt-4 flex items-center gap-3 p-2.5 rounded-lg border border-dashed border-gold-300 dark:border-gold-500/30 bg-gold-50/50 dark:bg-gold-500/5 hover:bg-gold-50 dark:hover:bg-gold-500/10 transition-colors group">
        <div className="w-9 h-9 rounded-lg bg-gold-100 dark:bg-gold-500/15 text-gold-600 dark:text-gold-400 flex items-center justify-center shrink-0">
          <Building2 size={16}/>
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-base font-semibold text-gray-900 dark:text-white">New here and want to sell a property?</p>
          <p className="text-xs text-muted">Create a seller account. Listing is free, and you only pay 2.5% on a closed sale.</p>
        </div>
        <ArrowRight size={15} className="text-gold-500 shrink-0"/>
      </Link>
    </>
  )
}
