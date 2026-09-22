"use client";

import { useMemo, useState } from "react";
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";

export type OpsColumn<T> = {
  key: string;
  label: string;
  value: (row: T) => string | number | null | undefined;
  render?: (row: T) => React.ReactNode;
  className?: string;
  sortable?: boolean;
};

export function OpsTable<T>({ rows, columns, rowKey, onRowClick, emptyTitle = "No results" }: {
  rows: T[]; columns: OpsColumn<T>[]; rowKey: (row: T) => string; onRowClick?: (row: T) => void; emptyTitle?: string;
}) {
  const [sort, setSort] = useState<{ key: string; direction: "asc" | "desc" }>({ key: columns[0]?.key ?? "", direction: "desc" });
  const sorted = useMemo(() => {
    const column = columns.find((item) => item.key === sort.key);
    if (!column) return rows;
    return [...rows].sort((a, b) => {
      const av = column.value(a) ?? "";
      const bv = column.value(b) ?? "";
      const result = typeof av === "number" && typeof bv === "number" ? av - bv : String(av).localeCompare(String(bv));
      return sort.direction === "asc" ? result : -result;
    });
  }, [columns, rows, sort]);

  if (!rows.length) return <EmptyState title={emptyTitle} description="Try adjusting the active filters or search term." className="border-0" />;

  return (
    <Table>
      <TableHeader><TableRow>{columns.map((column) => (
        <TableHead key={column.key} className={column.className}>
          {column.sortable === false ? column.label : (
            <Button type="button" variant="ghost" size="sm" className="-ml-3 h-8 px-2 text-[11px] uppercase" onClick={() => setSort((current) => ({ key: column.key, direction: current.key === column.key && current.direction === "asc" ? "desc" : "asc" }))}>
              {column.label}
              {sort.key !== column.key ? <ArrowUpDown className="size-3" /> : sort.direction === "asc" ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />}
            </Button>
          )}
        </TableHead>
      ))}</TableRow></TableHeader>
      <TableBody>{sorted.map((row) => (
        <TableRow key={rowKey(row)} tabIndex={onRowClick ? 0 : undefined} role={onRowClick ? "link" : undefined}
          className={cn(onRowClick && "cursor-pointer focus-visible:bg-muted")}
          onClick={() => onRowClick?.(row)} onKeyDown={(event) => { if (onRowClick && (event.key === "Enter" || event.key === " ")) onRowClick(row); }}>
          {columns.map((column) => <TableCell key={column.key} className={column.className}>{column.render ? column.render(row) : String(column.value(row) ?? "—")}</TableCell>)}
        </TableRow>
      ))}</TableBody>
    </Table>
  );
}

