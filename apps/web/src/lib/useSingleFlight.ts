import { useCallback, useRef } from "react";

/**
 * Runs an async action at most once at a time. A button's loading state disables it only once the next render lands,
 * so the second click of a double click would otherwise send the request again — a 409, or a second invitation.
 */
export function useSingleFlight(): <T>(action: () => Promise<T>) => Promise<T | undefined> {
  const inFlight = useRef(false);
  return useCallback(async <T,>(action: () => Promise<T>) => {
    if (inFlight.current) return undefined;
    inFlight.current = true;
    try {
      return await action();
    } finally {
      inFlight.current = false;
    }
  }, []);
}
