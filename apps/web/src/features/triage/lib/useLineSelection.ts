import { useCallback, useMemo, useState } from "react";
import { useToast } from "../../../components/ui/Toast";
import { triageRowId, type TriageCompanyRow } from "./triageRows";

/** A ticked grid line, kept as what it names so a tick made on another page still counts. */
interface TickedLine {
  companyId: string | null;
  candidateId: string | null;
}

/**
 * Grid lines ticked on a stage — a person at a company, a company nobody is listed at yet, or a person at no company
 * of the mandate — held to a cap. Find executives reads the companies they name, once each; Add to sequence the
 * people.
 */
export function useLineSelection(cap: number) {
  const toast = useToast();
  const [ticked, setTicked] = useState<ReadonlyMap<string, TickedLine>>(
    () => new Map(),
  );
  const clear = useCallback(() => setTicked(new Map()), []);

  const toggle = (row: TriageCompanyRow) => {
    const id = triageRowId(row);
    const next = new Map(ticked);
    if (next.has(id)) {
      next.delete(id);
    } else if (next.size >= cap) {
      toast(`${cap} rows can be ticked at once`);
      return;
    } else {
      next.set(id, lineOf(row));
    }
    setTicked(next);
  };

  const toggleAll = (rows: readonly TriageCompanyRow[]) => {
    const next = new Map(ticked);
    if (rows.every((row) => next.has(triageRowId(row)))) {
      rows.forEach((row) => next.delete(triageRowId(row)));
      setTicked(next);
      return;
    }
    const adding = rows.filter((row) => !next.has(triageRowId(row)));
    const room = Math.max(0, cap - next.size);
    adding
      .slice(0, room)
      .forEach((row) => next.set(triageRowId(row), lineOf(row)));
    if (adding.length > room)
      toast(`${cap} rows can be ticked at once — the first ${cap} are ticked`);
    setTicked(next);
  };

  const companyIds = useMemo(
    () => distinct([...ticked.values()].map((line) => line.companyId)),
    [ticked],
  );
  const candidateIds = useMemo(
    () => distinct([...ticked.values()].map((line) => line.candidateId)),
    [ticked],
  );

  return {
    size: ticked.size,
    isTicked: (row: TriageCompanyRow) => ticked.has(triageRowId(row)),
    /** The companies the ticked lines name, each once. */
    companyIds,
    /** The people on the ticked lines. */
    candidateIds,
    clear,
    toggle,
    toggleAll,
  };
}

function lineOf(row: TriageCompanyRow): TickedLine {
  return {
    companyId: row.company?.id ?? null,
    candidateId: row.candidate?.id ?? null,
  };
}

function distinct(ids: (string | null)[]): string[] {
  return [...new Set(ids.filter((id): id is string => id !== null))];
}
