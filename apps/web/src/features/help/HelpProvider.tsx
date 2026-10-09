import { createContext, lazy, Suspense, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { HelpSection } from "./lib/helpSection";
import { latestUnseen } from "./lib/whatsNew";

// Its own chunk: the panel brings the Markdown renderer, which nothing else on first load needs.
const HelpPanel = lazy(() => import("./components/HelpPanel"));

interface HelpContextValue {
  openHelp: (section?: HelpSection) => void;
  /** Whether something in What's new has not been seen yet: the dot on the Help button. */
  hasUnseenNews: boolean;
}

const HelpContext = createContext<HelpContextValue>({ openHelp: () => {}, hasUnseenNews: false });

export function useHelp(): HelpContextValue {
  return useContext(HelpContext);
}

/** The Help panel and its `?` shortcut, for every signed-in screen. */
export function HelpProvider({ children }: { children: ReactNode }) {
  const [section, setSection] = useState<HelpSection | null>(null);
  const [hasUnseenNews, setHasUnseenNews] = useState(latestUnseen);

  const openHelp = useCallback((next: HelpSection = "home") => setSection(next), []);
  const closeHelp = useCallback(() => setSection(null), []);
  const markNewsSeen = useCallback(() => setHasUnseenNews(false), []);

  useEffect(() => {
    const handleKey = (event: KeyboardEvent) => {
      if (event.key !== "?" || event.metaKey || event.ctrlKey || event.altKey) return;
      if (event.defaultPrevented || event.repeat || typingIn(event.target)) return;
      // A dialog already open keeps the keyboard: Help would land beneath it and take its Escape.
      if (document.querySelector('[aria-modal="true"]')) return;
      event.preventDefault();
      setSection("home");
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, []);

  const value = useMemo(() => ({ openHelp, hasUnseenNews }), [openHelp, hasUnseenNews]);

  return (
    <HelpContext.Provider value={value}>
      {children}
      {section && (
        <Suspense fallback={null}>
          <HelpPanel initialSection={section} onClose={closeHelp} onNewsSeen={markNewsSeen} />
        </Suspense>
      )}
    </HelpContext.Provider>
  );
}

function typingIn(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName);
}
