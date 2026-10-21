"use client";

import { useMemo, useState } from "react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Filter } from "lucide-react";

interface Props {
  columnLabel: string;
  availableValues: string[];
  activeValues: Set<string> | null;
  onApply: (values: Set<string> | null) => void;
}

export default function ColumnFilterPopover({
  columnLabel,
  availableValues,
  activeValues,
  onApply,
}: Props) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [draft, setDraft] = useState<Set<string>>(
    new Set(activeValues ?? availableValues),
  );

  const isActive = activeValues !== null;

  function openPanel(next: boolean) {
    if (next) {
      setDraft(new Set(activeValues ?? availableValues));
      setSearch("");
    }
    setOpen(next);
  }

  const filteredValues = useMemo(
    () =>
      availableValues.filter((v) =>
        v.toLowerCase().includes(search.toLowerCase()),
      ),
    [availableValues, search],
  );

  const allChecked =
    filteredValues.length > 0 &&
    filteredValues.every((v) => draft.has(v));

  function toggleAll(checked: boolean) {
    const next = new Set(draft);
    filteredValues.forEach((v) => {
      if (checked) next.add(v);
      else next.delete(v);
    });
    setDraft(next);
  }

  function toggleOne(value: string, checked: boolean) {
    const next = new Set(draft);
    if (checked) next.add(value);
    else next.delete(value);
    setDraft(next);
  }

  function apply() {
    if (draft.size === availableValues.length) {
      onApply(null);
    } else {
      onApply(new Set(draft));
    }
    setOpen(false);
  }

  function reset() {
    onApply(null);
    setOpen(false);
  }

  return (
    <Popover open={open} onOpenChange={openPanel}>
      <PopoverTrigger asChild>
        <button
          type="button"
          className={`ml-1 inline-flex h-4 w-4 items-center justify-center rounded ${
            isActive
              ? "bg-white text-green-600"
              : "text-white/80 hover:text-white"
          }`}
          aria-label={`Filter ${columnLabel}`}
        >
          <Filter className="h-3 w-3" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-64 p-0" align="start">
        <div className="border-b p-2">
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search..."
            className="h-8 text-sm"
          />
        </div>
        <div className="max-h-56 overflow-y-auto p-2 space-y-1">
          <div className="flex items-center gap-2 px-1 py-1">
            <Checkbox
              checked={allChecked}
              onCheckedChange={(c) => toggleAll(Boolean(c))}
              id={`select-all-${columnLabel}`}
            />
            <label
              htmlFor={`select-all-${columnLabel}`}
              className="text-sm font-semibold cursor-pointer"
            >
              (Select All)
            </label>
          </div>
          {filteredValues.map((value) => (
            <div key={value} className="flex items-center gap-2 px-1 py-1">
              <Checkbox
                checked={draft.has(value)}
                onCheckedChange={(c) => toggleOne(value, Boolean(c))}
                id={`filter-${columnLabel}-${value}`}
              />
              <label
                htmlFor={`filter-${columnLabel}-${value}`}
                className="text-sm cursor-pointer truncate"
                title={value}
              >
                {value}
              </label>
            </div>
          ))}
          {filteredValues.length === 0 && (
            <div className="text-xs text-gray-400 px-1 py-2">
              No values found.
            </div>
          )}
        </div>
        <div className="flex justify-end gap-2 border-t bg-gray-50 p-2">
          <Button size="sm" variant="outline" className="text-red-600" onClick={reset}>
            Reset
          </Button>
          <Button size="sm" variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button size="sm" onClick={apply}>
            OK
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}