import { Badge, EmptyState, PageHeader } from "@/components/ui";
import { api, type PageResponse } from "@/lib/api";
import { readToken } from "@/lib/session";
import type { FraudCase } from "@/lib/types";

export default async function FraudPage() {
  const token = await readToken();

  let cases: FraudCase[] = [];
  let failed = false;
  try {
    const page = await api.get<PageResponse<FraudCase>>("/api/fraud/v1/fraud-cases?size=50", token);
    cases = page.content;
  } catch {
    failed = true;
  }

  return (
    <>
      <PageHeader
        title="Fraud"
        subtitle="Cases raised by the rules engine, with evidence and payout holds."
      />
      {failed ? (
        <EmptyState message="fraud-service is not reachable." />
      ) : cases.length === 0 ? (
        <EmptyState message="No cases open." />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)]">
          <table className="w-full text-sm">
            <thead className="border-b border-[var(--color-line)] text-left text-xs uppercase tracking-wide text-[var(--color-muted)]">
              <tr>
                <th className="px-4 py-3 font-medium">Case</th>
                <th className="px-4 py-3 font-medium">Typology</th>
                <th className="px-4 py-3 font-medium">Severity</th>
                <th className="px-4 py-3 font-medium">Confidence</th>
                <th className="px-4 py-3 font-medium">Payout</th>
                <th className="px-4 py-3 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {cases.map((item) => (
                <tr key={item.id} className="border-b border-[var(--color-line)] last:border-0">
                  <td className="px-4 py-3 font-medium">{item.caseNumber}</td>
                  <td className="px-4 py-3 text-[var(--color-muted)]">{item.typology}</td>
                  <td className="px-4 py-3"><Badge value={item.severity} /></td>
                  <td className="px-4 py-3 tabular-nums">{(item.confidence * 100).toFixed(0)}%</td>
                  <td className="px-4 py-3 text-[var(--color-muted)]">
                    {item.payoutHeld ? "Held" : "Released"}
                  </td>
                  <td className="px-4 py-3"><Badge value={item.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
