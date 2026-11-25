import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { withOpenCandidate } from "./useOpenRecord";

/** Where Open full page came from, held in the history entry it pushed, and whom it opened. */
export interface ExecutivesEntry {
  from: "table" | "pipeline";
  enteredFor: string;
}

/** The entry's state, where it is one Open full page pushed; any other router state is no entry. */
export function executivesEntryOf(state: unknown): ExecutivesEntry | null {
  if (typeof state !== "object" || state === null) return null;
  const { from, enteredFor } = state as Partial<ExecutivesEntry>;
  return (from === "table" || from === "pipeline") && typeof enteredFor === "string" ? { from, enteredFor } : null;
}

/**
 * Open full page is a visit, not a choice of view: a step in history, so Back returns to the grid or the board with
 * the person open, and the remembered view stays as it was.
 */
export function useExecutivesVisit(active: boolean, openId: string | null) {
  const location = useLocation();
  const navigate = useNavigate();
  const [, setSearchParams] = useSearchParams();
  const entry = active ? executivesEntryOf(location.state) : null;

  const enter = (from: ExecutivesEntry["from"], candidateId: string) =>
    setSearchParams(
      (current) => {
        const params = withOpenCandidate(current, candidateId);
        params.set("view", "executives");
        params.delete("record");
        return params;
      },
      { state: { from, enteredFor: candidateId } satisfies ExecutivesEntry },
    );

  // Back pops the entry while it still holds the person it was pushed for; after a step it replaces, with whoever
  // is open now, rather than returning to someone the reader has moved on from.
  const leave = () => {
    if (!entry) return;
    if (openId === entry.enteredFor) {
      navigate(-1);
      return;
    }
    setSearchParams(
      (current) => {
        const params = new URLSearchParams(current);
        params.set("view", entry.from);
        if (openId) params.set("candidate", openId);
        return params;
      },
      { replace: true },
    );
  };

  return { entry, enter, leave };
}
