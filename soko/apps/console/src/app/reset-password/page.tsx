import { ResetForm } from "@/app/reset-password/form";

export default async function ResetPasswordPage({
  searchParams
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const { token } = await searchParams;
  return (
    <main className="min-h-screen bg-[var(--color-canvas)]">
      <div className="mx-auto flex min-h-screen w-full max-w-[400px] items-center px-6 py-12">
        <ResetForm token={token ?? ""} />
      </div>
    </main>
  );
}
