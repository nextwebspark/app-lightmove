import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { PanelToastContext } from "../lib/panelToast";

interface ToastMessage {
  id: number;
  text: string;
  undo?: () => void;
}

const TOAST_LINGER_MS = 6000;

/** One toast at a time at the foot of the panel, with Undo where the action can be taken back. */
export function PanelToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const counter = useRef(0);
  const show = useCallback((text: string, undo?: () => void) => {
    counter.current += 1;
    setToast({ id: counter.current, text, undo });
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast((current) => (current?.id === toast.id ? null : current)), TOAST_LINGER_MS);
    return () => clearTimeout(timer);
  }, [toast]);

  return (
    <PanelToastContext.Provider value={show}>
      {children}
      {toast && (
        <div
          role="status"
          className="animate-toast-in fixed inset-x-3.5 bottom-3.5 z-[60] flex items-center gap-3 rounded-[8px] bg-u-text px-3 py-2.5 text-[12.5px] font-medium text-u-bg shadow-u-e3"
        >
          <span className="flex-1">{toast.text}</span>
          {toast.undo && (
            <button
              type="button"
              onClick={() => {
                toast.undo?.();
                setToast(null);
              }}
              className="font-semibold text-u-accent-solid hover:underline"
            >
              Undo
            </button>
          )}
        </div>
      )}
    </PanelToastContext.Provider>
  );
}
