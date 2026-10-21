"use client";

import { useEffect, useMemo, useState } from "react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Search, ChevronUp, ChevronDown, X } from "lucide-react";
import { DataGridColumn, DataGridProps, SortState } from "./types";
import ColumnFilterPopover from "./ColumnFilterPopover";
import ColumnVisibilityPopover from "./ColumnVisibilityPopover";
import { getColSettings, saveColSettings } from "@/lib/colSettings";

function getCellValue<T>(row: T, col: DataGridColumn<T>): string {
  if (col.accessor) return col.accessor(row);
  const raw = (row as Record<string, unknown>)[col.key];
  if (raw === null || raw === undefined) return "";
  return String(raw);
}

function compareValues(a: string, b: string, type?: string): number {
  if (type === "number") {
    return (parseFloat(a) || 0) - (parseFloat(b) || 0);
  }
  if (type === "date") {
    const parse = (val: string) => {
      if (!val) return 0;
      const parseable =
        val.endsWith("Z") || /[+-]\d{2}:?\d{2}$/.test(val) ? val : `${val.replace(" ", "T")}Z`;
      const time = new Date(parseable).getTime();
      return isNaN(time) ? 0 : time;
    };
    return parse(a) - parse(b);
  }
  return a.localeCompare(b, undefined, { numeric: true });
}

