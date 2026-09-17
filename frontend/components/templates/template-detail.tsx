"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { CodeBlock } from "@/components/ui/code-block";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { ApiClientError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import {
  activateTemplateVersion,
  archiveTemplate,
  getTemplate,
  listTemplateVersions,
} from "@/services/templates";
import type { Template, TemplateVersion } from "@/types/api";

export function TemplateDetail({ templateId }: { templateId: string }) {
  const [template, setTemplate] = useState<Template | null>(null);
  const [versions, setVersions] = useState<TemplateVersion[]>([]);
  const [selected, setSelected] = useState<TemplateVersion | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([getTemplate(templateId), listTemplateVersions(templateId)])
      .then(([tpl, vers]) => {
        if (cancelled) return;
        setTemplate(tpl);
        setVersions(vers);
        const current = vers.find((v) => v.id === tpl.currentVersionId) ?? vers[0] ?? null;
        setSelected(current);
        setError(null);
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof ApiClientError ? cause.message : "Unable to load template.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [templateId]);

  async function reload() {
    setLoading(true);
    setError(null);
    try {
      const [tpl, vers] = await Promise.all([getTemplate(templateId), listTemplateVersions(templateId)]);
      setTemplate(tpl);
      setVersions(vers);
      const current = vers.find((v) => v.id === tpl.currentVersionId) ?? vers[0] ?? null;
      setSelected(current);
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Unable to load template.");
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <LoadingState label="Loading template" />;
  if (error) return <ErrorState description={error} onRetry={reload} />;
  if (!template) return null;

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Workspace"
        title={template.name}
        description={`${template.slug} · current v${template.currentVersion ?? "—"}`}
        actions={
          <>
            <Button asChild variant="secondary">
              <Link href={`/templates/${template.id}/edit`}>New version</Link>
            </Button>
            <Button
              variant="ghost"
              disabled={template.status === "ARCHIVED"}
              onClick={async () => {
                try {
                  await archiveTemplate(template.id);
                  toast.success("Template archived");
                  await reload();
                } catch (cause) {
                  toast.error(cause instanceof ApiClientError ? cause.message : "Archive failed");
                }
              }}
            >
              Archive
            </Button>
          </>
        }
      />

      <div className="grid gap-4 xl:grid-cols-[0.85fr_1.15fr]">
        <SectionPanel title="Versions">
          <ul className="space-y-2">
            {versions.map((version) => {
              const active = version.id === template.currentVersionId;
              return (
                <li key={version.id}>
                  <button
                    type="button"
                    onClick={() => setSelected(version)}
                    className="flex w-full items-center justify-between rounded-lg border border-border/70 px-3 py-2 text-left hover:bg-muted/50"
                  >
                    <span className="text-sm font-medium">v{version.version}</span>
                    <div className="flex items-center gap-2">
                      {active ? <Badge variant="success">Current</Badge> : null}
                      <span className="text-caption">{formatDateTime(version.createdAt)}</span>
                    </div>
                  </button>
                  {!active ? (
                    <Button
                      size="sm"
                      variant="ghost"
                      className="mt-1"
                      onClick={async () => {
                        try {
                          await activateTemplateVersion(template.id, version.version);
                          toast.success(`Activated v${version.version}`);
                          await reload();
                        } catch (cause) {
                          toast.error(cause instanceof ApiClientError ? cause.message : "Activate failed");
                        }
                      }}
                    >
                      Activate
                    </Button>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </SectionPanel>

        <SectionPanel title={selected ? `Version ${selected.version}` : "Content"}>
          {selected ? (
            <div className="space-y-4">
              <div>
                <p className="tech-label">Subject</p>
                <p className="mt-1 text-sm">{selected.subject}</p>
              </div>
              <div>
                <p className="tech-label mb-2">HTML</p>
                <CodeBlock code={selected.htmlContent} />
              </div>
              {selected.textContent ? (
                <div>
                  <p className="tech-label mb-2">Text</p>
                  <CodeBlock code={selected.textContent} />
                </div>
              ) : null}
              <div>
                <p className="tech-label mb-2">Variables schema</p>
                <CodeBlock code={JSON.stringify(selected.variablesSchema ?? {}, null, 2)} />
              </div>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">No versions yet.</p>
          )}
        </SectionPanel>
      </div>
    </div>
  );
}
