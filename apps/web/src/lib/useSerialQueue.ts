import { useCallback, useRef } from "react";

/**
 * Runs the async calls it is given one at a time, in the order they were given. A call that fails still
 * lets the next one run: the chain swallows the failure, while the caller's own promise rejects with it.
 */
export function useSerialQueue() {
  const tail = useRef<Promise<unknown>>(Promise.resolve());
  return useCallback(<T,>(call: () => Promise<T>): Promise<T> => {
    const queued = tail.current.then(call);
    tail.current = queued.catch(() => undefined);
    return queued;
  }, []);
}
