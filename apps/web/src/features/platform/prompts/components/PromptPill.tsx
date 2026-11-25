import type { ReactNode } from "react";
import { cn } from "../../../../lib/cn";

type PillTone = "plain" | "live" | "edited" | "warning" | "danger";

const TONE_CLASS: Record<PillTone, string> = {
  plain: "border border-u-border-strong text-u-text3",
  live: "bg-u-direct-tint text-u-direct",
  edited: "bg-u-inferred-tint text-u-inferred",
  warning: "bg-u-accent-tint text-u-accent",
  danger: "bg-u-offlimits-tint text-u-offlimits",
};

/** The small state pills of the Prompts screens: Live, Edited, Shipped, a warning, a retiring model. */
export function PromptPill({ tone, title, children }: { tone: PillTone; title?: string; children: ReactNode }) {
  return (
    <span title={title} className={cn("flex-none whitespace-nowrap rounded-full px-2 py-0.5 type-tag", TONE_CLASS[tone])}>
      {children}
    </span>
  );
}

export function OriginPill({ origin }: { origin: "BUNDLED" | "EDITED" }) {
  return origin === "EDITED" ? <PromptPill tone="edited">Edited</PromptPill> : <PromptPill tone="plain">Shipped</PromptPill>;
}
