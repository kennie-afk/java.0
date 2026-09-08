"use server";

import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { api, ApiError } from "@/lib/api";
import { findEntity, type FormFieldSpec } from "@/lib/catalogue.generated";
import { readTenantId, readToken } from "@/lib/session";
import type { FormState } from "@/lib/form-state";

/** Fields the caller never supplies: the server derives them from the token. */
const DERIVED_FROM_TOKEN: Record<string, "tenant"> = {
  sellerOrgId: "tenant",
  buyerOrgId: "tenant",
  organisationId: "tenant"
};

function coerce(spec: FormFieldSpec, raw: FormDataEntryValue | null): unknown {
  if (spec.kind === "bool") {
    return raw === "true";
  }
  if (typeof raw !== "string" || raw.trim() === "") {
    return null;
  }
  const value = raw.trim();

  switch (spec.kind) {
    case "int":
    case "long": {
      const parsed = Number.parseInt(value, 10);
      return Number.isFinite(parsed) ? parsed : null;
    }
    case "decimal": {
      const parsed = Number(value);
      return Number.isFinite(parsed) ? parsed : null;
    }
    case "ts": {
      // <input type="datetime-local"> yields local wall-clock time with no zone.
      const parsed = new Date(value);
      return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
    }
    default:
      return value;
  }
}

async function buildBody(fields: FormFieldSpec[], form: FormData): Promise<Record<string, unknown>> {
  const tenantId = await readTenantId();
  const body: Record<string, unknown> = {};

  for (const spec of fields) {
    if (DERIVED_FROM_TOKEN[spec.name] === "tenant" && tenantId) {
      body[spec.name] = tenantId;
      continue;
    }
    const value = coerce(spec, form.get(spec.name));
    if (value !== null) {
      body[spec.name] = value;
    }
  }
  return body;
}

/** Everything the user typed, so a failed save can redisplay it. */
function submitted(form: FormData): Record<string, string> {
  const values: Record<string, string> = {};
  for (const [key, value] of form.entries()) {
    if (typeof value === "string") {
      values[key] = value;
    }
  }
  return values;
}

function failure(error: unknown, form: FormData): FormState {
  const values = submitted(form);
  if (error instanceof ApiError) {
    return {
      ok: false,
      message: error.problem?.detail ?? "The request was rejected",
      fieldErrors: error.problem?.errors ?? {},
      values
    };
  }
  return {
    ok: false,
    message: "The platform is not reachable right now",
    fieldErrors: {},
    values
  };
}

export async function createRecord(
  serviceSlug: string,
  entitySlug: string,
  _prev: FormState,
  form: FormData
): Promise<FormState> {
  const found = findEntity(serviceSlug, entitySlug);
  if (!found) {
    return { ok: false, message: "Unknown record type", fieldErrors: {} };
  }

  const token = await readToken();
  if (!token) {
    redirect("/login");
  }

  const list = `/${serviceSlug}/${entitySlug}`;
  try {
    await api.post(found.entity.path, await buildBody(found.entity.formFields, form), token);
  } catch (error) {
    return failure(error, form);
  }

  // Outside the try: redirect() signals by throwing, and catching it here would
  // turn a successful save into "the request was rejected".
  revalidatePath(list);
  redirect(list);
}

export async function updateRecord(
  serviceSlug: string,
  entitySlug: string,
  id: string,
  _prev: FormState,
  form: FormData
): Promise<FormState> {
  const found = findEntity(serviceSlug, entitySlug);
  if (!found) {
    return { ok: false, message: "Unknown record type", fieldErrors: {} };
  }

  const token = await readToken();
  if (!token) {
    redirect("/login");
  }

  const record = `/${serviceSlug}/${entitySlug}/${id}`;
  try {
    await api.patch(
      `${found.entity.path}/${id}`,
      await buildBody(found.entity.formFields, form),
      token
    );
  } catch (error) {
    return failure(error, form);
  }

  revalidatePath(record);
  redirect(record);
}

export async function deleteRecord(
  serviceSlug: string,
  entitySlug: string,
  id: string
): Promise<FormState> {
  const found = findEntity(serviceSlug, entitySlug);
  if (!found) {
    return { ok: false, message: "Unknown record type", fieldErrors: {} };
  }

  const token = await readToken();
  if (!token) {
    redirect("/login");
  }

  const list = `/${serviceSlug}/${entitySlug}`;
  try {
    await api.delete(`${found.entity.path}/${id}`, token);
  } catch (error) {
    // Delete has no form to echo back, so it reports the reason only.
    return failure(error, new FormData());
  }

  revalidatePath(list);
  redirect(list);
}
