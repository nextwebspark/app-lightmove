import { createContext, useContext, useEffect } from "react";

/** The console shell names the open page in its breadcrumb; the page tells it what to say. */
export const PlatformCrumbContext = createContext<(page: string | null) => void>(() => {});

export function usePlatformPageCrumb(page: string | null) {
  const setPage = useContext(PlatformCrumbContext);
  useEffect(() => {
    setPage(page);
    return () => setPage(null);
  }, [page, setPage]);
}
