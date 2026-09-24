import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";

/**
 * Whether the assistant is open, and which chat it shows.
 *
 * <p>Mounted above the routes because `ProjectLayout`'s panel remounts as the reader moves between
 * screens, and the open chat should survive that. The chat is remembered per project, so opening
 * the assistant in another project never shows this one's conversation.
 */
type AssistantState = {
  open: boolean;
  /**
   * Whether the open state came from somebody pressing something rather than from a reload: focus
   * follows a person's action, never a page load.
   */
  toggledByUser: boolean;
  openAssistant: () => void;
  closeAssistant: () => void;
  toggleAssistant: () => void;
  threadIdFor: (projectId: string) => string | null;
  /** Shows a chat, or `null` for a new one. */
  showThread: (projectId: string, threadId: string | null) => void;
};

const OPEN_KEY = "lm.assistant.open";

const AssistantContext = createContext<AssistantState>({
  open: false,
  toggledByUser: false,
  openAssistant: () => {},
  closeAssistant: () => {},
  toggleAssistant: () => {},
  threadIdFor: () => null,
  showThread: () => {},
});

export function useAssistant(): AssistantState {
  return useContext(AssistantContext);
}

export function AssistantProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(readStoredOpen);
  const [toggledByUser, setToggledByUser] = useState(false);
  const [threads, setThreads] = useState<Record<string, string | null>>({});

  const remember = useCallback((next: boolean) => {
    setOpen(next);
    setToggledByUser(true);
    try {
      localStorage.setItem(OPEN_KEY, next ? "1" : "0");
    } catch {
      // A private window refuses this, and the panel opening is not worth failing over.
    }
  }, []);

  const showThread = useCallback((projectId: string, threadId: string | null) => {
    setThreads((current) => ({ ...current, [projectId]: threadId }));
  }, []);

  const value = useMemo<AssistantState>(
    () => ({
      open,
      toggledByUser,
      openAssistant: () => remember(true),
      closeAssistant: () => remember(false),
      toggleAssistant: () => remember(!open),
      threadIdFor: (projectId) => threads[projectId] ?? null,
      showThread,
    }),
    [open, toggledByUser, threads, remember, showThread],
  );

  return <AssistantContext.Provider value={value}>{children}</AssistantContext.Provider>;
}

/** Closed by default: the panel takes 400px of the page, so opening it is the user's decision. */
function readStoredOpen(): boolean {
  try {
    return localStorage.getItem(OPEN_KEY) === "1";
  } catch {
    return false;
  }
}
