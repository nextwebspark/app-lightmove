import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { ASSISTANT_OPEN_KEY } from "./assistantStorage";

/**
 * Whether the assistant is open, and which chat it shows.
 *
 * <p>Mounted above the routes because `ProjectLayout`'s panel remounts as the reader moves between
 * screens, and the open chat should survive that. Open and chat are both per project, so moving to
 * another project neither keeps the panel open nor shows this one's conversation.
 */
type AssistantState = {
  isOpenFor: (projectId: string) => boolean;
  /**
   * Whether the open state came from somebody pressing something rather than from a reload: focus
   * follows a person's action, never a page load.
   */
  toggledByUser: boolean;
  openAssistant: (projectId: string) => void;
  closeAssistant: () => void;
  threadIdFor: (projectId: string) => string | null;
  /** Shows a chat, or `null` for a new one. */
  showThread: (projectId: string, threadId: string | null) => void;
};

const AssistantContext = createContext<AssistantState>({
  isOpenFor: () => false,
  toggledByUser: false,
  openAssistant: () => {},
  closeAssistant: () => {},
  threadIdFor: () => null,
  showThread: () => {},
});

export function useAssistant(): AssistantState {
  return useContext(AssistantContext);
}

export function AssistantProvider({ children }: { children: ReactNode }) {
  const [openProjectId, setOpenProjectId] = useState(readStoredOpenProject);
  const [toggledByUser, setToggledByUser] = useState(false);
  const [threads, setThreads] = useState<Record<string, string | null>>({});

  const remember = useCallback((projectId: string | null) => {
    setOpenProjectId(projectId);
    setToggledByUser(true);
    try {
      if (projectId) localStorage.setItem(ASSISTANT_OPEN_KEY, projectId);
      else localStorage.removeItem(ASSISTANT_OPEN_KEY);
    } catch {
      // A private window refuses this, and the panel opening is not worth failing over.
    }
  }, []);

  const showThread = useCallback((projectId: string, threadId: string | null) => {
    setThreads((current) => ({ ...current, [projectId]: threadId }));
  }, []);

  const value = useMemo<AssistantState>(
    () => ({
      isOpenFor: (projectId) => openProjectId === projectId,
      toggledByUser,
      openAssistant: (projectId) => remember(projectId),
      closeAssistant: () => remember(null),
      threadIdFor: (projectId) => threads[projectId] ?? null,
      showThread,
    }),
    [openProjectId, toggledByUser, threads, remember, showThread],
  );

  return <AssistantContext.Provider value={value}>{children}</AssistantContext.Provider>;
}

/** Closed by default: the panel takes 400px of the page, so opening it is the user's decision. */
function readStoredOpenProject(): string | null {
  try {
    return localStorage.getItem(ASSISTANT_OPEN_KEY);
  } catch {
    return null;
  }
}
