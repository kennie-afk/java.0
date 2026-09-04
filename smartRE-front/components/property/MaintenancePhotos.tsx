'use client'
import { useState } from 'react'
import { ImageOff, X } from 'lucide-react'
import { pmsApi } from '@/lib/api'
import { cn } from '@/lib/utils'

export default function MaintenancePhotos({ requestId, count, className }:
  { requestId:string; count:number; className?:string }) {
  const [broken, setBroken] = useState<Record<number, boolean>>({})
  const [open, setOpen] = useState<number|null>(null)

  if (count === 0) return null

  return (
    <>
      <div className={cn('flex gap-2 flex-wrap', className)}>
        {Array.from({ length: count }).map((_, i) => (
          <button key={i} onClick={() => setOpen(i)}
            className="relative w-16 h-16 rounded-md overflow-hidden bg-gray-100 dark:bg-[#1A1A35] border border-base hover:opacity-80 transition-opacity"
            aria-label={`Photo ${i + 1}`}>
            {broken[i] ? (
              <span className="w-full h-full flex items-center justify-center text-gray-400"><ImageOff size={16}/></span>
            ) : (
              <img src={pmsApi.maintenance.photoUrl(requestId, i)} alt={`Photo ${i + 1}`}
                className="w-full h-full object-cover"
                onError={() => setBroken(b => ({ ...b, [i]: true }))}/>
            )}
          </button>
        ))}
      </div>

      {open !== null && (
        <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4" onClick={() => setOpen(null)}>
          <button className="absolute top-4 right-4 text-white/80 hover:text-white" aria-label="Close"
            onClick={() => setOpen(null)}><X size={24}/></button>
          <img src={pmsApi.maintenance.photoUrl(requestId, open)} alt={`Photo ${open + 1}`}
            className="max-h-full max-w-full object-contain rounded-lg"
            onClick={e => e.stopPropagation()}/>
        </div>
      )}
    </>
  )
}
