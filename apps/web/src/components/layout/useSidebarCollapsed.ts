import { useCallback, useState } from "react";

/** The mockups' key, so a sidebar collapsed there stays collapsed here. */
const STORAGE_KEY = "lm-side-collapsed";

export function useSidebarCollapsed(): { collapsed: boolean; toggle: () => void } {
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) === "1";
    } catch {
      return false;
    }
  });

  const toggle = useCallback(() => {
    setCollapsed((current) => {
      try {
        localStorage.setItem(STORAGE_KEY, current ? "0" : "1");
      } catch {
        // Private mode: the preference just doesn't survive the tab.
      }
      return !current;
    });
  }, []);

  return { collapsed, toggle };
}
