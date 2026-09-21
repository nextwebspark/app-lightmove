import { useEffect, useRef } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useAssistant } from "../AssistantProvider";

/**
 * The way in from any screen, and only while the panel is shut.
 *
 * <p>Bottom-right because the toast holds bottom-centre, and under the toast's `z-[120]` for the
 * same reason: a toast reporting what the assistant just filed has to be readable over it.
 *
 * <p>Hidden below `lg` along with the panel it opens. Below that breakpoint the Strategy filter rail
 * is itself a fixed overlay and two of them cannot share a phone, so the assistant has no layout
 * there yet — and a button that opens nothing is worse than no button.
 */
export function AssistantLauncher() {
  const { open, toggledByUser, openAssistant } = useAssistant();
  const button = useRef<HTMLButtonElement>(null);

  // Closing the panel unmounts it, so without this focus falls to <body> and a keyboard user is left
  // at the top of the document with the whole nav rail to tab through.
  useEffect(() => {
    if (!open && toggledByUser) button.current?.focus();
  }, [open, toggledByUser]);

  if (open) return null;

  return (
    <button
      ref={button}
      type="button"
      onClick={openAssistant}
      title="Ask the assistant"
      className="fixed bottom-5 end-5 z-[110] hidden h-11 lg:inline-flex items-center gap-2.5 rounded-[22px] border border-ai-line bg-[linear-gradient(135deg,var(--color-ai),var(--color-ai2))] pe-[17px] ps-3.5 font-sans text-[13px] font-semibold text-white shadow-[0_8px_24px_-6px_rgba(79,70,229,.5)] transition hover:brightness-110"
    >
      <Icon d={ICONS.sparkle} size={16} className="flex-none" />
      Ask
    </button>
  );
}
