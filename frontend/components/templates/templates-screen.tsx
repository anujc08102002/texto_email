"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { PageHeader } from "@/components/layout/page-header";
import { UpgradeHint } from "@/components/billing/upgrade-hint";
import { TemplatesList } from "@/components/templates/templates-list";
import { SectionPanel } from "@/components/ops/section-panel";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { ApiClientError } from "@/lib/api";
import { getEntitlements } from "@/services/platform";
import { listTemplates } from "@/services/templates";
import type { Template } from "@/types/api";

export function TemplatesScreen() {
  const [templates, setTemplates] = useState<Template[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [allowed, setAllowed] = useState(true);

  function reload() {
    setLoading(true);
    setError(null);
    Promise.all([listTemplates(), getEntitlements().catch(() => null)])
      .then(([data, entitlements]) => {
        setTemplates(data);
        if (entitlements) {
          setAllowed(entitlements.features.TEMPLATES !== false);
        }
      })
      .catch((cause) => {
        setError(cause instanceof ApiClientError ? cause.message : "Unable to load templates.");
      })
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    let cancelled = false;
    Promise.all([listTemplates(), getEntitlements().catch(() => null)])
      .then(([data, entitlements]) => {
        if (cancelled) return;
        setTemplates(data);
        if (entitlements) {
          setAllowed(entitlements.features.TEMPLATES !== false);
        }
        setError(null);
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiClientError ? cause.message : "Unable to load templates.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Email"
        title="Templates"
        description="Reusable HTML and text templates for transactional sends."
        actions={
          allowed ? (
            <Button asChild>
              <Link href="/templates/new">New template</Link>
            </Button>
          ) : (
            <Button asChild variant="secondary">
              <Link href="/billing">Upgrade to create</Link>
            </Button>
          )
        }
      />

      {!allowed ? <UpgradeHint feature="Templates" /> : null}

      <SectionPanel>
        {loading ? <LoadingState label="Loading templates" /> : null}
        {error ? <ErrorState description={error} onRetry={reload} /> : null}
        {!loading && !error ? <TemplatesList templates={templates} /> : null}
      </SectionPanel>
    </div>
  );
}
