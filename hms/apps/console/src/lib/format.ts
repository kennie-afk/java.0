export const date = (v?: string | null) => (v ? new Date(v).toLocaleDateString("en-KE", { day: "2-digit", month: "short", year: "numeric" }) : "");
export const time = (v?: string | null) => (v ? new Date(v).toLocaleTimeString("en-KE", { hour: "2-digit", minute: "2-digit" }) : "");
export const stamp = (v?: string | null) => (v ? `${date(v)} ${time(v)}` : "");
export const kes = (v?: number | string | null) => `KES ${Number(v ?? 0).toLocaleString("en-KE", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
export const today = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Nairobi" });
export const addDays = (iso: string, n: number) => {
  const d = new Date(iso + "T00:00:00Z");
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};
export const age = (birth: string) => {
  const b = new Date(birth);
  const n = new Date();
  let y = n.getFullYear() - b.getFullYear();
  if (n < new Date(n.getFullYear(), b.getMonth(), b.getDate())) y--;
  return y < 2 ? `${Math.max(0, (n.getFullYear() - b.getFullYear()) * 12 + n.getMonth() - b.getMonth())}m` : `${y}y`;
};
export const uuid = () => crypto.randomUUID();
