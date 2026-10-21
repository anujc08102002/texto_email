"use client";

import { useMemo, useRef, useState } from "react";
import { Download, FileSpreadsheet, Upload } from "lucide-react";
import * as Papa from "papaparse";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { ApiClientError } from "@/lib/api";
import {
  importContactsCsv,
  type ContactImportResult,
} from "@/services/contacts";

type CsvRow = Record<string, string>;
type FieldKey = "email" | "first_name" | "last_name" | "company" | "phone";
type Mapping = Record<FieldKey, string>;
type RejectedRow = { row: CsvRow; reason: string };

const fields: { key: FieldKey; label: string; aliases: string[] }[] = [
  { key: "email", label: "Email", aliases: ["email", "emailaddress"] },
  { key: "first_name", label: "First name", aliases: ["firstname", "givenname"] },
  { key: "last_name", label: "Last name", aliases: ["lastname", "surname", "familyname"] },
  { key: "company", label: "Company", aliases: ["company", "organization", "business"] },
  { key: "phone", label: "Phone", aliases: ["phone", "phonenumber", "mobile"] },
];

const emptyMapping: Mapping = {
  email: "",
  first_name: "",
  last_name: "",
  company: "",
  phone: "",
};

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function normalizeHeader(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]/g, "");
}

function suggestedMapping(headers: string[]): Mapping {
  return Object.fromEntries(
    fields.map((field) => [
      field.key,
      headers.find((header) => field.aliases.includes(normalizeHeader(header))) ?? "",
    ]),
  ) as Mapping;
}

