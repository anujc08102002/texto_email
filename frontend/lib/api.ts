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

async function parseResponse<T>(response: Response): Promise<T> {
  const payload = (await response.json()) as ApiResponse<T>;

  if (!response.ok || !payload.success || payload.data === null) {
    throw new ApiClientError(
      payload.error?.message ?? "Request failed",
      response.status,
      payload.error?.code,
    );
  }

  return payload.data;
}

/** For endpoints that return success with null data (e.g. logout). */
async function parseEmpty(response: Response): Promise<void> {
  const payload = (await response.json()) as ApiResponse<unknown>;
  if (!response.ok || !payload.success) {
    throw new ApiClientError(
      payload.error?.message ?? "Request failed",
      response.status,
      payload.error?.code,
    );
  }
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: headers(),
    cache: "no-store",
  });
  return parseResponse<T>(response);
}

export async function apiPost<T>(path: string, body?: unknown, extraHeaders?: Record<string, string>): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: { ...headers("application/json"), ...extraHeaders },
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
  });
  return parseResponse<T>(response);
}

export async function apiPostEmpty(path: string, body?: unknown): Promise<void> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: headers("application/json"),
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
  });
  return parseEmpty(response);
}

export async function apiPatch<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "PATCH",
    headers: headers("application/json"),
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
  });
  return parseResponse<T>(response);
}

export async function apiDelete<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "DELETE",
    headers: headers(),
    cache: "no-store",
  });
  return parseResponse<T>(response);
}

export async function apiDeleteEmpty(path: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "DELETE",
    headers: headers(),
    cache: "no-store",
  });
  return parseEmpty(response);
}
