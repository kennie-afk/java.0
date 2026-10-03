"use client";

import { useState } from "react";
import { post, upload, useFetch } from "@/lib/api";
import { stamp } from "@/lib/format";
import { Button, Card, Confirm, ErrorNote, Field, Input, Notice, useAction } from "@/components/ui";

type Image = { id: string; contentType: string; sizeBytes: number; width: number; height: number; caption?: string; uploadedAt: string; url: string };

const src = (url: string) => `/api${url}`;

/**
 * The images attached to a study, a plain viewer, and the upload form. PNG and JPEG only; DICOM is not supported and the form says so.
 * The picture is shown as the file is: the controls change only how it is displayed on this screen (zoom, invert, brightness,
 * contrast) and never touch the stored file.
 */
export function ImagingImages({ orderId, status, canAttach }: { orderId: string; status: string; canAttach: boolean }) {
  const list = useFetch<Image[]>(`/v1/imaging/orders/${orderId}/attachments`);
  const [open, setOpen] = useState<string | null>(null);
  const [view, setView] = useState({ fit: true, invert: false, brightness: 100, contrast: 100 });
  const [file, setFile] = useState<File | null>(null);
  const [caption, setCaption] = useState("");
  const act = useAction();
  const images = list.data ?? [];
  const current = images.find((i) => i.id === open) ?? null;
  const editable = canAttach && (status === "PERFORMED" || status === "REPORTED");
  if (!list.loading && images.length === 0 && !editable) return null;
  const filter = `${view.invert ? "invert(1) " : ""}brightness(${view.brightness}%) contrast(${view.contrast}%)`;
  return (
    <Card title="Images" description="PNG or JPEG. Shown as stored; the controls below only change how it looks on this screen.">
      <ErrorNote error={act.error} />
      {images.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {images.map((i) => (
            <button key={i.id} type="button" onClick={() => setOpen(open === i.id ? null : i.id)} className={`overflow-hidden rounded-md border bg-black ${open === i.id ? "border-accent" : "border-line"}`} aria-label={`Open image ${i.caption ?? i.id}`}>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={src(i.url)} alt={i.caption ?? "Study image"} width={96} height={72} className="h-[72px] w-24 object-cover" />
            </button>
          ))}
        </div>
      )}
      {current && (
        <div className="mt-3 space-y-2">
          <div className="flex flex-wrap items-center gap-3 text-sm">
            <Button variant="secondary" onClick={() => setView({ ...view, fit: !view.fit })}>{view.fit ? "Actual size" : "Fit to screen"}</Button>
            <label className="flex items-center gap-1.5"><input type="checkbox" checked={view.invert} onChange={(e) => setView({ ...view, invert: e.target.checked })} />Invert</label>
            <label className="flex items-center gap-1.5">Brightness<input type="range" min={40} max={200} value={view.brightness} onChange={(e) => setView({ ...view, brightness: Number(e.target.value) })} /></label>
            <label className="flex items-center gap-1.5">Contrast<input type="range" min={40} max={250} value={view.contrast} onChange={(e) => setView({ ...view, contrast: Number(e.target.value) })} /></label>
            <Button variant="secondary" onClick={() => setView({ fit: true, invert: false, brightness: 100, contrast: 100 })}>Reset</Button>
            {editable && <Confirm label="Withdraw image" prompt="Withdraw this image from the study, for example because it belongs to another patient? It leaves the study; the record of it stays in the audit trail." needsReason minReason={5}
              onConfirm={(reason) => act.run(async () => { await post(`/v1/imaging/orders/${orderId}/attachments/${current.id}/remove`, { reason }); setOpen(null); await list.reload(); })} />}
          </div>
          <div className="max-h-[70vh] overflow-auto rounded-md border border-line bg-black">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={src(current.url)} alt={current.caption ?? "Study image"} style={{ filter, ...(view.fit ? { maxWidth: "100%", maxHeight: "70vh", margin: "0 auto", display: "block" } : { maxWidth: "none" }) }} />
          </div>
          <p className="text-xs text-muted">{current.caption ? `${current.caption} · ` : ""}{current.width} by {current.height} px · {(current.sizeBytes / 1024).toFixed(0)} KB · attached {stamp(current.uploadedAt)}</p>
        </div>
      )}
      {editable && (
        <form className="mt-3 flex flex-wrap items-end gap-2 border-t border-line pt-3" onSubmit={(e) => {
          e.preventDefault();
          if (!file) return;
          void act.run(async () => {
            const form = new FormData();
            form.append("file", file);
            if (caption.trim()) form.append("caption", caption.trim());
            await upload(`/v1/imaging/orders/${orderId}/attachments`, form);
            setFile(null);
            setCaption("");
            (e.target as HTMLFormElement).reset();
            await list.reload();
          });
        }}>
          <Field label="Image (PNG or JPEG, up to 8 MB)"><Input type="file" accept="image/png,image/jpeg" onChange={(e) => setFile(e.target.files?.[0] ?? null)} /></Field>
          <Field label="Caption"><Input maxLength={200} value={caption} onChange={(e) => setCaption(e.target.value)} placeholder="e.g. PA erect" /></Field>
          <Button type="submit" busy={act.busy} disabled={!file}>Attach image</Button>
        </form>
      )}
      {editable && <div className="pt-2"><Notice title="DICOM is not supported">Export the key images from the modality as PNG or JPEG. Original DICOM studies stay in the modality's own archive.</Notice></div>}
    </Card>
  );
}
