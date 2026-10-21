"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, Eye, Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import DataGrid from "@/components/data-grid/DataGrid";
import { ErrorState } from "@/components/ui/error-state";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { LoadingState } from "@/components/ui/loading-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ApiClientError } from "@/lib/api";
import {
  createSegment,
  updateSegment,
  deleteSegment,
  listSegments,
  previewSavedSegment,
  previewSegment,
  type Segment,
  type SegmentPreview,
} from "@/services/segments";

type ConditionType = "TEXT" | "NUMBER" | "DATE";
type Condition = {
  id: string;
  field: string;
  type: ConditionType;
  operator: string;
  value: string;
  endValue: string;
  customField: string;
};
type ConditionGroup = {
  id: string;
  operator: "AND" | "OR";
  conditions: Condition[];
};
type SegmentRule = Condition | ConditionGroup;
type Definition = { operator: "AND" | "OR"; conditions: SegmentRule[] };

const contactFields: { value: string; label: string }[] = [
  { value: "email", label: "Email" },
  { value: "first_name", label: "First name" },
  { value: "last_name", label: "Last name" },
  { value: "company", label: "Company" },
  { value: "phone", label: "Phone" },
  { value: "status", label: "Contact status" },
  { value: "subscription_status", label: "Subscription status" },
  { value: "source", label: "Source" },
  { value: "created_at", label: "Created date" },
  { value: "updated_at", label: "Updated date" },
];

const operators: Record<ConditionType, { value: string; label: string }[]> = {
  TEXT: [
    { value: "EQUALS", label: "Equals" },
    { value: "NOT_EQUALS", label: "Does not equal" },
    { value: "CONTAINS", label: "Contains" },
    { value: "STARTS_WITH", label: "Starts with" },
    { value: "ENDS_WITH", label: "Ends with" },
    { value: "IS_EMPTY", label: "Is empty" },
    { value: "IS_NOT_EMPTY", label: "Is not empty" },
  ],
  NUMBER: [
    { value: "EQUALS", label: "Equals" },
    { value: "NOT_EQUALS", label: "Does not equal" },
    { value: "GREATER_THAN", label: "Greater than" },
    { value: "GREATER_THAN_OR_EQUAL", label: "At least" },
    { value: "LESS_THAN", label: "Less than" },
    { value: "LESS_THAN_OR_EQUAL", label: "At most" },
    { value: "BETWEEN", label: "Between" },
    { value: "IS_EMPTY", label: "Is empty" },
    { value: "IS_NOT_EMPTY", label: "Is not empty" },
  ],
  DATE: [
    { value: "EQUALS", label: "Is on" },
    { value: "NOT_EQUALS", label: "Is not on" },
    { value: "GREATER_THAN", label: "After" },
    { value: "LESS_THAN", label: "Before" },
    { value: "BETWEEN", label: "Between" },
    { value: "IS_EMPTY", label: "Is empty" },
    { value: "IS_NOT_EMPTY", label: "Is not empty" },
  ],
};

function makeCondition(): Condition {
  return {
    id: crypto.randomUUID(),
    field: "email",
    type: "TEXT",
    operator: "EQUALS",
    value: "",
    endValue: "",
    customField: "",
  };
}

function makeDefinition(): Definition {
  return { operator: "AND", conditions: [makeCondition()] };
}

function isGroup(rule: SegmentRule): rule is ConditionGroup {
  return "conditions" in rule;
}

function serializeCondition(condition: Condition) {
  return {
    field: condition.field === "__custom"
      ? `custom.${condition.customField.trim()}`
      : condition.field,
    type: condition.type,
    operator: condition.operator,
    value: condition.operator === "BETWEEN"
      ? [condition.value, condition.endValue]
      : condition.value,
  };
}

function serializeDefinition(definition: Definition) {
  return {
    operator: definition.operator,
    conditions: definition.conditions.map((rule) =>
      isGroup(rule)
        ? { operator: rule.operator, conditions: rule.conditions.map(serializeCondition) }
        : serializeCondition(rule),
    ),
  };
}

