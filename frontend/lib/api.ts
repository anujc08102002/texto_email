import { getToken } from "@/lib/session";
import type { ApiResponse } from "@/types/api";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export class ApiClientError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(message);
    this.name = "ApiClientError";
  }
}

function headers(contentType?: string): HeadersInit {
  const result: Record<string, string> = {
    Accept: "application/json",
  };
  if (contentType) {
    result["Content-Type"] = contentType;
  }
  const token = getToken();
  if (token) {
    result.Authorization = `Bearer ${token}`;
  }
  return result;
}

async function readPayload<T>(response: Response): Promise<ApiResponse<T>> {
  const raw = await response.text();
  try {
    return JSON.parse(raw) as ApiResponse<T>;
  } catch {
    throw new ApiClientError(
      raw?.trim() ? `API error (${response.status})` : "Unable to reach the API.",
      response.status || 0,
    );
  }
}

function errorMessage(payload: ApiResponse<unknown>, fallback: string): string {
  const message = payload.error?.message ?? fallback;
  const details = payload.error?.details;
  if (!details?.length) {
    return message;
  }
  const extra = details
    .map((detail) => (detail.field ? `${detail.field}: ${detail.message}` : detail.message))
    .filter(Boolean)
    .join("; ");
  return extra ? `${message} (${extra})` : message;
}

async function parseResponse<T>(response: Response): Promise<T> {
  const payload = await readPayload<T>(response);

  if (!response.ok || !payload.success || payload.data === null) {
    throw new ApiClientError(errorMessage(payload, "Request failed"), response.status, payload.error?.code);
  }

  return payload.data;
}

/** For endpoints that return success with null data (e.g. logout). */
async function parseEmpty(response: Response): Promise<void> {
  const payload = await readPayload<unknown>(response);
  if (!response.ok || !payload.success) {
    throw new ApiClientError(errorMessage(payload, "Request failed"), response.status, payload.error?.code);
  }
}

async function request(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(`${API_BASE_URL}${path}`, { ...init, cache: "no-store" });
  } catch {
    throw new ApiClientError("Unable to reach the API.", 0);
  }
}

export async function apiGet<T>(path: string): Promise<T> {
  return parseResponse<T>(await request(path, { headers: headers() }));
}

export async function apiPost<T>(path: string, body?: unknown, extraHeaders?: Record<string, string>): Promise<T> {
  return parseResponse<T>(
    await request(path, {
      method: "POST",
      headers: { ...headers("application/json"), ...extraHeaders },
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  );
}

export async function apiPostEmpty(path: string, body?: unknown): Promise<void> {
  await parseEmpty(
    await request(path, {
      method: "POST",
      headers: headers("application/json"),
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  );
}

export async function apiPatch<T>(path: string, body?: unknown): Promise<T> {
  return parseResponse<T>(
    await request(path, {
      method: "PATCH",
      headers: headers("application/json"),
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  );
}

export async function apiDelete<T>(path: string): Promise<T> {
  return parseResponse<T>(await request(path, { method: "DELETE", headers: headers() }));
}

export async function apiDeleteEmpty(path: string): Promise<void> {
  await parseEmpty(await request(path, { method: "DELETE", headers: headers() }));
}
