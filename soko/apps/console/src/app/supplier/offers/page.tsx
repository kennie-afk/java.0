import { api, describeError } from "@/lib/api";
import { Notice, PageHeader } from "@/components/ui";
import { OfferEditor, type SupplierOffer } from "@/components/offer-editor";

export default async function SupplierOffersPage() {
  let offers: SupplierOffer[] = [];
  let error: string | null = null;

  try {
    offers = await api.get<SupplierOffer[]>("/v1/supplier/offers");
  } catch (caught) {
    error = describeError(caught);
  }

  if (error) {
    return (<><PageHeader title="What I sell" /><Notice tone="danger">{error}</Notice></>);
  }

  return (
    <>
      <PageHeader
        title="What I sell"
        subtitle="Your price and how much you can cover. The distributor routes to the cheapest supplier who can still deliver in time."
      />
      <div className="space-y-3">
        {offers.length === 0 ? (
          <p className="text-[0.8125rem] text-[var(--color-muted)]">
            The distributor has not listed anything against you yet.
          </p>
        ) : (
          offers.map((offer) => <OfferEditor key={offer.id} offer={offer} />)
        )}
      </div>
    </>
  );
}
