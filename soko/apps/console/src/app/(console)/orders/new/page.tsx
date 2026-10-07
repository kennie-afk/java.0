import Link from "next/link";
import { Card, PageHeader, secondaryButtonClass } from "@/components/ui";
import { OrderForm } from "@/components/order-form";

export default function NewOrderPage() {
  return (
    <>
      <PageHeader title="New order" subtitle="Each line goes to the cheapest supplier that can keep the cold chain and deliver inside the shelf life. A line nobody can fill is refused with the reason."
        actions={<Link href="/orders" className={secondaryButtonClass}>Back</Link>} />
      <div className="max-w-2xl">
        <Card>
          <OrderForm />
        </Card>
      </div>
    </>
  );
}
