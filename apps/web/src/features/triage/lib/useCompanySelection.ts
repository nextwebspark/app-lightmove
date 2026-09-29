import { useCallback, useState } from "react";
import { useToast } from "../../../components/ui/Toast";
import { companiesOf } from "./sourcingSummary";

/** Companies ticked for Find executives — by company id, since a grid line is a person at one — held to a cap. */
export function useCompanySelection() {
  const toast = useToast();
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<string>>(() => new Set());
  const clear = useCallback(() => setSelectedIds(new Set()), []);

  const toggle = (companyId: string, cap: number) => {
    const next = new Set(selectedIds);
    if (next.has(companyId)) {
      next.delete(companyId);
    } else if (next.size >= cap) {
      toast(`Find executives takes ${companiesOf(cap)} at a time`);
      return;
    } else {
      next.add(companyId);
    }
    setSelectedIds(next);
  };

  const toggleAll = (companyIds: string[], cap: number) => {
    const next = new Set(selectedIds);
    if (companyIds.every((id) => next.has(id))) {
      companyIds.forEach((id) => next.delete(id));
      setSelectedIds(next);
      return;
    }
    const room = Math.max(0, cap - next.size);
    const adding = companyIds.filter((id) => !next.has(id));
    adding.slice(0, room).forEach((id) => next.add(id));
    if (adding.length > room) {
      toast(`Find executives takes ${companiesOf(cap)} at a time — the first ${cap} are ticked`);
    }
    setSelectedIds(next);
  };

  return { selectedIds, clear, toggle, toggleAll };
}
