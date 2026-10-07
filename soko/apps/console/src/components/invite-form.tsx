"use client";

import { useState } from "react";
import { ActionForm } from "@/components/action-form";
import { LookupPicker } from "@/components/lookup-picker";
import { Card, Field, Select, inputClass } from "@/components/ui";
import { inviteUser } from "@/app/(console)/actions";

const ROLES = [
  { value: "OPERATOR", label: "Operator: runs orders, catalogue and suppliers" },
  { value: "SUPPLIER", label: "Supplier: sees and fulfils their own order lines" },
  { value: "CUSTOMER", label: "Customer: shops and pays for their own orders" }
];

export function InviteForm() {
  const [role, setRole] = useState("OPERATOR");
  return (
    <Card>
      <ActionForm action={inviteUser} submit="Add account" pending="Adding…">
        <Field label="Full name"><input name="fullName" className={inputClass} required maxLength={200} /></Field>
        <Field label="Email address"><input name="email" type="email" className={inputClass} required /></Field>
        <Field label="Temporary password" hint="At least 10 characters. Share it privately; they can reset it from the sign-in page.">
          <input name="password" type="password" autoComplete="new-password" minLength={10} className={inputClass} required />
        </Field>
        <Select name="role" label="Role" value={role} onChange={(event) => setRole(event.target.value)}
          placeholder="Choose a role…" options={ROLES} />
        {role === "SUPPLIER" ? (
          <Field label="Supplier"><LookupPicker kind="suppliers" name="supplierId" label="Supplier" placeholder="Search suppliers…" /></Field>
        ) : null}
        {role === "CUSTOMER" ? (
          <Field label="Customer"><LookupPicker kind="customers" name="customerId" label="Customer" placeholder="Search customers…" /></Field>
        ) : null}
      </ActionForm>
    </Card>
  );
}
