import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";

/**
 * Whether the assistant is open, and which mandate it is asking about.
 *
 * <p>Mounted above {@link AppRoutes} rather than inside the shell, and that is the whole reason it
 * exists. There is no single layout wrapping every signed-in route — `WorkspaceLayout`,
 * `ProjectLayout` and `SettingsLayout` are siblings — so state held anywhere below them is reset by
 * crossing from a project screen to a workspace one. The panel is meant to survive that.
 *
 * The panel itself renders inside `AppShell`, because the mockup docks it beside the page rather
 * than over the top: `<main>` is `flex-1`, so a sibling with a fixed width narrows it and nothing is
 * ever covered. That component may remount freely — everything worth keeping is here.
 */
type AssistantState = {
  open: boolean;
  openAssistant: () => void;
  closeAssistant: () => void;
  toggleAssistant: () => void;
};

const OPEN_KEY = "lm.assistant.open";

const AssistantContext = createContext<AssistantState>({
  open: false,
  openAssistant: () => {},
  closeAssistant: () => {},
  toggleAssistant: () => {},
});

export function useAssistant(): AssistantState {
  return useContext(AssistantContext);
}

export function AssistantProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(readStoredOpen);

  const remember = useCallback((next: boolean) => {
    setOpen(next);
    try {
      localStorage.setItem(OPEN_KEY, next ? "1" : "0");
    } catch {
      // A private window refuses this, and the panel opening is not worth failing over.
    }
  }, []);

  const value = useMemo<AssistantState>(
    () => ({
      open,
      openAssistant: () => remember(true),
      closeAssistant: () => remember(false),
      toggleAssistant: () => remember(!open),
    }),
    [open, remember],
  );

  return <AssistantContext.Provider value={value}>{children}</AssistantContext.Provider>;
}

/** Closed by default: the panel takes 400px of the grid, so opening it is the user's decision. */
function readStoredOpen(): boolean {
  try {
    return localStorage.getItem(OPEN_KEY) === "1";
  } catch {
    return false;
  }
}
