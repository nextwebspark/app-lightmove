import { useLocation, useNavigate, useSearchParams } from "react-router-dom";

/** What an opened panel leaves in its history entry: which param it opened, and the rest of the address then. */
interface PanelEntry {
  addressedPanel: { param: string; rest: string };
}

/** A panel held in the address (`?client=…`): opening is a step in history, stepping and closing are not. */
export interface AddressedPanel {
  value: string | null;
  /**
   * Opens the panel as a step in history, so Back closes it; already open, it moves along instead. `alongside` sets
   * params that live with it in the same write (`{ tab: "activity" }`).
   */
  open: (value: string, alongside?: Readonly<Record<string, string>>) => void;
  /** Moves the open panel to another value in place, as Previous and Next do. */
  step: (value: string, alongside?: Readonly<Record<string, string>>) => void;
  /** Pops the step that opened it while the rest of the address is as it was; otherwise clears it in place. */
  close: () => void;
}

/**
 * The one rule for a panel, drawer or record opened from a list (`docs/architecture/features.md`, Back navigation):
 * opening adds a step, so Back closes it and lands on the list as left; moving along it or closing it adds none, so
 * Back never walks through everyone looked at, nor reopens what was just closed. `alsoClears` are the params that
 * live and die with the panel (`?tab=`, `?note=`).
 */
export function useAddressedPanel(param: string, alsoClears: readonly string[] = []): AddressedPanel {
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const value = searchParams.get(param);
  const owned = [param, ...alsoClears];

  const write = (next: string | null, mode: "push" | "replace", alongside: Readonly<Record<string, string>> = {}) =>
    setSearchParams(
      (current) => {
        const params = new URLSearchParams(current);
        for (const key of owned) params.delete(key);
        if (next !== null) params.set(param, next);
        for (const [key, alongsideValue] of Object.entries(alongside)) params.set(key, alongsideValue);
        return params;
      },
      mode === "push"
        ? { state: { ...stateOf(location.state), addressedPanel: { param, rest: restOf(searchParams, owned) } } }
        : { replace: true, state: location.state },
    );

  const step = (next: string, alongside?: Readonly<Record<string, string>>) => write(next, "replace", alongside);
  const open = (next: string, alongside?: Readonly<Record<string, string>>) =>
    value === null ? write(next, "push", alongside) : step(next, alongside);
  const close = () => {
    if (value === null) return;
    const entry = (location.state as Partial<PanelEntry> | null)?.addressedPanel;
    // A filter or page changed under the panel since it opened would come undone with the pop, so then it clears.
    if (entry?.param === param && entry.rest === restOf(searchParams, owned)) navigate(-1);
    else write(null, "replace");
  };

  return { value, open, step, close };
}

/** What else the entry carries (the Executives view's way back), kept on the step the panel adds. */
function stateOf(state: unknown): Record<string, unknown> {
  return typeof state === "object" && state !== null ? (state as Record<string, unknown>) : {};
}

function restOf(params: URLSearchParams, owned: readonly string[]): string {
  const rest = new URLSearchParams(params);
  for (const key of owned) rest.delete(key);
  rest.sort();
  return rest.toString();
}
