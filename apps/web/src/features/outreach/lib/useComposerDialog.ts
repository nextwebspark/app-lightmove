import { useState } from "react";

/**
 * A composer's dialog: filled by the composer, grown in place on Expand, and held open against Escape and a stray click
 * once anything is written — only its close button then leaves.
 */
export function useComposerDialog() {
  const [isLarge, setIsLarge] = useState(false);
  const [hasDraft, setHasDraft] = useState(false);
  return {
    modalProps: {
      fills: true,
      dismissible: !hasDraft,
      className: isLarge ? "md:h-[85dvh] md:w-[880px]" : "md:w-[640px]",
    },
    onExpandChange: setIsLarge,
    onDraftChange: setHasDraft,
    reset: () => {
      setIsLarge(false);
      setHasDraft(false);
    },
  };
}
