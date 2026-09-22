import { NextResponse } from "next/server";

const API_BASE_URL = process.env.API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function POST(request: Request) {
  let body: { email?: string; password?: string };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "Invalid JSON body." }, { status: 400 });
  }

  const email = String(body.email ?? "").trim().toLowerCase();
  const password = String(body.password ?? "");
  if (!email || !password) return NextResponse.json({ error: "Email and password are required." }, { status: 400 });

  try {
    const upstream = await fetch(`${API_BASE_URL}/api/v1/admin/auth/login`, {
      method: "POST",
      cache: "no-store",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ email, password }),
    });
    const payload = await upstream.json() as { success?: boolean; data?: { token: string; admin: { email: string; name: string; role: "PLATFORM_ADMIN" } }; error?: { message?: string } };
    if (!upstream.ok || !payload.success || !payload.data) {
      return NextResponse.json({ error: payload.error?.message ?? "Invalid admin credentials." }, { status: upstream.status || 401 });
    }
    return NextResponse.json(payload.data);
  } catch {
    return NextResponse.json({ error: "Unable to reach the operations API." }, { status: 502 });
  }
}
