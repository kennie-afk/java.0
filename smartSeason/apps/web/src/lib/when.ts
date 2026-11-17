/** Dates shown to farmers are in Kenyan time whatever the server's clock says. */
const ZONE = "Africa/Nairobi";

export function when(iso: string): string {
  return new Date(iso).toLocaleString("en-KE", {
    timeZone: ZONE,
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit"
  });
}

/** "2 h", "3 d": how long ago, for lists where the exact time is noise. */
export function ago(iso: string): string {
  const minutes = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.round(minutes / 60);
  if (hours < 48) return `${hours} h`;
  return `${Math.round(hours / 24)} d`;
}
