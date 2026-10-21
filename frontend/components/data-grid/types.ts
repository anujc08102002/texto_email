import { ReactNode } from "react";

export interface DataGridColumn<T> {
  key: string;
  label: string;
  sortable?: boolean;
  filterable?: boolean;
  fixed?: boolean;
  width?: string;
  align?: "left" | "right" | "center";
  sortType?: "alpha" | "number" | "date";
  accessor?: (row: T) => string;
  render?: (row: T) => ReactNode;
}

export interface DataGridProps<T> {
  pageName: string;
  columns: DataGridColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
  searchPlaceholder?: string;
  emptyTitle?: string;
  emptyMessage?: string;
  toolbarActions?: ReactNode;
  toolbarCenter?: ReactNode;
  totalCountLabel?: ReactNode | string | number;
  entityName?: string;
  loading?: boolean;
  server?: {
    page: number; pageSize: number; total: number; search: string;
    onPageChange: (page: number) => void; onPageSizeChange: (size: number) => void;
    onSearchChange: (query: string) => void; sort: SortState; onSortChange: (sort: SortState) => void;
  };
}

export type SortDirection = "asc" | "desc";

export interface SortState {
  key: string;
  direction: SortDirection;
}