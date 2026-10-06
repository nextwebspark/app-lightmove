import { useState } from "react";
import { ChatGptMark, ClaudeMark } from "../../../components/ui/BrandMarks";
import { cn } from "../../../lib/cn";
import type { ClientIdentity } from "../api/types";
import { clientLogoOf } from "../lib/consentRequest";

/** The app's tile: what `clientLogoOf` allows, or its letter. Decorative — the name sits beside it. */
export function ClientMark({ client: context, size = "lg" }: { client: ClientIdentity; size?: "md" | "lg" }) {
  const [isBroken, setIsBroken] = useState(false);
  const logo = clientLogoOf(context);
  const shown = logo?.kind === "url" && isBroken ? null : logo;
  return (
    <span
      aria-hidden="true"
      data-mark={shown === null ? "letter" : shown.kind === "mark" ? shown.mark : "logo"}
      className={cn(
        "grid flex-none place-items-center overflow-hidden font-bold",
        size === "lg" ? "size-11 rounded-[10px] text-lg" : "size-9 rounded-lg text-[13px]",
        context.verified
          ? "border border-u-border-strong bg-u-raised text-u-text"
          : "border border-dashed border-u-signal bg-u-signal-tint text-u-signal",
      )}
    >
      {shown?.kind === "mark" ? (
        shown.mark === "claude" ? (
          <ClaudeMark size={size === "lg" ? 26 : 20} />
        ) : (
          <ChatGptMark size={size === "lg" ? 26 : 20} />
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
