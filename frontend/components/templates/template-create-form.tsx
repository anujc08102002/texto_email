"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { HtmlPreview } from "@/components/templates/html-preview";
import { SectionPanel } from "@/components/ops/section-panel";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiClientError } from "@/lib/api";
import { createTemplate } from "@/services/templates";
import type { TemplateVariableSchema } from "@/types/api";

function parseVariablesSchema(raw: string): TemplateVariableSchema | undefined {
  const trimmed = raw.trim();
  if (!trimmed) return undefined;
  const parsed = JSON.parse(trimmed) as unknown;
  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("Variables schema must be a JSON object.");
  }
  return parsed as TemplateVariableSchema;
}

export function TemplateCreateForm() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [subject, setSubject] = useState("");
  const [htmlContent, setHtmlContent] = useState(
    "<!DOCTYPE html>\n<html>\n<body>\n  <h1>Hello {{name}}</h1>\n</body>\n</html>",
  );
  const [textContent, setTextContent] = useState("Hello {{name}}");
  const [variablesJson, setVariablesJson] = useState(
    '{\n  "name": { "type": "string", "required": true }\n}',
  );
  const [submitting, setSubmitting] = useState(false);

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

      const created = await createTemplate({
        name: name.trim(),
        description: description.trim() || undefined,
        subject: subject.trim(),
        htmlContent,
        textContent: textContent.trim() || undefined,
        variablesSchema,
      });
      toast.success("Template created");
      router.push(`/templates/${created.id}`);
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to create template.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,0.95fr)]">
        <div className="space-y-4">
          <SectionPanel title="Template" description="Metadata and subject line">
            <div className="space-y-3">
              <div className="space-y-1.5">
                <Label htmlFor="template-name">Name</Label>
                <Input
                  id="template-name"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder="Welcome email"
                  required
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="template-description">Description</Label>
                <Input
                  id="template-description"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  placeholder="Optional"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="template-subject">Subject</Label>
                <Input
                  id="template-subject"
                  value={subject}
                  onChange={(event) => setSubject(event.target.value)}
                  placeholder="Welcome, {{name}}"
                  required
                />
              </div>
            </div>
          </SectionPanel>

          <SectionPanel title="HTML content" description="Monospace editor · scripts will not run in preview">
            <Textarea
              value={htmlContent}
              onChange={(event) => setHtmlContent(event.target.value)}
              className="min-h-[280px] font-mono text-xs leading-5"
              required
              spellCheck={false}
            />
          </SectionPanel>

          <SectionPanel title="Text content" description="Plain-text fallback">
            <Textarea
              value={textContent}
              onChange={(event) => setTextContent(event.target.value)}
              className="min-h-28 font-mono text-xs"
              spellCheck={false}
            />
          </SectionPanel>

          <SectionPanel title="Variables schema" description="JSON object describing merge fields">
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
        <Button type="button" variant="secondary" onClick={() => router.push("/templates")}>
          Cancel
        </Button>
        <Button
          type="submit"
          loading={submitting}
          disabled={!name.trim() || !subject.trim() || !htmlContent.trim()}
        >
          Create template
        </Button>
      </div>
    </form>
  );
}