export default function DataGrid<T>({
  pageName,
  columns,
  rows,
  rowKey,
  searchPlaceholder = "Search...",
  emptyTitle = "No results found",
  emptyMessage = "No records match your current filters.",
  toolbarActions,
  toolbarCenter,
  totalCountLabel,
  entityName = "Profiles",
  loading,
  server,
}: DataGridProps<T>) {
  const [search, setSearch] = useState("");
  const [filters, setFilters] = useState<Record<string, Set<string>>>({});
  const [sort, setSort] = useState<SortState | null>(null);
  const [hiddenCols, setHiddenCols] = useState<Set<string>>(new Set());
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  useEffect(() => {
    getColSettings(pageName).then((saved) => {
      if (!saved) return;
      const hidden = new Set<string>();
      Object.entries(saved).forEach(([key, visible]) => {
        if (!visible) hidden.add(key);
      });
      setHiddenCols(hidden);
    });
  }, [pageName]);

  function matchesOtherFilters(row: T, excludeKey: string): boolean {
    return Object.entries(filters).every(([key, values]) => {
      if (key === excludeKey) return true;
      const cellValue = getCellValue(
        row,
        columns.find((c) => c.key === key)!,
      );
      return values.has(cellValue);
    });
  }

  function getAvailableValues(col: DataGridColumn<T>): string[] {
    const set = new Set<string>();
    rows.forEach((row) => {
      if (!matchesOtherFilters(row, col.key)) return;
      const v = getCellValue(row, col);
      if (v !== "") set.add(v);
    });
    return Array.from(set).sort((a, b) =>
      a.localeCompare(b, undefined, { numeric: true }),
    );
  }

  const filteredRows = useMemo(() => {
    const q = server ? "" : search.toLowerCase();
    return rows.filter((row) => {
      const passFilters = Object.entries(filters).every(([key, values]) => {
        const col = columns.find((c) => c.key === key);
        if (!col) return true;
        return values.has(getCellValue(row, col));
      });
      if (!passFilters) return false;

      if (!q) return true;
      return columns.some((col) =>
        getCellValue(row, col).toLowerCase().includes(q),
      );
    });
  }, [rows, filters, search, columns, server]);

  const sortedRows = useMemo(() => {
    if (server || !sort) return filteredRows;
    const col = columns.find((c) => c.key === sort.key);
    if (!col) return filteredRows;

    const copy = [...filteredRows];
    copy.sort((a, b) => {
      const cmp = compareValues(
        getCellValue(a, col),
        getCellValue(b, col),
        col.sortType,
      );
      return sort.direction === "asc" ? cmp : -cmp;
    });
    return copy;
  }, [filteredRows, sort, columns, server]);

  const effectiveSize = server?.pageSize ?? pageSize;
  const count = server?.total ?? sortedRows.length;
  const totalPages = Math.max(1, Math.ceil(count / effectiveSize));
  const currentPage = Math.min(server ? server.page + 1 : page, totalPages);
  const startIdx = (currentPage - 1) * effectiveSize;
  const pageRows = server ? sortedRows : sortedRows.slice(startIdx, startIdx + effectiveSize);
  const effectiveSort = server?.sort ?? sort;
  function changePage(next: number) { if (server) server.onPageChange(next - 1); else setPage(next); }

  const visibleColumns = columns.filter(
    (c) => c.fixed || !hiddenCols.has(c.key),
  );

  function toggleSort(col: DataGridColumn<T>) {
    if (!col.sortable) return;
    if (server) { server.onSortChange({ key: col.key, direction: server.sort.key === col.key && server.sort.direction === "asc" ? "desc" : "asc" }); return; }
    setSort((prev) => {
      if (!prev || prev.key !== col.key) return { key: col.key, direction: "asc" };
      return {
        key: col.key,
        direction: prev.direction === "asc" ? "desc" : "asc",
      };
    });
  }

  function applyFilter(col: DataGridColumn<T>, values: Set<string> | null) {
    setFilters((prev) => {
      const next = { ...prev };
      if (values === null) delete next[col.key];
      else next[col.key] = values;
      return next;
    });
    setPage(1);
  }

  function toggleColumn(key: string, visible: boolean) {
    setHiddenCols((prev) => {
      const next = new Set(prev);
      if (visible) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function resetColumns() {
    setHiddenCols(new Set());
  }

  async function persistColumns() {
    const visibility: Record<string, boolean> = {};
    columns.forEach((col) => {
      if (!col.fixed) visibility[col.key] = !hiddenCols.has(col.key);
    });
    const ok = await saveColSettings(pageName, visibility);
    if (!ok) throw new Error("Failed to save column settings.");
  }

  function clearAllFilters() {
    setFilters({});
    if (server) server.onSearchChange("");
    else setSearch("");
    setPage(1);
  }

  const hasActiveFilters = Object.keys(filters).length > 0 || (server?.search ?? search) !== "";

  return (
    <div className="texto-data-grid min-w-0 rounded-2xl border border-gray-100 bg-white shadow-xs p-4 sm:p-6 space-y-5">
      {/* Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-4 py-1">
        <div className="relative w-full sm:w-80">
          <Search className="absolute left-3.5 top-3 h-4 w-4 text-gray-400" />
          <Input
            value={server?.search ?? search}
            onChange={(e) => {
              if (server) server.onSearchChange(e.target.value);
              else { setSearch(e.target.value); setPage(1); }
            }}
            placeholder={searchPlaceholder}
            aria-label={searchPlaceholder}
            className="pl-9 bg-white border-gray-300 rounded-lg text-sm h-10 focus-visible:ring-[#00a65a] placeholder:text-gray-400 font-normal shadow-2xs"
          />
        </div>

        {toolbarCenter ? (
          <div className="flex flex-col items-center justify-center text-center">
            {toolbarCenter}
          </div>
        ) : totalCountLabel !== undefined ? (
          <div className="text-sm text-gray-500 font-normal text-center">
            Total <span className="font-bold text-[#00a65a] mx-0.5">{totalCountLabel}</span> {entityName}
          </div>
        ) : null}

        <div className="flex items-center gap-3">
          <ColumnVisibilityPopover
            columns={columns}
            hiddenCols={hiddenCols}
            onToggle={toggleColumn}
            onReset={resetColumns}
            onSave={persistColumns}
          />
          {toolbarActions}
        </div>
      </div>

      {/* Table Container */}
      <div className="w-full overflow-hidden rounded-xl border border-gray-100 shadow-2xs">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[700px]">
            <thead>
              <tr className="bg-[#00a65a]">
                {visibleColumns.map((col, idx) => (
                  <th
                    key={col.key}
                    style={{ width: col.width }}
                    className={`whitespace-nowrap px-4 py-3.5 text-left text-xs font-bold uppercase tracking-wider text-white ${
                      col.align === "right" ? "text-right" : ""
                    } ${idx === 0 ? "rounded-tl-xl" : ""} ${
                      idx === visibleColumns.length - 1 ? "rounded-tr-xl" : ""
                    }`}
                  >
                    <span className="inline-flex items-center gap-1.5">
                      <button type="button" disabled={!col.sortable} aria-label={col.sortable ? `Sort by ${col.label}` : col.label}
                        className={col.sortable ? "cursor-pointer select-none inline-flex items-center gap-1" : ""}
                        onClick={() => toggleSort(col)}
                      >
                        {col.label}
                        {col.sortable && (
                          <span className="text-white/80">
                            {effectiveSort?.key === col.key ? (
                              effectiveSort.direction === "asc" ? (
                                <ChevronUp className="h-3.5 w-3.5" />
                              ) : (
                                <ChevronDown className="h-3.5 w-3.5" />
                              )
                            ) : (
                              <ChevronDown className="h-3.5 w-3.5 opacity-70" />
                            )}
                          </span>
                        )}
                      </button>
                      {col.filterable && !server && (
                        <ColumnFilterPopover
                          columnLabel={col.label}
                          availableValues={getAvailableValues(col)}
                          activeValues={filters[col.key] ?? null}
                          onApply={(values) => applyFilter(col, values)}
                        />
                      )}
                    </span>
                  </th>
                ))}
              </tr>
            </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={visibleColumns.length} className="p-8 text-center text-gray-400">
                  Loading...
                </td>
              </tr>
            ) : pageRows.length === 0 ? (
              <tr>
                <td colSpan={visibleColumns.length} className="p-0">
                  <div className="flex flex-col items-center gap-3 py-16 text-center">
                    <div className="w-16 h-16 rounded-full bg-purple-50 flex items-center justify-center text-2xl text-purple-600 shadow-2xs">
                      🔍
                    </div>
                    <div className="text-base font-extrabold text-[#00a65a]">
                      {emptyTitle}
                    </div>
                    <div className="text-xs text-gray-500">{emptyMessage}</div>
                    {hasActiveFilters && (
                      <Button size="sm" variant="outline" onClick={clearAllFilters} className="mt-2 text-xs">
                        <X className="mr-1 h-3 w-3" />
                        Clear All Filters
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            ) : (
              
              pageRows.map((row) => (
                <tr key={rowKey(row)} className="border-b hover:bg-gray-50">
                  {visibleColumns.map((col) => (
                    <td
                      key={col.key}
                      className={`px-3 py-2.5 text-sm text-gray-700 ${
                        col.align === "right" ? "text-right" : ""
                      }`}
                    >
                      {col.render ? col.render(row) : getCellValue(row, col)}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>

      {/* Pagination */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 bg-gray-50 px-4 py-3">
        <div className="flex items-center gap-2 text-sm text-gray-600">
          Show
          <select
            aria-label="Rows per page"
            value={effectiveSize}
            onChange={(e) => {
              if (server) server.onPageSizeChange(Number(e.target.value));
              else { setPageSize(Number(e.target.value)); setPage(1); }
            }}
            className="h-8 rounded-md border border-gray-300 bg-white px-2 text-sm font-medium focus:outline-none"
          >
            {[10, 20, 50, 100].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
          per page
        </div>

        <div className="text-sm text-gray-500 font-medium">
          {count > 0
            ? `${startIdx + 1}–${Math.min(startIdx + effectiveSize, count)} of ${count} ${entityName}`
            : ""}
        </div>

        <div className="grid-pagination flex flex-wrap items-center justify-center gap-1">
          <button
            type="button"
            disabled={currentPage === 1}
            aria-label="First page" onClick={() => changePage(1)}
            className="h-8 w-8 text-xs font-medium rounded-md border border-gray-200 bg-white text-gray-500 hover:bg-gray-100 disabled:opacity-40 disabled:hover:bg-white cursor-pointer disabled:cursor-not-allowed flex items-center justify-center"
          >
            «
          </button>
          <button
            type="button"
            disabled={currentPage === 1}
            aria-label="Previous page" onClick={() => changePage(currentPage - 1)}
            className="h-8 w-8 text-xs font-medium rounded-md border border-gray-200 bg-white text-gray-500 hover:bg-gray-100 disabled:opacity-40 disabled:hover:bg-white cursor-pointer disabled:cursor-not-allowed flex items-center justify-center"
          >
            ‹
          </button>
          {Array.from({ length: Math.min(7, totalPages) }, (_, i) => {
            let p = i + 1;
            if (totalPages > 7) {
              const start = Math.max(1, Math.min(currentPage - 3, totalPages - 6));
              p = start + i;
            }
            return (
              <button
                key={p}
                type="button"
                onClick={() => changePage(p)}
                className={`h-8 w-8 text-xs font-bold rounded-md border transition-colors cursor-pointer flex items-center justify-center ${
                  currentPage === p
                    ? "bg-[#00a65a] text-white border-[#00a65a]"
                    : "bg-white text-gray-700 border-gray-200 hover:bg-gray-100"
                }`}
              >
                {p}
              </button>
            );
          })}
          <button
            type="button"
            disabled={currentPage === totalPages}
            aria-label="Next page" onClick={() => changePage(currentPage + 1)}
            className="h-8 w-8 text-xs font-medium rounded-md border border-gray-200 bg-white text-gray-500 hover:bg-gray-100 disabled:opacity-40 disabled:hover:bg-white cursor-pointer disabled:cursor-not-allowed flex items-center justify-center"
          >
            ›
          </button>
          <button
            type="button"
            disabled={currentPage === totalPages}
            aria-label="Last page" onClick={() => changePage(totalPages)}
            className="h-8 w-8 text-xs font-medium rounded-md border border-gray-200 bg-white text-gray-500 hover:bg-gray-100 disabled:opacity-40 disabled:hover:bg-white cursor-pointer disabled:cursor-not-allowed flex items-center justify-center"
          >
            »
          </button>
        </div>
      </div>
    </div>
  );
}
