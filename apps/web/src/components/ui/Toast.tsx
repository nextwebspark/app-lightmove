import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { ICONS, Icon } from "../layout/Icon";
import { cn } from "../../lib/cn";

export type ToastKind = "info" | "success" | "error";

export interface ToastFn {
  /** The plain notice every older call site sends; the same as `toast.info`. */
  (message: string): void;
  info(message: string): void;
  /** `undo` adds an Undo button and keeps the toast up longer, so there is time to reach it. */
  success(message: string, options?: { undo?: () => void }): void;
  /** Stays until dismissed or until `retry` is pressed. */
  error(message: string, options?: { retry?: () => void }): void;
}

export const TOAST_LIMIT = 3;
export const TOAST_DURATION_MS = 4_000;
export const TOAST_WITH_UNDO_DURATION_MS = 6_000;

interface ToastAction {
  label: "Undo" | "Retry";
  run: () => void;
}

interface ToastItem {
  id: number;
  kind: ToastKind;
  message: string;
  action: ToastAction | null;
  /** Bumped when the same message is sent again, so its timer starts over. */
  shownAt: number;
}

const noop = () => {};
const silentToast: ToastFn = Object.assign((_message: string) => {}, { info: noop, success: noop, error: noop });

const ToastContext = createContext<ToastFn>(silentToast);

export function useToast(): ToastFn {
  return useContext(ToastContext);
}

/**
 * The mockups' bottom-centre toast, grown into a stack of up to three: a success or notice leaves on
 * its own (later when it offers Undo), an error stays until it is dismissed, and hovering or focusing
 * a toast holds it.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(0);

  const dismiss = useCallback((id: number) => setToasts((current) => current.filter((t) => t.id !== id)), []);

  const toast = useMemo(() => {
    const show = (kind: ToastKind, message: string, action: ToastAction | null) =>
      setToasts((current) => {
        const shownAt = Date.now();
        // An action keeps its own toast: two Undos for two moves must each undo their own move.
        const repeat = action === null ? current.find((t) => t.kind === kind && t.message === message && !t.action) : undefined;
        if (repeat) return current.map((t) => (t === repeat ? { ...t, shownAt } : t));
        nextId.current += 1;
        return [...current, { id: nextId.current, kind, message, action, shownAt }].slice(-TOAST_LIMIT);
      });
    const info = (message: string) => show("info", message, null);
    return Object.assign(info, {
      info,
      success: (message: string, options?: { undo?: () => void }) =>
        show("success", message, options?.undo ? { label: "Undo", run: options.undo } : null),
      error: (message: string, options?: { retry?: () => void }) =>
        show("error", message, options?.retry ? { label: "Retry", run: options.retry } : null),
    }) satisfies ToastFn;
  }, []);

  return (
    <ToastContext.Provider value={toast}>
      {children}
      {toasts.length > 0 && (
        <div className="fixed bottom-[22px] left-1/2 z-[120] flex w-max max-w-[calc(100vw-24px)] -translate-x-1/2 flex-col items-center gap-2">
          {toasts.map((item) => (
            <ToastCard key={item.id} item={item} onDismiss={dismiss} />
          ))}
        </div>
      )}
    </ToastContext.Provider>
  );
}

function ToastCard({ item, onDismiss }: { item: ToastItem; onDismiss: (id: number) => void }) {
  const [isHeld, setIsHeld] = useState(false);
  const isError = item.kind === "error";
  const duration = item.action ? TOAST_WITH_UNDO_DURATION_MS : TOAST_DURATION_MS;
  const remaining = useRef(duration);
  const startedAt = useRef(0);

  useEffect(() => {
    remaining.current = duration;
  }, [item.shownAt, duration]);

  useEffect(() => {
    if (isError || isHeld) return;
    startedAt.current = Date.now();
    const timer = setTimeout(() => onDismiss(item.id), remaining.current);
    return () => {
      clearTimeout(timer);
      remaining.current = Math.max(0, remaining.current - (Date.now() - startedAt.current));
    };
  }, [isError, isHeld, item.id, item.shownAt, onDismiss]);

  const handleAction = () => {
    onDismiss(item.id);
    item.action?.run();
  };

  return (
    <div
      role={isError ? "alert" : "status"}
      onMouseEnter={() => setIsHeld(true)}
      onMouseLeave={() => setIsHeld(false)}
      onFocus={() => setIsHeld(true)}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setIsHeld(false);
      }}
      className={cn(
        "flex max-w-full animate-fade-up items-center gap-2.5 rounded-lg border bg-u-surface px-4 py-[9px] font-mono text-xs font-medium shadow-u-e3",
        isError ? "border-u-offlimits/60 text-u-offlimits" : "border-u-border-strong text-u-text2",
      )}
    >
      {item.kind === "success" && <Icon d={ICONS.checkCircle} size={14} className="shrink-0 text-u-direct" />}
      {isError && <Icon d={ICONS.warning} size={14} className="shrink-0" />}
      <span className="min-w-0">{item.message}</span>
      {item.action && (
        <button
          type="button"
          onClick={handleAction}
          className="shrink-0 rounded px-1 font-semibold text-u-accent underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-u-accent"
        >
          {item.action.label}
        </button>
      )}
      {isError && (
        <button
          type="button"
          aria-label="Dismiss"
          onClick={() => onDismiss(item.id)}
          className="-mr-1 shrink-0 rounded p-0.5 text-u-text3 hover:text-u-text focus-visible:outline-2 focus-visible:outline-u-accent"
        >
          <Icon d={ICONS.close} size={13} />
        </button>
      )}
    </div>
  );
}
