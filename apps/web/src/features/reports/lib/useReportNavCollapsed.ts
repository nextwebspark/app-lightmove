import { useCallback, useState } from "react";

const STORAGE_KEY = "lm-report-nav-collapsed";

/** Whether the report's chapter rail is folded to its icons, remembered across chapters and visits. */
export function useReportNavCollapsed(): { collapsed: boolean; toggle: () => void } {
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
