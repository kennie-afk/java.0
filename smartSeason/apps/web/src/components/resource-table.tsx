import { Badge, EmptyState } from "@/components/ui";

export interface Column<T> {
  header: string;
  render: (row: T) => React.ReactNode;
  numeric?: boolean;
}

export function ResourceTable<T extends { id: string }>({
  rows,
  columns,
  failed,
  failureMessage,
  emptyMessage
}: {
  rows: T[];
  columns: Column<T>[];
  failed: boolean;
  failureMessage: string;
  emptyMessage: string;
}) {
  if (failed) {
    return <EmptyState message={failureMessage} />;
  }
  if (rows.length === 0) {
    return <EmptyState message={emptyMessage} />;
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)]">
      <table className="w-full text-sm">
        <thead className="border-b border-[var(--color-line)] text-left text-xs uppercase tracking-wide text-[var(--color-muted)]">
          <tr>
            {columns.map((column) => (
              <th key={column.header} className="px-4 py-3 font-medium">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id} className="border-b border-[var(--color-line)] last:border-0">
              {columns.map((column) => (
                <td
                  key={column.header}
                  className={`px-4 py-3 ${column.numeric ? "tabular-nums" : ""}`}
                >
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export { Badge };
