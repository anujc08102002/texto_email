"use client";

import { FormEvent, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { HtmlPreview } from "@/components/templates/html-preview";
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
  const availableVariables = useMemo(() => {
    try {
      const parsed = JSON.parse(schemaJson) as Record<string, unknown>;
      return Object.keys(parsed);
    } catch {
      return [];
    }
  }, [schemaJson]);

  function appendVariable(variable: string) {
    const token = `{{${variable}}}`;
    setHtmlContent((current) => `${current}${current ? " " : ""}${token}`);
    setTextContent((current) => `${current}${current ? " " : ""}${token}`);
  }

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
      <form onSubmit={onSubmit} className="email-editor-grid grid grid-cols-1 gap-6 xl:grid-cols-[1.1fr_0.9fr]">
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
              {availableVariables.length > 0 ? (
                <div className="flex flex-wrap gap-1.5 pt-1" aria-label="Insert variable">
                  {availableVariables.map((variable) => (
                    <Button
                      key={variable}
                      type="button"
                      size="sm"
                      variant="secondary"
                      className="h-7 font-mono text-xs"
                      onClick={() => appendVariable(variable)}
                    >
                      {`{{${variable}}}`}
                    </Button>
                  ))}
                </div>
              ) : null}
            </div>
            <Button type="submit" loading={submitting}>
              {mode === "create" ? "Create template" : "Create version"}
            </Button>
          </div>
        </SectionPanel>
        <SectionPanel title="Email preview" className="email-editor-preview">
          <HtmlPreview html={preview} subject={subject} />
        </SectionPanel>
      </form>
    </div>
  );
}