function deserializeDefinition(source: string): Definition {
  const parsed = JSON.parse(source);
  function condition(value: { field: string; type?: ConditionType; operator: string; value?: string | string[] }): Condition {
    if (!value.field) throw new Error("Nested groups require the advanced editor");
    const custom = value.field.startsWith("custom.") || value.field.startsWith("properties.");
    if (value.value && typeof value.value === "object" && !Array.isArray(value.value)) throw new Error("Advanced bounds");
    const type = value.type ?? "TEXT";
    const normalized = value.operator.replaceAll("_", "").toUpperCase();
    const operator = operators[type]?.find((item) => item.value.replaceAll("_", "") === normalized)?.value;
    const field = custom ? "__custom" : contactFields.find((item) => item.value.replaceAll("_", "") === value.field.replaceAll("_", "").toLowerCase())?.value;
    if (!operator || !field) throw new Error("Advanced condition");
    return { id: crypto.randomUUID(), field,
      customField: custom ? value.field.slice(value.field.indexOf(".") + 1) : "",
      type, operator,
      value: Array.isArray(value.value) ? String(value.value[0] ?? "") : String(value.value ?? ""),
      endValue: Array.isArray(value.value) ? String(value.value[1] ?? "") : "" };
  }
  return { operator: parsed.operator, conditions: parsed.conditions.map((value: { field: string; type?: ConditionType; operator: string; value?: string; conditions?: Parameters<typeof condition>[0][] }) =>
    value.conditions ? { id: crypto.randomUUID(), operator: value.operator, conditions: value.conditions.map(condition) } : condition(value)) };
}

function conditionIsValid(condition: Condition) {
  if (condition.field === "__custom" && !condition.customField.trim()) return false;
  if (condition.operator === "IS_EMPTY" || condition.operator === "IS_NOT_EMPTY") return true;
  if (!condition.value.trim()) return false;
  return condition.operator !== "BETWEEN" || Boolean(condition.endValue.trim());
}

function definitionIsValid(definition: Definition) {
  if (definition.conditions.length === 0) return false;
  return definition.conditions.every((rule) =>
    isGroup(rule)
      ? rule.conditions.length > 0 && rule.conditions.every(conditionIsValid)
      : conditionIsValid(rule),
  );
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "-" : date.toLocaleDateString();
}

