'use client'
import { useRef, useState } from 'react'
import { Upload, Loader2, CheckCircle2, AlertTriangle } from 'lucide-react'
import { documentApi, verifApi } from '@/lib/api'
import type { BulkIntakeResponse, OwnershipVerificationResponse } from '@/types'
import toast from 'react-hot-toast'

const MAX_SIZE = 10 * 1024 * 1024
// The server accepts at most 30 documents in one intake call.
export const MAX_FILES = 30

/** Splits what was chosen into what can be sent and a plain reason for each file that cannot. */
export function screenFiles(files: { name: string; size: number }[]): { ok: number[]; refused: string[] } {
  const ok: number[] = []
  const refused: string[] = []
  files.forEach((f, i) => {
    if (f.size > MAX_SIZE) refused.push(`${f.name} is over 10 MB`)
    else if (ok.length >= MAX_FILES) refused.push(`${f.name}: at most ${MAX_FILES} documents at a time`)
    else ok.push(i)
  })
  return { ok, refused }
}

/**
 * Drop a folder of title documents with no category chosen for any of them. Each file is stored,
 * then the whole set is handed to the server in one call, which files what it is sure about and
 * lists what still needs a person.
 */
export default function SmartIntakeUpload({
  verificationId,
  categoryLabels,
  onFiled,
}: {
  verificationId: string
  categoryLabels: Record<string, string>
  onFiled: (verifications: OwnershipVerificationResponse[]) => void
}) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<BulkIntakeResponse | null>(null)
  const [failed, setFailed] = useState<string[]>([])

  const label = (c: string) => categoryLabels[c] || c.replace(/_/g, ' ').toLowerCase()

  const run = async (chosen: File[]) => {
    if (chosen.length === 0) return
    const { ok, refused } = screenFiles(chosen)
    setFailed(refused)
    setResult(null)
    if (ok.length === 0) return
    setBusy(true)
    const stored: { documentUrl: string; originalFilename: string; mimeType?: string; fileSizeBytes?: number }[] = []
    const couldNot: string[] = [...refused]
    for (const index of ok) {
      const file = chosen[index]
      try {
        const res = await documentApi.upload(file, 'ownership_intake')
        stored.push({ documentUrl: res.url, originalFilename: file.name, mimeType: file.type || undefined, fileSizeBytes: file.size })
      } catch (e: any) {
        couldNot.push(`${file.name}: ${e?.response?.data?.error || 'upload failed'}`)
      }
    }
    setFailed(couldNot)
    try {
      if (stored.length > 0) {
        const intake = await verifApi.bulkOwnerDocs(verificationId, stored)
        setResult(intake)
        if (intake.fullyAutomatic) toast.success(`${intake.filedCount} document${intake.filedCount === 1 ? '' : 's'} filed`)
        onFiled(await verifApi.myOwner())
      }
    } catch (e: any) {
      toast.error(e?.response?.data?.error || 'Could not file those documents')
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  return (
    <div className="space-y-2">
      <button type="button" disabled={busy} onClick={() => input.current?.click()}
        className="w-full border border-dashed rounded-lg px-3 py-3 flex items-center justify-center gap-2 text-sm text-muted border-gray-200 dark:border-[#1E1E3A] disabled:opacity-60">
        {busy ? <Loader2 size={14} className="animate-spin"/> : <Upload size={14}/>}
        {busy ? 'Filing your documents…' : 'Not sure which is which? Add a folder and we will sort it'}
      </button>
      <input ref={input} type="file" multiple accept=".jpg,.jpeg,.png,.pdf" className="hidden"
        onChange={e => run(Array.from(e.target.files ?? []))}/>

      {result && (
        <div className="rounded-lg border border-base p-3 space-y-2 text-sm" role="status">
          <p className="flex items-center gap-1.5 font-medium text-gray-900 dark:text-white">
            <CheckCircle2 size={14} className="text-emerald-500"/>
            Filed {result.filedCount} document{result.filedCount === 1 ? '' : 's'}
          </p>
          {Object.entries(result.filed).map(([category, names]) => (
            <p key={category} className="text-xs text-muted">
              <span className="text-gray-700 dark:text-gray-300">{label(category)}:</span> {names.join(', ')}
            </p>
          ))}
          {result.needsReview.length > 0 && (
            <div className="space-y-1">
              <p className="flex items-center gap-1.5 text-xs font-medium text-amber-600 dark:text-amber-400">
                <AlertTriangle size={12}/> {result.needsReview.length} need{result.needsReview.length === 1 ? 's' : ''} you
              </p>
              {result.needsReview.map(r => (
                <p key={r.filename} className="text-xs text-muted"><span className="text-gray-700 dark:text-gray-300">{r.filename}:</span> {r.message}</p>
              ))}
            </div>
          )}
          {result.stillMissing.length > 0 && (
            <p className="text-xs text-muted">Still missing: {result.stillMissing.map(label).join(', ')}</p>
          )}
        </div>
      )}

      {failed.length > 0 && (
        <ul className="text-xs text-red-600 dark:text-red-400 space-y-0.5" role="alert">
          {failed.map(f => <li key={f}>{f}</li>)}
        </ul>
      )}
    </div>
  )
}
