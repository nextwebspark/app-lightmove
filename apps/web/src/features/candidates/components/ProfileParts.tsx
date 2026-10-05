import { useLayoutEffect, useRef, useState } from "react";
import type { CandidateEducationEntry } from "../api/types";
import { NetworkMark } from "../../../components/ui/NetworkMark";
import { cn } from "../../../lib/cn";
import { toBrowsableUrl } from "../../../lib/url";

/**
 * The pieces an executive's profile panel is drawn from, shared by the candidate drawer and by
 * Strategy's People preview, so a person reads the same before and after they are filed.
 */

/** LinkedIn's own mark beside the name, through the same guard as the Contact row's link. */
export function HeaderProfileLink({ linkedinUrl }: { linkedinUrl: string | null }) {
  const profileUrl = toBrowsableUrl(linkedinUrl);
  if (!profileUrl) return null;
  return (
    <a
      href={profileUrl}
      target="_blank"
      rel="noreferrer noopener"
      aria-label="LinkedIn profile"
      className="flex flex-none items-center opacity-80 transition hover:opacity-100"
    >
      <NetworkMark network="linkedin" size={16} />
    </a>
  );
}

export function FoldAllButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="font-mono text-[11px] text-u-text3 transition hover:text-u-text"
    >
      {label}
    </button>
  );
}

/** The mockup's language pills, reused for skills: a row of small rounded tags under a tiny label. */
export function PillRow({ label, values, empty }: { label: string; values: readonly string[]; empty?: string }) {
  return (
    <div className="mt-3">
      <div className="mb-1.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-u-text3">
        {label}
      </div>
      {values.length === 0 ? (
        <p className="font-mono text-[12.5px] text-u-text3">{empty}</p>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {values.map((value) => (
            <span
              key={value}
              className="inline-flex items-center rounded-full border border-u-border-strong bg-u-raised px-2.5 py-1 font-mono text-[12px] font-medium text-u-text2"
            >
              {value}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

/** Schools as the profile lists them: name, then degree, then the years. */
export function EducationList({ education }: { education: readonly CandidateEducationEntry[] }) {
  return (
    <ul className="flex flex-col gap-2.5">
      {education.map((school, index) => (
        <li key={`${school.school}-${school.degree}-${index}`}>
          {school.school && <div className="font-sans text-[13px] font-semibold text-u-text">{school.school}</div>}
          {school.degree && <div className="mt-0.5 font-sans text-[13px] text-u-text2">{school.degree}</div>}
          {school.period && <div className="mt-0.5 font-mono text-[11.5px] text-u-text3">{school.period}</div>}
        </li>
      ))}
    </ul>
  );
}

export interface ProfileListItem {
  title: string;
  subtitle: string | null;
  period: string | null;
  url: string | null;
  description: string | null;
}

/** Certifications, publications, projects: the Education list's shape, with a link out when there is one. */
export function ProfileItemList({ items }: { items: readonly ProfileListItem[] }) {
  return (
    <ul className="flex flex-col gap-2.5">
      {items.map((item, index) => {
        const href = toBrowsableUrl(item.url);
        return (
          <li key={`${item.title}-${index}`}>
            <div className="font-sans text-[13px] font-semibold text-u-text">
              {href ? (
                <a href={href} target="_blank" rel="noreferrer noopener" className="hover:underline">
                  {item.title}
                </a>
              ) : (
                item.title
              )}
            </div>
            {item.subtitle && <div className="mt-0.5 font-sans text-[13px] text-u-text2">{item.subtitle}</div>}
            {item.period && <div className="mt-0.5 font-mono text-[11.5px] text-u-text3">{item.period}</div>}
            {item.description && (
              <p className="mt-1 whitespace-pre-line text-[12.5px]/[1.55] text-u-text2">{item.description}</p>
            )}
          </li>
        );
      })}
    </ul>
  );
}

/** A paragraph cut to three lines, with See more only when the text runs past them. */
export function ClampedText({ text, className }: { text: string; className?: string }) {
  const paragraph = useRef<HTMLParagraphElement>(null);
  const [isExpanded, setIsExpanded] = useState(false);
  const [overflows, setOverflows] = useState(false);

  useLayoutEffect(() => {
    const element = paragraph.current;
    if (!element || isExpanded) return;
    const measure = () => setOverflows(element.scrollHeight > element.clientHeight + 1);
    measure();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [text, isExpanded]);

  return (
    <div>
      <p ref={paragraph} className={cn("whitespace-pre-line", !isExpanded && "line-clamp-3", className)}>
        {text}
      </p>
      {(overflows || isExpanded) && (
        <button
          type="button"
          aria-expanded={isExpanded}
          onClick={() => setIsExpanded((current) => !current)}
          className="mt-1 text-note font-medium text-u-accent hover:underline"
        >
          {isExpanded ? "See less" : "See more"}
        </button>
      )}
    </div>
  );
}
