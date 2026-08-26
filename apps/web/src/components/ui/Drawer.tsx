import { useEffect, type ReactNode } from "react";

/** The mockups' right slide-over: floating rounded panel, overlay and Escape to dismiss. */
export function Drawer({
  open,
  onClose,
  children,
}: {
  open: boolean;
  onClose: () => void;
  children: ReactNode;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <>
      <div className="fixed inset-0 z-[90] bg-[rgba(15,20,30,0.4)]" onClick={onClose} />
      <aside className="fixed inset-x-2.5 bottom-2.5 top-14 z-[95] flex animate-fade-up flex-col rounded-[10px] border border-line bg-panel shadow-panel sm:inset-x-auto sm:right-2.5 sm:top-2.5 sm:w-[420px] sm:max-w-[92vw]">
        {children}
      </aside>
    </>
  );
}
