import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { HelpPanel, type HelpSection } from "./components/HelpPanel";
import { latestUnseen } from "./lib/whatsNew";

interface HelpContextValue {
  openHelp: (section?: HelpSection) => void;
  /** Whether something in What's new has not been opened yet: the dot on the Help button. */
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

  useEffect(() => {
    const handleKey = (event: KeyboardEvent) => {
      if (event.key !== "?" || event.metaKey || event.ctrlKey || event.altKey) return;
      if (typingIn(event.target)) return;
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
        <HelpPanel
          initialSection={section}
          onClose={() => setSection(null)}
          onNewsSeen={() => setHasUnseenNews(false)}
        />
      )}
    </HelpContext.Provider>
  );
}

function typingIn(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName);
}
