export function homeFor(role: string): string {
  if (role === "SUPPLIER") {
    return "/supplier";
  }
  if (role === "CUSTOMER") {
    return "/shop";
  }
  return "/";
}
