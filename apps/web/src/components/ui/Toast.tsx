import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";

/**
 * The mockups' bottom-center toast: one at a time, mono text, gone in 2.2 seconds. A new toast
 * replaces the current one rather than stacking.
 */
const ToastContext = createContext<(message: string) => void>(() => {});

export function useToast(): (message: string) => void {
  return useContext(ToastContext);
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);

  const show = useCallback((next: string) => {
    setMessage(next);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setMessage(null), 2200);
  }, []);

  return (
    <ToastContext.Provider value={show}>
      {children}
      {message && (
        <div
          role="status"
          className="fixed bottom-[22px] left-1/2 z-[120] -translate-x-1/2 animate-fade-up rounded-lg border border-line bg-panel px-4 py-[9px] font-mono text-xs font-medium text-text2 shadow-panel"
        >
          {message}
        </div>
      )}
    </ToastContext.Provider>
  );
}
