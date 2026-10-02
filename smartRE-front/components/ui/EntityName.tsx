'use client'
import { useQuery } from '@tanstack/react-query'
import { propertyApi, userApi } from '@/lib/api'

/**
 * A readable name for an id that an admin screen only has a UUID for. Names are fetched once
 * and cached for the session; while loading, or if the lookup is refused, the short id is shown
 * so a screen never renders blank.
 */
export function UserName({ id, withEmail }: { id?: string | null; withEmail?: boolean }) {
  const { data } = useQuery({
    queryKey: ['entity-name', 'user', id],
    queryFn: () => userApi.getById(id as string),
    enabled: !!id,
    staleTime: 10 * 60_000,
    retry: false,
  })
  if (!id) return <>Unknown user</>
  if (!data) return <>{id.slice(0, 8)}…</>
  return <>{data.fullName}{withEmail && data.email ? <span className="text-muted font-normal"> · {data.email}</span> : null}</>
}

export function PropertyTitle({ id }: { id?: string | null }) {
  const { data } = useQuery({
    queryKey: ['entity-name', 'property', id],
    queryFn: () => propertyApi.getById(id as string),
    enabled: !!id,
    staleTime: 10 * 60_000,
    retry: false,
  })
  if (!id) return <>Unknown property</>
  if (!data) return <>{id.slice(0, 8)}…</>
  return <>{data.title}</>
}
