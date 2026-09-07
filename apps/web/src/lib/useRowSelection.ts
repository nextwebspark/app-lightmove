import { useCallback, useMemo, useRef, useState } from "react";

/** A stable empty set, so "nothing selected" is one identity rather than a new object per render. */
const NOTHING: ReadonlySet<string> = new Set();

export interface RowSelection {
  ids: ReadonlySet<string>;
  count: number;
  /** Whether every row on the current page is selected — the select-all box's checked state. */
  allOnPage: boolean;
  /** Partly selected: the select-all box's `"mixed"`. */
  someOnPage: boolean;
  has: (id: string) => boolean;
  /** `extend` is a shift-click: everything between the last box touched and this one takes its state. */
  toggle: (id: string, extend?: boolean) => void;
  toggleAllOnPage: () => void;
  clear: () => void;
}

/**
 * Which rows of a paged grid are ticked.
 *
 * <p>Ids, not rows: a selection outlives the page it was made on, so holding the row objects would
 * mean holding a copy of data the query cache already owns and re-rendering the whole grid whenever
 * it refetched. What the actions need is the identity, and what the bar needs is the count.
 *
 * <p>The selection deliberately survives paging — picking twelve companies across three pages is the
 * case bulk actions exist for — and just as deliberately does not survive a change to what is being
 * asked. The caller clears it when the filter, the search or the ordering moves, because a tick left
 * over from a scope the user has since abandoned would act on a company they can no longer see.
 */
export function useRowSelection(pageIds: readonly string[]): RowSelection {
  const [ids, setIds] = useState<ReadonlySet<string>>(NOTHING);
  // The last box touched, for shift-click. A ref rather than state: it changes on every click and
  // nothing renders from it.
  const anchorRef = useRef<string | null>(null);

  const has = useCallback((id: string) => ids.has(id), [ids]);

  const toggle = useCallback(
    (id: string, extend = false) => {
      const anchor = anchorRef.current;
      anchorRef.current = id;
      setIds((current) => {
        const selecting = !current.has(id);
        const next = new Set(current);
        // A range needs both ends on the page in front of the user. Shift-clicking after paging has
        // an anchor that is nowhere on screen, and painting rows the user cannot see is worse than
        // treating it as an ordinary click.
        const from = anchor === null ? -1 : pageIds.indexOf(anchor);
        const to = pageIds.indexOf(id);
        const span =
          extend && from !== -1 && to !== -1
            ? pageIds.slice(Math.min(from, to), Math.max(from, to) + 1)
            : [id];
        span.forEach((each) => (selecting ? next.add(each) : next.delete(each)));
        return next;
      });
    },
    [pageIds],
  );

  const toggleAllOnPage = useCallback(() => {
    anchorRef.current = null;
    setIds((current) => {
      const next = new Set(current);
      const selecting = pageIds.some((id) => !current.has(id));
      pageIds.forEach((id) => (selecting ? next.add(id) : next.delete(id)));
      return next;
    });
  }, [pageIds]);

  const clear = useCallback(() => {
    anchorRef.current = null;
    setIds(NOTHING);
  }, []);

  return useMemo(
    () => ({
      ids,
      count: ids.size,
      allOnPage: pageIds.length > 0 && pageIds.every((id) => ids.has(id)),
      someOnPage: pageIds.some((id) => ids.has(id)),
      has,
      toggle,
      toggleAllOnPage,
      clear,
    }),
    [ids, pageIds, has, toggle, toggleAllOnPage, clear],
  );
}
