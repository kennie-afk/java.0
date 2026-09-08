"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { readToken } from "@/lib/session";
import type { Member, TeamState } from "@/lib/team-types";

const TEAM = "/api/identity/v1/account/team";

export async function loadTeam(): Promise<{ members: Member[]; failed: boolean }> {
  const token = await readToken();
  if (!token) {
    redirect("/login");
  }
  try {
    return { members: await api.get<Member[]>(TEAM, token), failed: false };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      redirect("/login");
    }
    return { members: [], failed: true };
  }
}

function failure(error: unknown, values: Record<string, string>): TeamState {
  if (error instanceof ApiError) {
    return { ok: false, message: error.problem?.detail ?? "That request was rejected", values };
  }
  return { ok: false, message: "The platform is not reachable right now", values };
}

export async function addMember(_prev: TeamState, form: FormData): Promise<TeamState> {
  const token = await readToken();
  if (!token) {
    redirect("/login");
  }

  const values = {
    fullName: String(form.get("fullName") ?? "").trim(),
    email: String(form.get("email") ?? "").trim(),
    phone: String(form.get("phone") ?? "").trim()
  };
  const roles = form.getAll("roles").map(String).filter(Boolean);
  const password = String(form.get("password") ?? "");

  if (roles.length === 0) {
    return { ok: false, message: "Choose at least one role", values };
  }
  if (password !== String(form.get("confirmPassword") ?? "")) {
    return { ok: false, message: "The two passwords do not match", values };
  }

  try {
    await api.post(
      TEAM,
      { ...values, phone: values.phone || null, roles, password },
      token
    );
  } catch (error) {
    return failure(error, values);
  }

  revalidatePath("/team");
  return { ok: true, message: `${values.fullName} can now sign in.` };
}

export async function changeRoles(
  userId: string,
  _prev: TeamState,
  form: FormData
): Promise<TeamState> {
  const token = await readToken();
  if (!token) {
    redirect("/login");
  }

  const roles = form.getAll("roles").map(String).filter(Boolean);
  if (roles.length === 0) {
    return { ok: false, message: "A person needs at least one role" };
  }

  try {
    await api.patch(`${TEAM}/${userId}/roles`, { roles }, token);
  } catch (error) {
    return failure(error, {});
  }

  revalidatePath("/team");
  return { ok: true, message: "Roles updated." };
}
