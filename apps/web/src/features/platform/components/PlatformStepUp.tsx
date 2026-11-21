import { lazy, Suspense, useEffect, useRef, useState } from "react";
import { onStepUpRequired } from "../../../lib/apiClient";

const PlatformStepUpDialog = lazy(() => import("./PlatformStepUpDialog"));

interface Waiting {
  promise: Promise<void>;
  resolve: () => void;
  reject: (reason: Error) => void;
}

/**
 * Asks for the fresh two-factor code a high-impact platform action needs, from any request on any screen. Mounted
 * once, above the routes: the request that was refused waits here and is sent again once the code is taken.
 */
export function PlatformStepUp() {
  const waiting = useRef<Waiting | null>(null);
  const [open, setOpen] = useState(false);

  useEffect(
    () =>
      onStepUpRequired(() => {
        // Two requests refused together share one dialog and one code.
        if (!waiting.current) {
          let resolve!: () => void;
          let reject!: (reason: Error) => void;
          const promise = new Promise<void>((onResolve, onReject) => {
            resolve = onResolve;
            reject = onReject;
          });
          waiting.current = { promise, resolve, reject };
          setOpen(true);
        }
        return waiting.current.promise;
      }),
    [],
  );

  const finish = (settle: (pending: Waiting) => void) => {
    const pending = waiting.current;
    waiting.current = null;
    setOpen(false);
    if (pending) settle(pending);
  };

  if (!open) return null;
  return (
    <Suspense fallback={null}>
      <PlatformStepUpDialog
        onConfirmed={() => finish((pending) => pending.resolve())}
        onClose={() => finish((pending) => pending.reject(new Error("The code was not entered")))}
      />
    </Suspense>
  );
}