export function SegmentsWorkspace() {
  const [segments, setSegments] = useState<Segment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [builderOpen, setBuilderOpen] = useState(false);
  const [name, setName] = useState("");
  const [definition, setDefinition] = useState<Definition>(makeDefinition);
  const [editing, setEditing] = useState<Segment | null>(null);
  const [rawMode, setRawMode] = useState(false);
  const [rawDefinition, setRawDefinition] = useState("");
  const previewRequest = useRef<((page: number) => Promise<SegmentPreview>) | null>(null);
  const [saving, setSaving] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewName, setPreviewName] = useState("");
  const [preview, setPreview] = useState<SegmentPreview | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSegments(await listSegments());
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : "Unable to load segments.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  function updateCondition(id: string, change: Partial<Condition>) {
    setDefinition((current) => ({
      ...current,
      conditions: current.conditions.map((rule) =>
        isGroup(rule)
          ? { ...rule, conditions: rule.conditions.map((condition) => condition.id === id ? { ...condition, ...change } : condition) }
          : rule.id === id ? { ...rule, ...change } : rule,
      ),
    }));
  }

  function removeRule(id: string) {
    setDefinition((current) => ({
      ...current,
      conditions: current.conditions
        .filter((rule) => rule.id !== id)
        .map((rule) => isGroup(rule)
          ? { ...rule, conditions: rule.conditions.filter((condition) => condition.id !== id) }
          : rule),
    }));
  }

  function addGroup() {
    setDefinition((current) => ({
      ...current,
      conditions: [...current.conditions, { id: crypto.randomUUID(), operator: "AND", conditions: [makeCondition()] }],
    }));
  }

  function addGroupCondition(groupId: string) {
    setDefinition((current) => ({
      ...current,
      conditions: current.conditions.map((rule) =>
        isGroup(rule) && rule.id === groupId
          ? { ...rule, conditions: [...rule.conditions, makeCondition()] }
          : rule,
      ),
    }));
  }

  async function save() {
    if (!name.trim() || (!rawMode && !definitionIsValid(definition))) return;
    setSaving(true);
    try {
      if (editing) await updateSegment(editing.id, name.trim(), rawMode ? rawDefinition : JSON.stringify(serializeDefinition(definition)));
      else await createSegment(name.trim(), JSON.stringify(serializeDefinition(definition)));
      toast.success("Segment saved");
      setBuilderOpen(false);
      setName("");
      setDefinition(makeDefinition());
      await load();
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to save segment.");
    } finally {
      setSaving(false);
    }
  }

  async function remove(segment: Segment) {
    try {
      await deleteSegment(segment.id);
      toast.success("Segment deleted");
      await load();
    } catch (cause) {
      toast.error(cause instanceof ApiClientError ? cause.message : "Unable to delete segment.");
    }
  }

  async function runPreview(segmentName: string, request: (page: number) => Promise<SegmentPreview>, page = 0) {
    previewRequest.current = request;
    setPreviewName(segmentName);
    setPreview(null);
    setPreviewError(null);
    setPreviewLoading(true);
    setPreviewOpen(true);
    try {
      setPreview(await request(page));
    } catch (cause) {
      setPreviewError(cause instanceof ApiClientError ? cause.message : "Unable to preview segment.");
    } finally {
      setPreviewLoading(false);
    }
  }

  const canSave = name.trim().length > 0 && (rawMode ? rawDefinition.trim().length > 0 : definitionIsValid(definition));

  return (
    <div className="space-y-4">
      {error ? <ErrorState description={error} onRetry={() => void load()} /> : null}
      <DataGrid pageName="email-segments" rows={segments} rowKey={(segment) => segment.id}
        loading={loading} entityName="Segments" totalCountLabel={segments.length} searchPlaceholder="Search segments"
        emptyTitle="No segments found" emptyMessage="Create a segment to preview a reusable contact audience."
        toolbarActions={<Button onClick={() => { setEditing(null); setRawMode(false); setName(""); setDefinition(makeDefinition()); setBuilderOpen(true); }}><Plus className="size-4" />New segment</Button>}
        columns={[
          { key: "name", label: "Name", fixed: true, sortable: true, filterable: true },
          { key: "updatedAt", label: "Updated", sortable: true, sortType: "date", render: (segment) => formatDate(segment.updatedAt) },
          { key: "actions", label: "Actions", fixed: true, render: (segment) => <div className="flex gap-2">
            <Button size="icon" variant="outline" aria-label={`Preview ${segment.name}`} onClick={() => void runPreview(segment.name, (page) => previewSavedSegment(segment.id, page))}><Eye className="size-4" /></Button>
            <Button size="icon" variant="outline" aria-label={`Edit ${segment.name}`} onClick={() => { setEditing(segment); setName(segment.name); setRawDefinition(JSON.stringify(JSON.parse(segment.definition), null, 2)); try { setDefinition(deserializeDefinition(segment.definition)); setRawMode(false); } catch { setRawMode(true); } setBuilderOpen(true); }}><Pencil className="size-4" /></Button>
            <Button size="icon" variant="outline" aria-label={`Delete ${segment.name}`} onClick={() => void remove(segment)}><Trash2 className="size-4" /></Button>
          </div> },
        ]}
      />

      <Dialog open={builderOpen} onOpenChange={setBuilderOpen}>
        <DialogContent className="max-h-[90vh] max-w-5xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editing ? "Edit segment" : "Create segment"}</DialogTitle>
            <DialogDescription>Combine contact fields and custom properties, then preview the matching audience before saving.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="max-w-md space-y-1.5">
              <Label htmlFor="segment-name">Segment name</Label>
              <Input id="segment-name" value={name} onChange={(event) => setName(event.target.value)} maxLength={160} />
            </div>
            {rawMode ? <div className="space-y-2"><Label htmlFor="saved-segment-rules">Saved conditions</Label><Textarea id="saved-segment-rules" value={rawDefinition} onChange={(event) => setRawDefinition(event.target.value)} className="min-h-72 font-mono text-xs" /><p className="text-xs text-muted-foreground">Edit the saved AND/OR conditions. Preview validates the rules and shows the matching audience.</p></div> : <>
            <div className="flex items-center gap-2">
              <Label htmlFor="segment-root-operator">Match</Label>
              <select id="segment-root-operator" className="h-9 rounded-md border border-input bg-card px-2 text-sm" value={definition.operator} onChange={(event) => setDefinition((current) => ({ ...current, operator: event.target.value as "AND" | "OR" }))}>
                <option value="AND">all conditions (AND)</option>
                <option value="OR">any condition (OR)</option>
              </select>
            </div>
            <div className="space-y-3">
              {definition.conditions.map((rule) => isGroup(rule) ? (
                <section key={rule.id} className="space-y-3 rounded-lg border border-dashed p-3">
                  <div className="flex items-center justify-between gap-3">
                    <label className="flex items-center gap-2 text-sm">
                      <span>Group matches</span>
                      <select className="h-9 rounded-md border border-input bg-card px-2" value={rule.operator} onChange={(event) => setDefinition((current) => ({ ...current, conditions: current.conditions.map((currentRule) => isGroup(currentRule) && currentRule.id === rule.id ? { ...currentRule, operator: event.target.value as "AND" | "OR" } : currentRule) }))}>
                        <option value="AND">all (AND)</option>
                        <option value="OR">any (OR)</option>
                      </select>
                    </label>
                    <Button size="sm" variant="ghost" onClick={() => removeRule(rule.id)}>Remove group</Button>
                  </div>
                  {rule.conditions.map((condition) => (
                    <ConditionEditor key={condition.id} condition={condition} onChange={(change) => updateCondition(condition.id, change)} onRemove={() => removeRule(condition.id)} />
                  ))}
                  <Button size="sm" variant="outline" onClick={() => addGroupCondition(rule.id)}><Plus className="size-4" />Add condition</Button>
                </section>
              ) : (
                <ConditionEditor key={rule.id} condition={rule} onChange={(change) => updateCondition(rule.id, change)} onRemove={() => removeRule(rule.id)} />
              ))}
            </div>
            <div className="flex flex-wrap gap-2">
              <Button size="sm" variant="outline" onClick={() => setDefinition((current) => ({ ...current, conditions: [...current.conditions, makeCondition()] }))}><Plus className="size-4" />Add condition</Button>
              <Button size="sm" variant="outline" onClick={addGroup}><Plus className="size-4" />Add group</Button>
            </div>
            </>}
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={rawMode ? !rawDefinition.trim() : !definitionIsValid(definition)} loading={previewLoading} onClick={() => void runPreview(name.trim() || "Unsaved segment preview", (page) => previewSegment(rawMode ? rawDefinition : JSON.stringify(serializeDefinition(definition)), page))}>
              <Eye className="size-4" />Preview audience
            </Button>
            <Button disabled={!canSave} loading={saving} onClick={() => void save()}>Save segment</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-h-[85vh] max-w-4xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{previewName}</DialogTitle>
            <DialogDescription>Active, subscribed contacts matching the segment conditions.</DialogDescription>
          </DialogHeader>
          {previewLoading ? <LoadingState label="Evaluating segment" /> : null}
          {previewError ? <ErrorState description={previewError} /> : null}
          {preview ? (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">{preview.totalElements} matching contacts</p>
              <div className="overflow-x-auto rounded-lg border">
                <Table>
                  <TableHeader><TableRow><TableHead>Email</TableHead><TableHead>Name</TableHead><TableHead>Company</TableHead><TableHead>Source</TableHead></TableRow></TableHeader>
                  <TableBody>
                    {preview.items.map((contact) => (
                      <TableRow key={contact.id}>
                        <TableCell>{contact.email}</TableCell>
                        <TableCell>{[contact.firstName, contact.lastName].filter(Boolean).join(" ") || "-"}</TableCell>
                        <TableCell>{contact.company || "-"}</TableCell>
                        <TableCell>{contact.source || "-"}</TableCell>
                      </TableRow>
                    ))}
                    {preview.items.length === 0 ? <TableRow><TableCell colSpan={4} className="py-8 text-center text-muted-foreground">No matching contacts</TableCell></TableRow> : null}
                  </TableBody>
                </Table>
              </div>
              <div className="flex items-center justify-end gap-3 text-sm text-muted-foreground">
                <span>Page {preview.page + 1} of {Math.max(1, preview.totalPages)}</span>
                <Button variant="outline" size="icon" aria-label="Previous audience page" disabled={previewLoading || preview.page === 0} onClick={() => { if (previewRequest.current) void runPreview(previewName, previewRequest.current, preview.page - 1); }}><ChevronLeft className="size-4" /></Button>
                <Button variant="outline" size="icon" aria-label="Next audience page" disabled={previewLoading || preview.page + 1 >= preview.totalPages} onClick={() => { if (previewRequest.current) void runPreview(previewName, previewRequest.current, preview.page + 1); }}><ChevronRight className="size-4" /></Button>
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function ConditionEditor({
  condition,
  onChange,
  onRemove,
}: {
  condition: Condition;
  onChange: (change: Partial<Condition>) => void;
  onRemove: () => void;
}) {
  const needsValue = condition.operator !== "IS_EMPTY" && condition.operator !== "IS_NOT_EMPTY";
  return (
    <div className="grid items-end gap-2 rounded-md border bg-muted/20 p-3 md:grid-cols-[1.2fr_0.8fr_1fr_1.4fr_auto]">
      <div className="space-y-1.5">
        <Label htmlFor={`field-${condition.id}`}>Field</Label>
        <select
          id={`field-${condition.id}`}
          className="h-9 w-full rounded-md border border-input bg-card px-2 text-sm"
          value={condition.field}
          onChange={(event) => {
            const value = event.target.value;
            const field = contactFields.find((item) => item.value === value);
            onChange({ field: value, type: value === "__custom" ? condition.type : value.endsWith("_at") ? "DATE" : "TEXT", operator: value.endsWith("_at") ? "EQUALS" : "EQUALS", customField: field ? "" : condition.customField });
          }}
        >
          {contactFields.map((field) => <option key={field.value} value={field.value}>{field.label}</option>)}
          <option value="__custom">Custom property</option>
        </select>
        {condition.field === "__custom" ? (
          <Input aria-label="Custom property name" value={condition.customField} onChange={(event) => onChange({ customField: event.target.value })} placeholder="Property key" />
        ) : null}
      </div>
      {condition.field === "__custom" ? (
        <div className="space-y-1.5">
          <Label htmlFor={`type-${condition.id}`}>Data type</Label>
          <select id={`type-${condition.id}`} className="h-9 w-full rounded-md border border-input bg-card px-2 text-sm" value={condition.type} onChange={(event) => { const type = event.target.value as ConditionType; onChange({ type, operator: operators[type][0].value }); }}>
            <option value="TEXT">Text</option><option value="NUMBER">Number</option><option value="DATE">Date</option>
          </select>
        </div>
      ) : null}
      <div className="space-y-1.5">
        <Label htmlFor={`operator-${condition.id}`}>Operator</Label>
        <select id={`operator-${condition.id}`} className="h-9 w-full rounded-md border border-input bg-card px-2 text-sm" value={condition.operator} onChange={(event) => onChange({ operator: event.target.value })}>
          {operators[condition.type].map((operator) => <option key={operator.value} value={operator.value}>{operator.label}</option>)}
        </select>
      </div>
      {needsValue ? (
        <div className={condition.operator === "BETWEEN" ? "grid grid-cols-2 gap-2" : "space-y-1.5"}>
          <Label htmlFor={`value-${condition.id}`}>{condition.operator === "BETWEEN" ? "From" : "Value"}</Label>
          <Input id={`value-${condition.id}`} type={condition.type === "NUMBER" ? "number" : condition.type === "DATE" ? "date" : "text"} value={condition.value} onChange={(event) => onChange({ value: event.target.value })} />
          {condition.operator === "BETWEEN" ? (
            <>
              <Label htmlFor={`end-${condition.id}`} className="col-start-2">To</Label>
              <Input id={`end-${condition.id}`} type={condition.type === "NUMBER" ? "number" : condition.type === "DATE" ? "date" : "text"} value={condition.endValue} onChange={(event) => onChange({ endValue: event.target.value })} />
            </>
          ) : null}
        </div>
      ) : <div />}
      <Button type="button" size="icon" variant="ghost" aria-label="Remove condition" onClick={onRemove}><Trash2 className="size-4" /></Button>
    </div>
  );
}