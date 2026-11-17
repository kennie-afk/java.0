import Link from "next/link";
import { notFound } from "next/navigation";
import { Badge, Notice, PageHeader } from "@/components/ui";
import { ClaimButton, CloseButton, ReplyForm, ResolveForm } from "@/components/case-forms";
import { loadCase, PRIORITY_LABEL } from "@/lib/cases";
import { when } from "@/lib/when";

const ROLE_LABEL: Record<string, string> = {
  AGRONOMIST: "Agronomist",
  FARMER: "Farmer",
  MANAGER: "Farm manager",
  WORKER: "Worker",
  ADMIN: "Administrator"
};

export default async function CasePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const c = await loadCase(id);
  if (!c) notFound();
  const s = c.summary;
  const closed = s.status === "CLOSED";
  const staffView = c.canClaim || c.canResolve;

  return (
    <>
      <PageHeader
        title={`${s.cropCode ?? "Crop"} question${s.county ? ` · ${s.county}` : ""}`}
        subtitle={`Asked ${when(s.createdAt)}${s.raisedByName ? ` by ${s.raisedByName}` : ""}${
          s.assignedToName ? ` · with ${s.assignedToName}` : ""
        }`}
        actions={
          <>
            <Badge value={s.status} />
            <span className="text-xs text-[var(--color-muted)]">{PRIORITY_LABEL[s.priority]}</span>
          </>
        }
      />

      {s.overdue ? (
        <div className="mb-3">
          <Notice tone="warn">This question was due an answer by {when(s.dueAt)} and has not had one.</Notice>
        </div>
      ) : null}
      {s.status === "OPEN" && !staffView ? (
        <div className="mb-3">
          <Notice>No agronomist has picked this up yet. You will see a badge in the menu when they reply.</Notice>
        </div>
      ) : null}

      <div className="grid gap-2.5 lg:grid-cols-[1fr_20rem]">
        <section>
          <div className="panel p-3">
            <h2 className="text-2xs font-semibold uppercase tracking-widest text-gray-400">The question</h2>
            <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed">{c.fullQuestion}</p>
            {c.photos.length > 0 ? (
              <div className="mt-3 flex flex-wrap gap-2">
                {c.photos.map((photo) => (
                  <a key={photo.id} href={`/api/case-photo/${c.summary.id}/${photo.id}`} target="_blank" rel="noreferrer">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img
                      src={`/api/case-photo/${c.summary.id}/${photo.id}`}
                      alt="Photograph sent with the question"
                      className="h-24 w-24 rounded-md border border-[var(--color-line)] object-cover"
                    />
                  </a>
                ))}
              </div>
            ) : null}
          </div>

          {c.messages.length > 0 ? (
            <ul className="mt-2.5 space-y-2">
              {c.messages.map((m) => (
                <li key={m.id} className="panel p-3">
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="text-xs font-medium">
                      {m.authorName ?? ROLE_LABEL[m.authorRole] ?? "Someone"}
                      <span className="ml-2 font-normal text-[var(--color-muted)]">{ROLE_LABEL[m.authorRole]}</span>
                    </span>
                    <span className="text-xs text-[var(--color-faint)]">{when(m.at)}</span>
                  </div>
                  <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed">{m.body}</p>
                </li>
              ))}
            </ul>
          ) : null}

          {c.advisoryId ? (
            <p className="mt-2 text-xs text-[var(--color-muted)]">
              A formal advisory was issued and is recorded against the plot.
            </p>
          ) : null}

          {c.canReply ? <ReplyForm id={s.id} staff={staffView} /> : null}
          {c.canResolve ? <ResolveForm id={s.id} defaultTitle={`${s.cropCode ?? "Crop"} advice`} /> : null}
        </section>

        <aside className="space-y-2.5">
          {c.aiSummary ? (
            <div className="panel p-3">
              <h2 className="text-2xs font-semibold uppercase tracking-widest text-gray-400">
                What the {s.aiSource === "MODEL" ? "vision model" : "built-in rules"} said
              </h2>
              <p className="mt-1 text-sm leading-relaxed">{c.aiSummary}</p>
              {s.aiConfidence !== null ? (
                <p className="mt-1 text-xs text-[var(--color-muted)]">
                  Confidence {Math.round(s.aiConfidence * 100)}%. Treat this as a first opinion.
                </p>
              ) : null}
            </div>
          ) : null}

          {c.similarOpen > 0 ? (
            <Notice tone="info">
              {c.similarOpen} other open {c.similarOpen === 1 ? "case" : "cases"} for {s.cropCode} in {s.county}. If it is
              the same problem, one advisory can answer them all.
            </Notice>
          ) : null}

          <div className="panel space-y-2 p-3">
            {c.canClaim ? <ClaimButton id={s.id} /> : null}
            {!closed ? <CloseButton id={s.id} /> : null}
            <Link href={staffView ? "/queue" : "/cases"} className="block text-xs underline underline-offset-2">
              Back to the list
            </Link>
          </div>
        </aside>
      </div>
    </>
  );
}
