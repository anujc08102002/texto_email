"use client";

import { toast } from "sonner";

/** Preview-only admin action helper until /api/v1/admin exists */
export function previewAdminAction(action: string, detail?: string) {
  toast.message("Admin action queued (preview)", {
    description: detail ? `${action} · ${detail}` : `${action} · POST /api/v1/admin when available`,
  });
}
