import NewRecordPage from "../../../[service]/[entity]/new/page";

/**
 * The farm detail page lives at /farm/farms/[id], which would otherwise answer
 * /farm/farms/new as a farm called "new". A static segment wins, and renders the same
 * generic create form every other entity uses.
 */
export default function NewFarmPage() {
  return NewRecordPage({ params: Promise.resolve({ service: "farm", entity: "farms" }) });
}
