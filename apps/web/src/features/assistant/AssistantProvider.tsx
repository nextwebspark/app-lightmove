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
  /**
   * Whether the panel's current state came from somebody pressing something, rather than from the
   * remembered one being restored. Focus follows a person's action and must not follow a page load:
   * landing on a screen and having the caret yanked into a panel nobody just opened is the bug this
   * exists to avoid.
   */
  toggledByUser: boolean;
  openAssistant: () => void;
  closeAssistant: () => void;
  toggleAssistant: () => void;
};

const OPEN_KEY = "lm.assistant.open";

const AssistantContext = createContext<AssistantState>({
  open: false,
  toggledByUser: false,
  openAssistant: () => {},
  closeAssistant: () => {},
  toggleAssistant: () => {},
});

export function useAssistant(): AssistantState {
  return useContext(AssistantContext);
}

export function AssistantProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(readStoredOpen);
  const [toggledByUser, setToggledByUser] = useState(false);

  const remember = useCallback((next: boolean) => {
    setOpen(next);
    setToggledByUser(true);
    try {
      localStorage.setItem(OPEN_KEY, next ? "1" : "0");
    } catch {
      // A private window refuses this, and the panel opening is not worth failing over.
    }
  }, []);

  const value = useMemo<AssistantState>(
    () => ({
      open,
      toggledByUser,
      openAssistant: () => remember(true),
      closeAssistant: () => remember(false),
      toggleAssistant: () => remember(!open),
    }),
    [open, toggledByUser, remember],
  );

  return <AssistantContext.Provider value={value}>{children}</AssistantContext.Provider>;
}

/**
 * Closed by default: the panel takes 400px of the grid, so opening it is the user's decision.
 *
 * <p>Only the open state is remembered. #432 also asks for a persisted width, and there is
 * deliberately none — the mockup draws one fixed 400px panel with no resize handle, so a stored
 * width would be a setting nothing can change.
 */
function readStoredOpen(): boolean {
  try {
    return localStorage.getItem(OPEN_KEY) === "1";
  } catch {
    return false;
  }
}
