"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { HtmlPreview } from "@/components/templates/html-preview";
import { SectionPanel } from "@/components/ops/section-panel";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LoadingState } from "@/components/ui/loading-state";
import { Textarea } from "@/components/ui/textarea";
import { ApiClientError } from "@/lib/api";
import {
  createTemplateVersion,
  getTemplate,
  getTemplateVersion,
  listTemplateVersions,
} from "@/services/templates";
import type { Template, TemplateVariableSchema, TemplateVersion } from "@/types/api";

function parseVariablesSchema(raw: string): TemplateVariableSchema | undefined {
  const trimmed = raw.trim();
  if (!trimmed) return undefined;
  const parsed = JSON.parse(trimmed) as unknown;
  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("Variables schema must be a JSON object.");
  }
  return parsed as TemplateVariableSchema;
}

export function TemplateVersionEditor({ templateId }: { templateId: string }) {
  const router = useRouter();
  const [template, setTemplate] = useState<Template | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [subject, setSubject] = useState("");
  const [htmlContent, setHtmlContent] = useState("");
  const [textContent, setTextContent] = useState("");
  const [variablesJson, setVariablesJson] = useState("{}");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.all([getTemplate(templateId), listTemplateVersions(templateId)])
      .then(async ([tpl, versions]) => {
        if (cancelled) return;
        let current: TemplateVersion | undefined;
        if (tpl.currentVersion != null) {
          current = versions.find((v) => v.version === tpl.currentVersion);
          if (!current) {
            current = await getTemplateVersion(templateId, tpl.currentVersion);
          }
        } else {
          current = versions[0];
        }
        if (cancelled) return;
        setTemplate(tpl);
        setSubject(current?.subject ?? "");
        setHtmlContent(current?.htmlContent ?? "");
        setTextContent(current?.textContent ?? "");
        setVariablesJson(JSON.stringify(current?.variablesSchema ?? {}, null, 2));
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

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      let variablesSchema: TemplateVariableSchema | undefined;
      try {
        variablesSchema = parseVariablesSchema(variablesJson);
      } catch (cause) {
        toast.error(cause instanceof Error ? cause.message : "Invalid variables schema JSON.");
        return;
      }

      await createTemplateVersion(templateId, {
        subject: subject.trim(),
        htmlContent,
        textContent: textContent.trim() || undefined,
        variablesSchema,
      });
      toast.success("New version created");
      router.push(`/templates/${templateId}`);
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to create version.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <LoadingState label="Loading template" />;
  if (error) return <ErrorState description={error} />;
  if (!template) return null;

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Editing from current content of <span className="font-medium text-foreground">{template.name}</span>. Saving
        creates a new immutable version.
      </p>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,0.95fr)]">
        <div className="space-y-4">
          <SectionPanel title="Subject">
            <div className="space-y-1.5">
              <Label htmlFor="version-subject">Subject</Label>
              <Input
                id="version-subject"
                value={subject}
                onChange={(event) => setSubject(event.target.value)}
                required
              />
            </div>
          </SectionPanel>

          <SectionPanel title="HTML content">
            <Textarea
              value={htmlContent}
              onChange={(event) => setHtmlContent(event.target.value)}
              className="min-h-[280px] font-mono text-xs leading-5"
              required
              spellCheck={false}
            />
          </SectionPanel>

          <SectionPanel title="Text content">
            <Textarea
              value={textContent}
              onChange={(event) => setTextContent(event.target.value)}
              className="min-h-28 font-mono text-xs"
              spellCheck={false}
            />
          </SectionPanel>

          <SectionPanel title="Variables schema">
            <Textarea
              value={variablesJson}
              onChange={(event) => setVariablesJson(event.target.value)}
              className="min-h-36 font-mono text-xs"
              spellCheck={false}
            />
          </SectionPanel>
        </div>

        <SectionPanel title="Preview" description="Sandboxed iframe · scripts disabled">
          <HtmlPreview html={htmlContent} />
        </SectionPanel>
      </div>

      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={() => router.push(`/templates/${templateId}`)}>
          Cancel
        </Button>
        <Button type="submit" loading={submitting} disabled={!subject.trim() || !htmlContent.trim()}>
          Create version
        </Button>
      </div>
    </form>
  );
}
