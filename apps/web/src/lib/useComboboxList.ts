import { useEffect, useRef, useState } from "react";

/** How long a pause in typing must last before a server-backed list queries. */
export const DEBOUNCE_MS = 250;

/** The value, held still until typing pauses. */
export function useDebouncedValue<T>(value: T, delayMs = DEBOUNCE_MS): T {
  const [settled, setSettled] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return settled;
}

/**
 * The open/active/blur mechanics every picker over the universe shares. What each renders differs —
 * pills and counts here, logos and a location line there — but a list that closed on a different beat
 * from its neighbour would be a bug in one of them.
 */
export function useComboboxList({
  optionCount,
  onCommit,
  autoHighlightFirst = true,
}: {
  optionCount: number;
  onCommit: (index: number) => void;
  /**
   * Whether the first option starts highlighted, so Enter commits it. True for a picker whose input
   * exists only to search. False where the input is itself the value — the role title accepts "Group
   * CFO – Energy Division", and Enter on that must not commit the CFO suggestion sitting under it.
   */
  autoHighlightFirst?: boolean;
}) {
  const none = autoHighlightFirst ? 0 : -1;
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(none);
  const blurTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (blurTimer.current) clearTimeout(blurTimer.current);
    },
    [],
  );

  const commit = (index: number) => {
    onCommit(index);
    setActive(none);
    // Left open, the list covers the very rows it just added to.
    setOpen(false);
  };

  return {
    open,
    active,
    setActive,
    setOpen,
    /** Commits before the input's blur fires and closes the list. */
    commitFromPointer: (event: React.MouseEvent, index: number) => {
      event.preventDefault();
      if (blurTimer.current) clearTimeout(blurTimer.current);
      commit(index);
    },
    inputHandlers: {
      onFocus: () => setOpen(true),
      onBlur: () => {
        blurTimer.current = setTimeout(() => setOpen(false), 120);
      },
      onKeyDown: (event: React.KeyboardEvent<HTMLInputElement>) => {
        if (event.key === "ArrowDown") {
          event.preventDefault();
          setOpen(true);
          setActive((index) => Math.min(index + 1, optionCount - 1));
        } else if (event.key === "ArrowUp") {
          event.preventDefault();
          setActive((index) => Math.max(index - 1, none));
        } else if (event.key === "Enter") {
          if (active < 0 || optionCount === 0) return;
          event.preventDefault();
          commit(active);
        } else if (event.key === "Escape") {
          // Consumed while the list is open: inside a modal, Escape must close the innermost
          // surface first, not the dialog around it.
          if (open) event.stopPropagation();
          setOpen(false);
        }
      },
    },
    cancelBlur: () => {
      if (blurTimer.current) clearTimeout(blurTimer.current);
    },
  };
}
