import { api, describeError } from "@/lib/api";
import { Badge, Notice, PageHeader, Table, rowClass } from "@/components/ui";
import type { SupplierRow } from "@/lib/types";

export default async function SuppliersPage() {
  let suppliers: SupplierRow[] = [];
  let error: string | null = null;

  try {
    suppliers = await api.get<SupplierRow[]>("/v1/suppliers");
  } catch (caught) {
    error = describeError(caught);
  }

  if (error) {
    return (<><PageHeader title="Suppliers" /><Notice tone="danger">{error}</Notice></>);
  }

  return (
    <>
      <PageHeader title="Suppliers"
        subtitle="Lead time and cold chain decide what each supplier is allowed to fulfil." />
      <Table head={["Supplier", "County", "Lead time", "Cold chain", "Reliability", "Status"]}>
        {suppliers.map((supplier) => (
          <tr key={supplier.id} className={rowClass}>
            <td className="px-4 py-3 font-medium">{supplier.name}</td>
            <td className="px-4 py-3 text-[var(--color-muted)]">{supplier.county}</td>
            <td className="px-4 py-3 tabular-nums">{supplier.leadTimeHours} h</td>
            <td className="px-4 py-3">
              {supplier.coldChain ? <Badge value="Chilled" /> : <Badge value="Ambient" />}
            </td>
            <td className="px-4 py-3 tabular-nums">{Math.round(Number(supplier.reliability) * 100)}%</td>
            <td className="px-4 py-3"><Badge value={supplier.status} /></td>
          </tr>
        ))}
      </Table>
    </>
  );
}
