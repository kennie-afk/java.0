"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

export type FacilityRef = { id: string; name: string };
export type Me = { practitionerId: string; organisationId: string; fullName: string; facilities: FacilityRef[]; permissions: string[] };

type Ctx = {
  me: Me;
  can: (permission: string) => boolean;
  facilityId: string;
  facility: FacilityRef;
  setFacility: (id: string) => void;
};

const SessionContext = createContext<Ctx | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [facilityId, setFacilityId] = useState("");

  useEffect(() => {
    fetch("/api/session")
      .then((r) => (r.ok ? (r.json() as Promise<Me>) : Promise.reject(new Error("unauthorised"))))
      .then((m) => {
        setMe(m);
        let saved = "";
        try {
          saved = localStorage.getItem("hms.facility") ?? "";
        } catch {
          // storage can be unavailable; fall back to the first facility
        }
        setFacilityId(m.facilities.some((f) => f.id === saved) ? saved : (m.facilities[0]?.id ?? ""));
      })
      .catch(() => {
        window.location.href = "/login";
      });
  }, []);

  const setFacility = useCallback((id: string) => {
    setFacilityId(id);
    try {
      localStorage.setItem("hms.facility", id);
    } catch {
      // ignore
    }
  }, []);

  const value = useMemo<Ctx | null>(() => {
    if (!me || !facilityId) return null;
    const perms = new Set(me.permissions);
    return {
      me,
      can: (p) => perms.has(p),
      facilityId,
      facility: me.facilities.find((f) => f.id === facilityId) ?? me.facilities[0],
      setFacility
    };
  }, [me, facilityId, setFacility]);

  if (!me) return <div className="p-6 text-sm text-muted">Loading...</div>;
  if (!value) return <div className="p-6 text-sm text-muted">Your account has no facility assigned. Ask an administrator.</div>;
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error("useSession outside SessionProvider");
  return ctx;
}
