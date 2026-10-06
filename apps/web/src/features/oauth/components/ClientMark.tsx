import { useState } from "react";
import { ChatGptMark, ClaudeMark } from "../../../components/ui/BrandMarks";
import { cn } from "../../../lib/cn";
import type { ConsentContext } from "../api/types";
import { clientLogoOf } from "../lib/consentRequest";

/** The app's tile: what `clientLogoOf` allows, or its letter. Decorative — the name sits beside it. */
export function ClientMark({ context }: { context: ConsentContext }) {
  const [isBroken, setIsBroken] = useState(false);
  const logo = clientLogoOf(context);
  const shown = logo?.kind === "url" && isBroken ? null : logo;
  return (
    <span
      aria-hidden="true"
      data-mark={shown === null ? "letter" : shown.kind === "mark" ? shown.mark : "logo"}
      className={cn(
        "grid size-11 flex-none place-items-center overflow-hidden rounded-[10px] text-lg font-bold",
        context.verified
          ? "border border-u-border-strong bg-u-raised text-u-text"
          : "border border-dashed border-u-signal bg-u-signal-tint text-u-signal",
      )}
    >
      {shown?.kind === "mark" ? (
        shown.mark === "claude" ? (
          <ClaudeMark size={26} />
        ) : (
          <ChatGptMark size={26} />
        )
      ) : shown?.kind === "url" ? (
        <img
          src={shown.url}
          alt=""
          referrerPolicy="no-referrer"
          className="size-full object-contain p-1.5"
          onError={() => setIsBroken(true)}
        />
      ) : (
        context.clientName.trim().charAt(0).toUpperCase() || "?"
      )}
    </span>
  );
}
