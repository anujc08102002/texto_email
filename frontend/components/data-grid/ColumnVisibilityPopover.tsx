"use client";

import { useState } from "react";
import { DataGridColumn } from "./types";

interface Props<T> {
  columns: DataGridColumn<T>[];
  hiddenCols: Set<string>;
  onToggle: (key: string, visible: boolean) => void;
  onReset: () => void;
  onSave: () => Promise<void>;
}

export default function ColumnVisibilityPopover<T>({
  columns,
  hiddenCols,
  onToggle,
  onReset,
  onSave,
}: Props<T>) {
  const [open, setOpen] = useState(false);
  const [saveState, setSaveState] = useState<"idle" | "saving" | "saved" | "error">(
    "idle",
  );

  async function handleSave() {
    setSaveState("saving");
    const ok = await onSave().then(
      () => true,
      () => false,
    );
    setSaveState(ok ? "saved" : "error");
    if (ok) setOpen(false);
    setTimeout(() => setSaveState("idle"), 1800);
  }

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="border border-gray-300 rounded-lg px-3.5 py-2 text-xs font-bold text-gray-700 h-full cursor-pointer flex items-center gap-1.5 bg-white hover:bg-gray-50 shadow-2xs transition-colors"
        style={open ? { borderColor: "#00a65a", color: "#00a65a" } : {}}
      >
        <span>⚙ Columns</span>
        <span className="text-[10px] text-gray-500">▼</span>
      </button>
      {open && (
        <div className="absolute right-0 mt-2 w-56 bg-white border border-gray-200 rounded-lg shadow-lg z-50">
          <div className="text-xs font-bold text-gray-400 uppercase px-3 py-2 border-b border-gray-100">
            Show / hide columns
          </div>
          <div className="max-h-60 overflow-y-auto py-1">
            {columns.map((c) => {
              const visible = c.fixed || !hiddenCols.has(c.key);
              return (
                <label
                  key={c.key}
                  className="flex items-center gap-2 px-3 py-1.5 text-sm cursor-pointer hover:bg-gray-50"
                >
                  <input
                    type="checkbox"
                    checked={visible}
                    disabled={c.fixed}
                    onChange={(e) => onToggle(c.key, e.target.checked)}
                    style={{ accentColor: "#00a65a" }}
                    className="cursor-pointer"
                  />
                  <span className="text-sm font-medium text-gray-700">{c.label}</span>
                  {c.fixed && (
                    <span className="ml-auto text-xs opacity-50">🔒</span>
                  )}
                </label>
              );
            })}
          </div>
          <div className="flex gap-2 p-2 border-t border-gray-100 bg-gray-50">
            <button
              type="button"
              onClick={onReset}
              className="flex-1 border border-gray-300 rounded-md py-1 text-xs font-semibold text-gray-700 hover:bg-gray-100 cursor-pointer"
            >
              Reset
            </button>
            <button
              type="button"
              onClick={handleSave}
              className="flex-1 rounded-md py-1 text-xs font-semibold text-white cursor-pointer"
              style={{ background: "#00a65a" }}
            >
              {saveState === "saving"
                ? "Saving..."
                : saveState === "saved"
                  ? "✓ Saved"
                  : saveState === "error"
                    ? "✗ Failed"
                    : "Save"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}