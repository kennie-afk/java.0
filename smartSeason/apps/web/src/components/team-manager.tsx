"use client";

import { useActionState, useEffect, useState } from "react";
import { addMember, changeRoles } from "@/lib/team-actions";
import { emptyTeamState, type Member } from "@/lib/team-types";
import { ROLE_LABELS, ROLES, type Role } from "@/lib/catalogue.generated";
import {
  Badge,
  bareInputClass,
  buttonClass,
  rowClass,
  secondaryButtonClass,
  Table
} from "@/components/ui";

const LABEL = "mb-1 block text-2xs font-medium uppercase tracking-[0.06em] text-[var(--color-faint)]";

/** Checkboxes rather than a select: most people hold one role, some hold two. */
function RoleChoice({ name, checked }: { name: Role; checked: boolean }) {
  return (
    <label className="inline-flex items-center gap-1.5 text-2xs">
      <input type="checkbox" name="roles" value={name} defaultChecked={checked} />
      {ROLE_LABELS[name]}
    </label>
  );
}

function AddMemberForm() {
  const [state, action, pending] = useActionState(addMember, emptyTeamState);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (state.ok) setOpen(false);
  }, [state.ok]);

  if (!open) {
    return (
      <button type="button" onClick={() => setOpen(true)} className={buttonClass}>
        Add someone
      </button>
    );
  }

  return (
    <form
      action={action}
      className="mb-4 rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3.5"
    >
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-xs font-semibold">Add someone</h2>
        <button
          type="button"
          onClick={() => setOpen(false)}
          className="text-2xs text-[var(--color-muted)] hover:text-[var(--color-ink)]"
        >
          Cancel
        </button>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block">
          <span className={LABEL}>Full name *</span>
          <input
            name="fullName"
            required
            defaultValue={state.values?.fullName}
            className={bareInputClass}
          />
        </label>
        <label className="block">
          <span className={LABEL}>Email *</span>
          <input
            name="email"
            type="email"
            required
            defaultValue={state.values?.email}
            className={bareInputClass}
          />
        </label>
        <label className="block">
          <span className={LABEL}>Phone</span>
          <input
            name="phone"
            type="tel"
            defaultValue={state.values?.phone}
            className={bareInputClass}
          />
        </label>
        <div />
        <label className="block">
          <span className={LABEL}>Temporary password *</span>
          <input
            name="password"
            type="password"
            required
            minLength={12}
            autoComplete="new-password"
            className={bareInputClass}
          />
          <span className="mt-1 block text-2xs text-[var(--color-muted)]">
            At least 12 characters. They can change it once they sign in.
          </span>
        </label>
        <label className="block">
          <span className={LABEL}>Confirm password *</span>
          <input
            name="confirmPassword"
            type="password"
            required
            minLength={12}
            autoComplete="new-password"
            className={bareInputClass}
          />
        </label>
      </div>

      <fieldset className="mt-3">
        <legend className={LABEL}>Roles *</legend>
        <div className="flex flex-wrap gap-x-4 gap-y-1.5">
          {ROLES.map((role) => (
            <RoleChoice key={role} name={role} checked={false} />
          ))}
        </div>
      </fieldset>

      {state.message ? (
        <p
          role="alert"
          className={`mt-3 text-2xs ${state.ok ? "text-[var(--color-good)]" : "text-[var(--color-danger)]"}`}
        >
          {state.message}
        </p>
      ) : null}

      <button type="submit" disabled={pending} className={`${buttonClass} mt-4`}>
        {pending ? "Adding…" : "Add to the team"}
      </button>
    </form>
  );
}

function RoleEditor({ member }: { member: Member }) {
  const bound = changeRoles.bind(null, member.id);
  const [state, action, pending] = useActionState(bound, emptyTeamState);
  const [editing, setEditing] = useState(false);

  // Close the editor once the save lands, so the row shows the new roles
  // rather than leaving the checkboxes open as if nothing happened.
  useEffect(() => {
    if (state.ok) setEditing(false);
  }, [state.ok]);

  if (!editing) {
    return (
      <span className="flex flex-wrap items-center gap-1.5">
        {member.roles.map((role) => (
          <Badge key={role} value={ROLE_LABELS[role as Role] ?? role} />
        ))}
        <button
          type="button"
          onClick={() => setEditing(true)}
          className="text-2xs text-[var(--color-muted)] underline-offset-2 hover:text-[var(--color-ink)] hover:underline"
        >
          Change
        </button>
        {state.message ? (
          <span className={`text-2xs ${state.ok ? "text-[var(--color-good)]" : "text-[var(--color-danger)]"}`}>
            {state.message}
          </span>
        ) : null}
      </span>
    );
  }

  return (
    <form action={action} className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
      {ROLES.map((role) => (
        <RoleChoice key={role} name={role} checked={member.roles.includes(role)} />
      ))}
      <button type="submit" disabled={pending} className={secondaryButtonClass}>
        {pending ? "Saving…" : "Save"}
      </button>
      <button
        type="button"
        onClick={() => setEditing(false)}
        className="text-2xs text-[var(--color-muted)] hover:text-[var(--color-ink)]"
      >
        Cancel
      </button>
    </form>
  );
}

export function TeamManager({
  members,
  canManage
}: {
  members: Member[];
  canManage: boolean;
}) {
  return (
    <>
      {canManage ? <AddMemberForm /> : null}

      <div className="overflow-hidden rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)]">
        <Table
          head={[
            { key: "name", label: "Name" },
            { key: "email", label: "Email" },
            { key: "phone", label: "Phone" },
            { key: "roles", label: "Roles" },
            { key: "status", label: "Status" }
          ]}
        >
          {members.map((member) => (
            <tr key={member.id} className={rowClass}>
              <td className="px-3 py-1.5 font-medium">{member.fullName}</td>
              <td className="px-3 py-1.5 text-[var(--color-muted)]">{member.email}</td>
              <td className="px-3 py-1.5 text-[var(--color-muted)]">{member.phone ?? "—"}</td>
              <td className="px-3 py-1.5">
                {canManage ? (
                  <RoleEditor member={member} />
                ) : (
                  <span className="flex flex-wrap gap-1.5">
                    {member.roles.map((role) => (
                      <Badge key={role} value={ROLE_LABELS[role as Role] ?? role} />
                    ))}
                  </span>
                )}
              </td>
              <td className="px-3 py-1.5">
                <Badge value={member.status} />
              </td>
            </tr>
          ))}
        </Table>
      </div>
    </>
  );
}
