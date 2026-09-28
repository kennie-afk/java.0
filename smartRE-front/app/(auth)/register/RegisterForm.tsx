'use client'
import { useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Eye, EyeOff, Mail, Lock, User, Phone, Home, Building2, KeyRound } from 'lucide-react'
import { authApi, pmsApi } from '@/lib/api'
import { useAuthStore } from '@/lib/store'
import { Card } from '@/components/ui/Card'
import Button from '@/components/ui/Button'
import Input from '@/components/ui/Input'
import toast from 'react-hot-toast'

const schema = z.object({
  fullName: z.string().min(2,'Name must be at least 2 characters'),
  email:    z.string().email('Invalid email'),
  password: z.string().min(8,'Password must be at least 8 characters'),
  phone:    z.string().optional(),
  role:     z.enum(['BUYER','SELLER','TENANT']),
})
type Form = z.infer<typeof schema>

/**
 * TENANT is offered here, but it is a different kind of choice from the other two and the
 * copy has to say so. A buyer or seller is self-sufficient from the moment they sign up; a
 * tenant is not, because a tenancy is created by a landlord linking their tenant record.
 * Someone who picks this and sees an empty page has been misled, so the description sets
 * the expectation before they choose rather than after.
 */
const roleInfo = {
  BUYER:  { icon: Home,      label: 'Buy or rent',   desc: 'Browse verified listings' },
  SELLER: { icon: Building2, label: 'Sell or list',  desc: 'Free to list, 2.5% on close' },
  TENANT: { icon: KeyRound,  label: 'I already rent', desc: 'Your landlord links your tenancy' },
}

export default function RegisterForm() {
  const sp = useSearchParams()
  const roleParam = sp.get('role')
  // An invitation decides the role; it was issued against a specific tenancy, so offering a
  // choice here would only let someone arrive by invitation and register as something the
  // invitation cannot connect them to.
  const inviteToken = sp.get('invite')
  const initialRole: Form['role'] = inviteToken
    ? 'TENANT'
    : roleParam === 'SELLER' ? 'SELLER' : roleParam === 'TENANT' ? 'TENANT' : 'BUYER'
  const [showPwd, setShow] = useState(false)
  const { setUser } = useAuthStore()
  const router = useRouter()
  const { register, handleSubmit, watch, formState:{ errors, isSubmitting } } = useForm<Form>({
    resolver: zodResolver(schema), defaultValues:{ role: initialRole }
  })
  const selectedRole = watch('role')

  const onSubmit = async (d: Form) => {
    try {
      const res = await authApi.register(d)
      setUser(res)

      if (inviteToken) {
        // Redeemed here rather than by the server during registration, so the request is
        // made as the newly signed-in tenant. That is what makes the connection theirs:
        // the token proves they received the landlord's email, the session proves who
        // they are, and neither is taken from the request body.
        try {
          await pmsApi.tenants.redeemInvite(inviteToken)
          toast.success('Account created and your tenancy is connected.')
        } catch (e: any) {
          // The account exists and is usable; only the link failed. Saying so beats a
          // generic failure that makes them think they have to register again.
          toast.error(e.response?.data?.error
            || 'Account created, but the invitation could not be used. Ask your landlord to resend it.')
        }
      } else {
        toast.success('Account created successfully!')
      }

      router.push(res.role === 'ADMIN' ? '/overview' : '/dashboard')
    } catch (e: any) {
      toast.error(e.response?.data?.error || 'Registration failed. Please try again.')
    }
  }

  return (
    <Card padding="sm" className="sm:p-6">
      <div className="mb-5">
        <h1 className="font-display text-xl font-semibold text-gray-900 dark:text-white">Create your account</h1>
        <p className="text-base text-muted mt-1">Free for buyers. Free to list for sellers, and you only pay when a sale closes.</p>
      </div>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-3.5">
        {inviteToken && (
          <div className="rounded-lg border border-gold-300 bg-gold-50 dark:bg-gold-500/10 px-3 py-2.5">
            <p className="text-base font-semibold text-gold-700 dark:text-gold-400">You have been invited to a tenancy</p>
            <p className="text-2xs text-muted mt-0.5">
              Create your account with this link and your lease, invoices and receipts appear straight away.
            </p>
          </div>
        )}

        <div className={inviteToken ? 'hidden' : undefined}>
          <div className="grid grid-cols-3 gap-2 mb-1">
            {(Object.keys(roleInfo) as Array<keyof typeof roleInfo>).map(role => {
              const info = roleInfo[role]
              const active = selectedRole === role
              return (
                <label key={role} className="cursor-pointer">
                  <input type="radio" className="sr-only" value={role} {...register('role')}/>
                  <div className={`flex items-center gap-2 border-2 rounded-lg px-2.5 py-2.5 text-left transition-all
                    ${active ? 'border-gold-500 bg-gold-50 dark:bg-gold-500/10' : 'border-gray-200 dark:border-[#3A2F1F] hover:border-gold-300'}`}>
                    <info.icon size={17} className={active ? 'text-gold-600 dark:text-gold-400' : 'text-gray-400'}/>
                    <div>
                      <p className={`text-base font-semibold leading-tight ${active ? 'text-gold-700 dark:text-gold-400' : 'text-gray-700 dark:text-gray-200'}`}>{info.label}</p>
                      <p className="text-2xs text-muted leading-tight">{info.desc}</p>
                    </div>
                  </div>
                </label>
              )
            })}
          </div>
          {errors.role && <p className="text-xs text-red-500 mt-1">{errors.role.message}</p>}
        </div>

        <Input label="Full name" placeholder="Kennie Kamau" required leftIcon={<User size={15}/>} {...register('fullName')} error={errors.fullName?.message}/>
        <Input label="Email address" type="email" placeholder="kennieme24@gmail.com" required leftIcon={<Mail size={15}/>} {...register('email')} error={errors.email?.message}/>
        <Input label="Phone number" type="tel" placeholder="0712 345 678" leftIcon={<Phone size={15}/>} {...register('phone')} error={errors.phone?.message}/>
        <Input label="Password" type={showPwd?'text':'password'} placeholder="Min. 8 characters" required leftIcon={<Lock size={15}/>}
          rightIcon={<button type="button" onClick={() => setShow(s=>!s)} className="text-gray-400 hover:text-gray-600" aria-label={showPwd ? 'Hide password' : 'Show password'}>{showPwd?<EyeOff size={15}/>:<Eye size={15}/>}</button>}
          {...register('password')} error={errors.password?.message}/>

        <Button type="submit" fullWidth loading={isSubmitting}>Create account</Button>
      </form>
    </Card>
  )
}
