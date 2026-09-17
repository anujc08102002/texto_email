"use client";

import { FormEvent, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { PageHeader } from "@/components/layout/page-header";
import { SectionPanel } from "@/components/ops/section-panel";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiClientError } from "@/lib/api";
import { createTemplate, createTemplateVersion } from "@/services/templates";

function safePreviewHtml(html: string) {
  return html.replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, "");
}

export function TemplateEditor({
  mode,
  templateId,
  initial,
}: {
  mode: "create" | "version";
  templateId?: string;
  initial?: {
    name?: string;
    description?: string;
    subject: string;
    htmlContent: string;
    textContent?: string;
    variablesSchemaJson?: string;
  };
}) {
  const router = useRouter();
  const [name, setName] = useState(initial?.name ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [subject, setSubject] = useState(initial?.subject ?? "");
  const [htmlContent, setHtmlContent] = useState(initial?.htmlContent ?? "<p>Hello {{first_name}}</p>");
  const [textContent, setTextContent] = useState(initial?.textContent ?? "Hello {{first_name}}");
  const [schemaJson, setSchemaJson] = useState(
    initial?.variablesSchemaJson ??
      JSON.stringify(
        {
          first_name: { type: "string", required: false },
        },
        null,
        2,
      ),
  );
  const [submitting, setSubmitting] = useState(false);

  const preview = useMemo(() => safePreviewHtml(htmlContent), [htmlContent]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    let variablesSchema: Record<string, unknown> | undefined;
    try {
      variablesSchema = schemaJson.trim() ? (JSON.parse(schemaJson) as Record<string, unknown>) : undefined;
    } catch {
      toast.error("Variables schema must be valid JSON");
      return;
    }

    setSubmitting(true);
    try {
      if (mode === "create") {
        const created = await createTemplate({
          name: name.trim(),
          description: description.trim() || undefined,
          subject: subject.trim(),
          htmlContent,
          textContent: textContent || undefined,
          variablesSchema,
        });
        toast.success("Template created");
        router.push(`/templates/${created.id}`);
      } else if (templateId) {
        await createTemplateVersion(templateId, {
          subject: subject.trim(),
          htmlContent,
          textContent: textContent || undefined,
          variablesSchema,
        });
        toast.success("New version created");
        router.push(`/templates/${templateId}`);
      }
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to save template.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-full flex-col gap-4">
      <PageHeader
        eyebrow="Workspace"
        title={mode === "create" ? "New template" : "New version"}
        description="Variables use {{name}} placeholders only. No expressions or server-side code."
      />
      <form onSubmit={onSubmit} className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
        <SectionPanel title="Content">
          <div className="space-y-4">
            {mode === "create" ? (
              <>
                <div className="space-y-1.5">
                  <Label htmlFor="name">Name</Label>
                  <Input id="name" value={name} onChange={(e) => setName(e.target.value)} required />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="description">Description</Label>
                  <Input id="description" value={description} onChange={(e) => setDescription(e.target.value)} />
                </div>
              </>
            ) : null}
            <div className="space-y-1.5">
              <Label htmlFor="subject">Subject</Label>
              <Input id="subject" value={subject} onChange={(e) => setSubject(e.target.value)} required />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="html">HTML</Label>
              <Textarea
                id="html"
                value={htmlContent}
                onChange={(e) => setHtmlContent(e.target.value)}
                className="min-h-56 font-mono text-xs"
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="text">Text</Label>
              <Textarea
                id="text"
                value={textContent}
                onChange={(e) => setTextContent(e.target.value)}
                className="min-h-28 font-mono text-xs"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="schema">Variables schema (JSON)</Label>
              <Textarea
                id="schema"
                value={schemaJson}
                onChange={(e) => setSchemaJson(e.target.value)}
                className="min-h-36 font-mono text-xs"
              />
            </div>
            <Button type="submit" loading={submitting}>
              {mode === "create" ? "Create template" : "Create version"}
            </Button>
          </div>
        </SectionPanel>
        <SectionPanel title="Sandboxed preview" description="Scripts stripped · iframe sandbox without scripts">
          <iframe
            title="Template preview"
            sandbox=""
            srcDoc={preview}
            className="h-[28rem] w-full rounded-lg border border-border/80 bg-white"
          />
        </SectionPanel>
      </form>
    </div>
  );
}
