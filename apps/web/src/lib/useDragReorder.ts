import { useState, type DragEvent, type KeyboardEvent } from "react";

/** `items` with the one at `from` moved to `to`, the others keeping their order. */
export function moveItem<T>(items: readonly T[], from: number, to: number): T[] {
  const next = [...items];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

/**
 * Drag-to-reorder over a list's own rows, the browser's drag and drop, with the handle's arrow keys as the
 * keyboard's way to do the same. One instance per list: a row dragged out of its list is never a drop here.
 */
export function useDragReorder(ids: readonly string[], onMove: (from: number, to: number) => void) {
  const [draggingId, setDraggingId] = useState<string | null>(null);

  const rowProps = (id: string) => ({
    draggable: true,
    onDragStart: (event: DragEvent<HTMLElement>) => {
      if (event.target !== event.currentTarget) return;
      event.stopPropagation();
      if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = "move";
        event.dataTransfer.setData("text/plain", id);
      }
      setDraggingId(id);
    },
    onDragOver: (event: DragEvent<HTMLElement>) => {
      if (draggingId === null) return;
      event.preventDefault();
      event.stopPropagation();
    },
    onDrop: (event: DragEvent<HTMLElement>) => {
      if (draggingId === null) return;
      event.preventDefault();
      event.stopPropagation();
      const from = ids.indexOf(draggingId);
      const to = ids.indexOf(id);
      setDraggingId(null);
      if (from >= 0 && to >= 0 && from !== to) onMove(from, to);
    },
    onDragEnd: () => setDraggingId(null),
  });

  const handleKeyDown = (id: string) => (event: KeyboardEvent<HTMLElement>) => {
    const step = event.key === "ArrowUp" ? -1 : event.key === "ArrowDown" ? 1 : 0;
    if (step === 0) return;
    event.preventDefault();
    const from = ids.indexOf(id);
    const to = from + step;
    if (from >= 0 && to >= 0 && to < ids.length) onMove(from, to);
  };

  return { draggingId, rowProps, handleKeyDown };
}
