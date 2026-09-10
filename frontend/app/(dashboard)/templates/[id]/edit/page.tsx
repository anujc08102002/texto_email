"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { TemplateEditor } from "@/components/templates/template-editor";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { ApiClientError } from "@/lib/api";
import { getTemplate, listTemplateVersions } from "@/services/templates";

export default function EditTemplatePage() {
  const params = useParams<{ id: string }>();
  const templateId = params.id;
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [initial, setInitial] = useState<{
    subject: string;
    htmlContent: string;
    textContent?: string;
    variablesSchemaJson?: string;
  } | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([getTemplate(templateId), listTemplateVersions(templateId)])
      .then(([template, versions]) => {
        if (cancelled) return;
        const current = versions.find((v) => v.id === template.currentVersionId) ?? versions[0];
        if (!current) {
          setError("Template has no versions to edit.");
          return;
        }
        setInitial({
          subject: current.subject,
          htmlContent: current.htmlContent,
          textContent: current.textContent ?? undefined,
          variablesSchemaJson: JSON.stringify(current.variablesSchema ?? {}, null, 2),
        });
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

  if (loading) return <LoadingState label="Loading template" />;
  if (error || !initial) return <ErrorState description={error ?? "Unavailable"} />;
  return <TemplateEditor mode="version" templateId={templateId} initial={initial} />;
}
