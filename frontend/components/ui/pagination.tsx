import * as React from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type PaginationProps = {
  page: number;
  pageCount: number;
  onPageChange: (page: number) => void;
  className?: string;
};

function Pagination({ page, pageCount, onPageChange, className }: PaginationProps) {
  const canPrev = page > 1;
  const canNext = page < pageCount;

  return (
    <nav aria-label="Pagination" className={cn("flex items-center justify-between gap-3", className)}>
      <p className="text-sm text-muted-foreground">
        Page {page} of {Math.max(pageCount, 1)}
      </p>
      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={!canPrev}
          onClick={() => onPageChange(page - 1)}
          aria-label="Previous page"
        >
          <ChevronLeft />
          Previous
        </Button>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={!canNext}
          onClick={() => onPageChange(page + 1)}
          aria-label="Next page"
        >
          Next
          <ChevronRight />
        </Button>
      </div>
    </nav>
  );
}

export { Pagination };
