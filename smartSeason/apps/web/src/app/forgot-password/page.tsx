import { ForgotPasswordForm } from "@/components/forgot-password-form";

export default function ForgotPasswordPage() {
  return (
    <div className="flex min-h-screen items-center justify-center px-3.5 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-5">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/logo-icon.svg" alt="" aria-hidden="true" className="mb-3 h-14 w-14" />
          <h1 className="text-2xl font-semibold tracking-tight">Reset your password</h1>
          <p className="mt-1 text-sm text-[var(--color-muted)]">
            We will send a code to your email address.
          </p>
        </div>
        <ForgotPasswordForm />
      </div>
    </div>
  );
}
