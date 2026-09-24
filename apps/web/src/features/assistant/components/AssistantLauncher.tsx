import { useEffect, useRef } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { useAssistant } from "../AssistantProvider";

/**
 * The way in from any screen, and visible only while the panel is shut.
 *
 * <p>Bottom-right because the toast holds bottom-centre, and under the toast's `z-[120]` for the
 * same reason: a toast reporting what the assistant just filed has to be readable over it.
 *
 * <p>Hidden below `lg` along with the panel it opens. Below that breakpoint the Strategy filter rail
 * is itself a fixed overlay and two of them cannot share a phone, so the assistant has no layout
 * there yet — and a button that opens nothing is worse than no button.
 *
 * <p>Also hidden while any `Drawer` is open, as every mockup does it (`showLauncher: !drawerId`): the
 * pill sits exactly over a drawer's footer action. Read off the DOM rather than threaded through
 * state, because every screen owns its own drawer.
 */
export function AssistantLauncher() {
  const { open, toggledByUser, openAssistant } = useAssistant();
  const button = useRef<HTMLButtonElement>(null);

  // Closing the panel takes focus with it, so without this it falls to <body> and a keyboard user is
  // left at the top of the document with the whole nav rail to tab through.
  useEffect(() => {
    if (!open && toggledByUser) button.current?.focus();
  }, [open, toggledByUser]);

  // Faded rather than unmounted, so it stands aside as the panel arrives instead of blinking out a
  // frame ahead of it. `inert` is what keeps a hidden pill off the Tab order and out of the
  // accessibility tree, which returning null used to do for free.
  return (
    <button
      ref={button}
      type="button"
      onClick={openAssistant}
      title="Ask the assistant"
      aria-hidden={open}
      inert={open}
      className={cn(
        "fixed bottom-5 end-5 z-[110] hidden h-11 lg:inline-flex [body:has([data-drawer])_&]:!hidden items-center gap-2.5 rounded-[22px] border border-ai-line bg-[linear-gradient(135deg,var(--color-ai),var(--color-ai2))] pe-[17px] ps-3.5 font-sans text-[13px] font-semibold text-white shadow-[0_8px_24px_-6px_rgba(79,70,229,.5)] transition duration-200 hover:brightness-110 motion-reduce:transition-none",
        open ? "pointer-events-none scale-95 opacity-0" : "scale-100 opacity-100",
      )}
    >
      <Icon d={ICONS.sparkle} size={16} className="flex-none" />
      Ask
    </button>
  );
}
