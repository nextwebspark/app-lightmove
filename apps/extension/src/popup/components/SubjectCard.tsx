import type { ReactNode } from "react";
import { cn } from "../lib/cn";
import { ICONS } from "../lib/icons";
import { initials } from "../lib/initials";
import { Icon } from "./Icon";
import { LogoTile } from "./LogoTile";
import { LinkedInMark } from "./NetworkMark";

/**
 * Who or what the page is about: their photo or the company's logo, the name — locked while it is what the page said,
 * typeable when the page named nobody — a headline, and the LinkedIn address it was read off.
 */
export function SubjectCard({
  shape,
  name,
  isNameLocked,
  onNameChange,
  headline,
  linkedinUrl,
  imageSrc,
  isImagePending = false,
  isReading = false,
  onRescan,
  employerLogo,
  children,
}: {
  shape: "person" | "company";
  name: string;
  isNameLocked: boolean;
  onNameChange?: (name: string) => void;
  headline?: string | null;
  linkedinUrl: string | null;
  imageSrc: string | null | undefined;
  isImagePending?: boolean;
  isReading?: boolean;
  onRescan?: () => void;
  /** A person's employer, drawn small beside their headline. */
  employerLogo?: { name: string; src: string | null } | null;
  children?: ReactNode;
}) {
  const label = shape === "person" ? "Full name" : "Company name";
  return (
    <div className="flex items-start gap-3 rounded-[10px] border border-u-border bg-u-raised px-3 py-[11px]">
      <span className="relative flex-none">
        {shape === "person" ? (
          imageSrc ? (
            <img src={imageSrc} alt={name} className="size-11 rounded-full object-cover" />
          ) : (
            <span
              className={cn(
                "grid size-11 place-items-center rounded-full bg-u-accent-tint font-mono text-[14px] font-bold text-u-accent",
                isReading && "animate-pulse",
              )}
            >
              {name.trim() ? initials(name) : ""}
            </span>
          )
        ) : (
          <LogoTile name={name || "?"} src={imageSrc} size="lg" className={cn("rounded-[9px]", isReading && "animate-pulse")} />
        )}
        {isImagePending && (
          <span
            title="Fetching the profile photo"
            className="absolute -bottom-0.5 -right-0.5 grid size-[15px] place-items-center rounded-full border-2 border-u-raised bg-u-surface"
          >
            <span className="size-2 animate-spin rounded-full border-[1.5px] border-u-border-strong border-t-u-accent" />
          </span>
        )}
      </span>
      <span className="min-w-0 flex-1">
        {isNameLocked ? (
          <span title="Read from the page" className="block truncate text-[14.5px] font-semibold leading-[1.3] text-u-text">
            {name}
          </span>
        ) : (
          <input
            value={name}
            onChange={(event) => onNameChange?.(event.target.value)}
            aria-label={label}
            placeholder={label}
            className="block w-full rounded-u-control border border-u-border-strong bg-u-surface px-2 py-[5px] text-[13.5px] font-medium text-u-text outline-none focus:border-u-accent"
          />
        )}
        {headline && (
          <span className="mt-px flex min-w-0 items-center gap-1.5 text-[12px] leading-[1.4] text-u-text2">
            {employerLogo && <LogoTile name={employerLogo.name} src={employerLogo.src} size="sm" className="size-4 rounded-[4px] text-[7px]" />}
            <span className="truncate">{headline}</span>
          </span>
        )}
        {linkedinUrl && (
          <span className="mt-0.5 flex min-w-0 items-center gap-1.5 font-mono text-[11px] text-u-text3">
            <LinkedInMark size={11} />
            <span className="truncate">{displayAddress(linkedinUrl)}</span>
          </span>
        )}
        {children}
      </span>
      {onRescan && (
        <button
          type="button"
          onClick={onRescan}
          title="Read the page again"
          aria-label="Read the page again"
          className="grid size-[26px] flex-none place-items-center rounded-u-control border border-u-border-strong text-u-text3 transition hover:text-u-text"
        >
          <Icon d={ICONS.refresh} size={12} />
        </button>
      )}
    </div>
  );
}

function displayAddress(url: string): string {
  return url.replace(/^https?:\/\/(www\.)?/i, "").replace(/\/$/, "");
}
