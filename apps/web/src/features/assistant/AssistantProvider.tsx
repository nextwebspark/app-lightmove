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
  /** The conversation in progress. Held here for the reason the open state is: navigation. */
  threadId: string | null;
  turnId: string | null;
  question: string;
  startedTurn: (turn: { id: string; threadId: string; question: string }) => void;
  /**
   * Let go of a remembered conversation the server will not open — one that was deleted, or that
   * belongs to a workspace this user has since left. Nothing else may forget it: a thread is how the
   * panel keeps its place.
   */
  forgetThread: () => void;
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
const THREAD_KEY = "lm.assistant.thread";

const AssistantContext = createContext<AssistantState>({
  open: false,
  threadId: null,
  turnId: null,
  question: "",
  startedTurn: () => {},
  forgetThread: () => {},
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
  // The conversation is remembered; the turn inside it is not. A restored thread id opens no stream
  // — it is read back — while a restored turn id would open one on a turn that is long over.
  const [threadId, setThreadId] = useState(readStoredThread);
  const [turn, setTurn] = useState<{ id: string; threadId: string; question: string } | null>(null);

  const startedTurn = useCallback((started: { id: string; threadId: string; question: string }) => {
    setTurn(started);
    setThreadId(started.threadId);
    try {
      localStorage.setItem(THREAD_KEY, started.threadId);
    } catch {
      // A private window refuses this, and the conversation is still in memory for this session.
    }
  }, []);

  const remember = useCallback((next: boolean) => {
    setOpen(next);
    setToggledByUser(true);
    try {
      localStorage.setItem(OPEN_KEY, next ? "1" : "0");
    } catch {
      // A private window refuses this, and the panel opening is not worth failing over.
    }
  }, []);

  const forgetThread = useCallback(() => {
    setThreadId(null);
    setTurn(null);
    try {
      localStorage.removeItem(THREAD_KEY);
    } catch {
      // Nothing was stored to remove, and the state above is what the panel reads.
    }
  }, []);

  const value = useMemo<AssistantState>(
    () => ({
      open,
      threadId,
      turnId: turn?.id ?? null,
      question: turn?.question ?? "",
      startedTurn,
      forgetThread,
      toggledByUser,
      openAssistant: () => remember(true),
      closeAssistant: () => remember(false),
      toggleAssistant: () => remember(!open),
    }),
    [open, threadId, turn, toggledByUser, startedTurn, forgetThread, remember],
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

/**
 * The conversation the panel was last having, so a reload does not throw it away.
 *
 * <p>What makes this safe where a stored turn id would not be: a thread is fetched, not streamed.
 * A thread that no longer exists answers 404 and the panel falls back to its starters; a stored turn
 * id would instead open a stream on a turn nobody is waiting for.
 */
function readStoredThread(): string | null {
  try {
    return localStorage.getItem(THREAD_KEY);
  } catch {
    return null;
  }
}
