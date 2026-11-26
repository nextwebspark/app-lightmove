import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";

const SidebarYieldContext = createContext<{ claims: number; claim: (delta: number) => void }>({
  claims: 0,
  claim: () => {},
});

export function SidebarYieldProvider({ children }: { children: ReactNode }) {
  const [claims, setClaims] = useState(0);
  const claim = useCallback((delta: number) => setClaims((current) => current + delta), []);
  return <SidebarYieldContext.Provider value={{ claims, claim }}>{children}</SidebarYieldContext.Provider>;
}

/** Whether a screen below has asked the rail to fold out of its way. */
export function useSidebarYielded(): boolean {
  return useContext(SidebarYieldContext).claims > 0;
}

/** Folds the rail while `active`, without touching the stored preference: a screen that needs the width asks. */
export function useSidebarYield(active: boolean) {
  const { claim } = useContext(SidebarYieldContext);
  useEffect(() => {
    if (!active) return;
    claim(1);
    return () => claim(-1);
  }, [active, claim]);
}
