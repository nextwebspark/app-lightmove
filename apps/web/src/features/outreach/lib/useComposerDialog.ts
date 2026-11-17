import { useState } from "react";
import { cn } from "../../../lib/cn";

/**
 * A composer's dialog: filled by the composer, grown in place on Expand, and held open against Escape and a stray click
 * once anything is written — only its close button then leaves. Its height is fixed in both sizes, so long words
 * scroll inside the editor while the header and the actions stay put. An email opens expanded, and its smaller size
 * is taller than a note's: the signature under the words needs the room.
 */
export function useComposerDialog({ isEmail = false }: { isEmail?: boolean } = {}) {
  const [isLarge, setIsLarge] = useState(isEmail);
  const [hasDraft, setHasDraft] = useState(false);
  return {
    modalProps: {
      fills: true,
      dismissible: !hasDraft,
      className: isLarge
        ? "h-[90dvh] md:w-[880px]"
        : cn("h-[90dvh] md:w-[640px]", isEmail ? "md:h-[min(840px,90dvh)]" : "md:h-[min(600px,85dvh)]"),
    },
    startsLarge: isEmail,
    onExpandChange: setIsLarge,
    onDraftChange: setHasDraft,
    reset: () => {
      setIsLarge(isEmail);
      setHasDraft(false);
    },
  };
}
