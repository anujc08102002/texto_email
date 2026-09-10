import { NextResponse } from "next/server";
import { randomBytes } from "crypto";

export async function POST(request: Request) {
  let body: { email?: string; password?: string };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "Invalid JSON body." }, { status: 400 });
  }

  const email = String(body.email ?? "").trim().toLowerCase();
  const password = String(body.password ?? "");

  const expectedEmail = (process.env.ADMIN_EMAIL ?? "admin@texto.local").trim().toLowerCase();
  const expectedPassword = process.env.ADMIN_PASSWORD ?? "AdminTexto1!";

  if (!email || !password || email !== expectedEmail || password !== expectedPassword) {
    return NextResponse.json({ error: "Invalid admin credentials." }, { status: 401 });
  }

  const token = `adm_${randomBytes(24).toString("hex")}`;

  return NextResponse.json({
    token,
    admin: {
      email: expectedEmail,
      name: "Platform Admin",
      role: "PLATFORM_ADMIN" as const,
    },
  });
}
