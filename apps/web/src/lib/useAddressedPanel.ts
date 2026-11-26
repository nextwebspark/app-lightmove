import { useLocation, useNavigate, useSearchParams } from "react-router-dom";

interface PanelEntry {
  addressedPanel: { param: string; rest: string };
}

/** A panel held in the address (`?client=…`): opening is a step in history, stepping and closing are not. */
export interface AddressedPanel {
  value: string | null;
  /** A step in history from a closed panel; already open, it moves along instead. */
  open: (value: string, alongside?: Readonly<Record<string, string>>) => void;
  step: (value: string, alongside?: Readonly<Record<string, string>>) => void;
  /** Pops the step that opened it while the rest of the address is as it was; otherwise clears it in place. */
  close: () => void;
}

/** The Back rule for a panel opened from a list: `docs/architecture/features.md`, Back navigation. */
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

function stateOf(state: unknown): Record<string, unknown> {
  return typeof state === "object" && state !== null ? (state as Record<string, unknown>) : {};
}

function restOf(params: URLSearchParams, owned: readonly string[]): string {
  const rest = new URLSearchParams(params);
  for (const key of owned) rest.delete(key);
  rest.sort();
  return rest.toString();
}
