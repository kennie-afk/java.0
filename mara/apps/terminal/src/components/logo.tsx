/* eslint-disable @next/next/no-img-element */
export function Logo({ size = 28 }: { size?: number }) {
  return (
    <span className="flex items-center gap-2">
      <img src="/logo-icon.svg" alt="" width={size} height={size} />
      <span className="text-xl font-semibold tracking-tight">
        Ma<span className="text-accent">ra</span>
      </span>
    </span>
  );
}