export function ContactImportDialog({
  open,
  onOpenChange,
  onImported,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onImported: () => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [fileName, setFileName] = useState("");
  const [headers, setHeaders] = useState<string[]>([]);
  const [rows, setRows] = useState<CsvRow[]>([]);
  const [mapping, setMapping] = useState<Mapping>(emptyMapping);
  const [issue, setIssue] = useState<string | null>(null);
  const [parsing, setParsing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<ContactImportResult | null>(null);
  const [rejected, setRejected] = useState<RejectedRow[]>([]);

  const validatedRows = useMemo(() => {
    const seen = new Set<string>();
    return rows.map((row) => {
      const email = (row[mapping.email] ?? "").trim().toLowerCase();
      let reason = "";
      if (!mapping.email) reason = "Select the email column";
      else if (!emailPattern.test(email)) reason = "Invalid email";
      else if (seen.has(email)) reason = "Duplicate email in file";
      else if (
        Object.values(mapping)
          .filter(Boolean)
          .some((header) => /[\r\n]/.test(row[header] ?? ""))
      ) {
        reason = "Line breaks inside mapped fields are not supported";
      }
      if (email) seen.add(email);
      return { row, email, reason };
    });
  }, [mapping, rows]);

  const validCount = validatedRows.filter((row) => !row.reason).length;

  function reset() {
    setFileName("");
    setHeaders([]);
    setRows([]);
    setMapping(emptyMapping);
    setIssue(null);
    setParsing(false);
    setSubmitting(false);
    setResult(null);
    setRejected([]);
    if (inputRef.current) inputRef.current.value = "";
  }

  function changeOpen(nextOpen: boolean) {
    onOpenChange(nextOpen);
    if (!nextOpen) reset();
  }

  function parseFile(file?: File) {
    reset();
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".csv")) {
      setIssue("Choose a CSV file. Excel workbook import is not available yet.");
      return;
    }
    if (file.size === 0 || file.size > 5_000_000) {
      setIssue("CSV files must be between 1 byte and 5 MB.");
      return;
    }

    setFileName(file.name);
    setParsing(true);
    Papa.parse<CsvRow>(file, {
      header: true,
      skipEmptyLines: "greedy",
      transformHeader: (header) => header.trim(),
      complete: (parsed) => {
        const detectedHeaders = parsed.meta.fields ?? [];
        if (parsed.errors.length > 0) {
          setIssue("The CSV could not be parsed. Check its quoting and delimiters.");
          setParsing(false);
          return;
        }
        if (detectedHeaders.length === 0) {
          setIssue("The CSV needs a header row.");
          setParsing(false);
          return;
        }
        if (parsed.data.length > 10_000) {
          setIssue("CSV files are limited to 10,000 data rows.");
          setParsing(false);
          return;
        }

        setHeaders(detectedHeaders);
        setRows(parsed.data);
        setMapping(suggestedMapping(detectedHeaders));
        setParsing(false);
      },
    });
  }

  function updateMapping(field: FieldKey, value: string) {
    setMapping((current) => ({ ...current, [field]: value }));
  }

  async function submitImport() {
    if (!mapping.email || validCount === 0) return;
    setSubmitting(true);
    setIssue(null);
    try {
      const importableRows = validatedRows.flatMap((item, originalIndex) =>
          item.reason ? [] : [{
              originalIndex,
              values: {
                email: item.row[mapping.email] ?? "",
                first_name: mapping.first_name ? item.row[mapping.first_name] ?? "" : "",
                last_name: mapping.last_name ? item.row[mapping.last_name] ?? "" : "",
                company: mapping.company ? item.row[mapping.company] ?? "" : "",
                phone: mapping.phone ? item.row[mapping.phone] ?? "" : "",
                ...Object.fromEntries(headers.filter((header) => /^custom\.[a-zA-Z0-9_]{1,64}$/.test(header)).map((header) => [header, item.row[header] ?? ""])),
              },
            }],
      );
      const csv = Papa.unparse(importableRows.map((item) => item.values), {
        columns: ["email", "first_name", "last_name", "company", "phone", ...headers.filter((header) => /^custom\.[a-zA-Z0-9_]{1,64}$/.test(header))],
      });
      const upload = new File([csv], "contacts-import.csv", { type: "text/csv" });
      const response = await importContactsCsv(upload);
      const rejectedByIndex = new Map<number, string>();

      validatedRows.forEach((row, index) => {
        if (row.reason) rejectedByIndex.set(index, row.reason);
      });
      response.errors.forEach((message) => {
        const match = /^Row\s+(\d+):\s*(.+)$/.exec(message);
          const originalIndex = match ? importableRows[Number(match[1]) - 2]?.originalIndex : undefined;
          if (match && originalIndex !== undefined) rejectedByIndex.set(originalIndex, match[2]);
      });

      setRejected(
        [...rejectedByIndex.entries()]
          .filter(([index]) => index >= 0 && index < rows.length)
          .map(([index, reason]) => ({ row: rows[index], reason })),
      );
      setResult(response);
      toast.success(`Imported ${response.accepted} contacts`);
      onImported();
    } catch (cause) {
      const message =
        cause instanceof ApiClientError
          ? cause.message
          : "Unable to import contacts.";
      setIssue(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  }

  function downloadRejected() {
    if (rejected.length === 0) return;
    const exportRows = rejected.map(({ row, reason }) => ({
      ...row,
      "Import error": reason,
    }));
    const csv = Papa.unparse(exportRows);
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = "rejected-contacts.csv";
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <Dialog open={open} onOpenChange={changeOpen}>
      <DialogContent className="max-h-[90vh] max-w-4xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Import contacts from CSV</DialogTitle>
          <DialogDescription>
            Map the email column and preview validation before importing. CSV files are limited to 5 MB and 10,000 rows.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-5">
          <div className="space-y-2">
            <Label htmlFor="contacts-csv">CSV file</Label>
            <input
              ref={inputRef}
              id="contacts-csv"
              type="file"
              accept=".csv,text/csv"
              disabled={submitting}
              onChange={(event) => parseFile(event.target.files?.[0])}
              className="block w-full rounded-lg border border-input bg-card px-3 py-2 text-sm file:mr-3 file:rounded-md file:border-0 file:bg-secondary file:px-3 file:py-1.5 file:text-sm file:font-medium"
            />
            {fileName ? (
              <p className="flex items-center gap-2 text-xs text-muted-foreground">
                <FileSpreadsheet className="size-4" />
                {fileName}
              </p>
            ) : null}
          </div>

          {issue ? (
            <p role="alert" className="text-sm text-destructive">
              {issue}
            </p>
          ) : null}

          {headers.length > 0 ? (
            <>
              <section aria-labelledby="contact-import-mapping">
                <h3 id="contact-import-mapping" className="mb-3 text-sm font-semibold">
                  Map columns
                </h3>
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {fields.map((field) => (
                    <div className="space-y-1.5" key={field.key}>
                      <Label htmlFor={`map-${field.key}`}>
                        {field.label}{field.key === "email" ? " *" : ""}
                      </Label>
                      <select
                        id={`map-${field.key}`}
                        value={mapping[field.key]}
                        onChange={(event) => updateMapping(field.key, event.target.value)}
                        className="h-10 w-full rounded-lg border border-input bg-card px-3 text-sm text-foreground"
                      >
                        <option value="">Not mapped</option>
                        {headers.map((header) => (
                          <option key={header} value={header}>{header}</option>
                        ))}
                      </select>
                    </div>
                  ))}
                </div>
              </section>

              <section aria-labelledby="contact-import-preview">
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                  <h3 id="contact-import-preview" className="text-sm font-semibold">
                    Preview
                  </h3>
                  <p className="text-xs text-muted-foreground">
                    {validCount} ready, {validatedRows.length - validCount} rejected
                  </p>
                </div>
                <div className="max-h-64 overflow-auto rounded-lg border">
                  <table className="w-full min-w-[36rem] text-left text-sm">
                    <thead className="sticky top-0 bg-muted text-xs text-muted-foreground">
                      <tr>
                        <th className="px-3 py-2">Email</th>
                        <th className="px-3 py-2">First name</th>
                        <th className="px-3 py-2">Company</th>
                        <th className="px-3 py-2">Validation</th>
                      </tr>
                    </thead>
                    <tbody>
                      {validatedRows.slice(0, 10).map((item, index) => (
                        <tr key={index} className="border-t">
                          <td className="max-w-56 truncate px-3 py-2">{item.email || "-"}</td>
                          <td className="px-3 py-2">{mapping.first_name ? item.row[mapping.first_name] || "-" : "-"}</td>
                          <td className="px-3 py-2">{mapping.company ? item.row[mapping.company] || "-" : "-"}</td>
                          <td className={item.reason ? "px-3 py-2 text-destructive" : "px-3 py-2 text-success"}>
                            {item.reason || "Ready"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {validatedRows.length > 10 ? (
                  <p className="mt-2 text-xs text-muted-foreground">
                    Showing the first 10 rows of {validatedRows.length}.
                  </p>
                ) : null}
              </section>
            </>
          ) : null}

          {result ? (
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-muted/40 p-3">
              <p className="text-sm">
                Imported <strong>{result.accepted}</strong>; skipped <strong>{result.skipped + validatedRows.filter((row) => row.reason).length}</strong>.
              </p>
              {rejected.length > 0 ? (
                <Button variant="outline" size="sm" onClick={downloadRejected}>
                  <Download className="size-4" />
                  Download rejected rows
                </Button>
              ) : null}
            </div>
          ) : null}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => changeOpen(false)}>
            Close
          </Button>
          <Button
            disabled={!fileName || !mapping.email || validCount === 0 || parsing || submitting || Boolean(result)}
            loading={submitting || parsing}
            onClick={() => void submitImport()}
          >
            <Upload className="size-4" />
            Import {validCount} contacts
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}