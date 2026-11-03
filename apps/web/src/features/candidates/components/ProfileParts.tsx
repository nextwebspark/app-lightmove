import { useLayoutEffect, useRef, useState, type ReactNode } from "react";
import type { CandidateContacts, CandidateEducationEntry } from "../api/types";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { CompanyLogo } from "../../../components/ui";
import { NetworkMark } from "../../../components/ui/NetworkMark";
import { cn } from "../../../lib/cn";
import { toBrowsableUrl } from "../../../lib/url";
import { SectionEditButton } from "./ProfileSectionForm";
import { FITTING_PILL, LEFT_OUT_PILL, useFittingCount } from "../../../lib/useFittingCount";

/**
 * The pieces an executive's profile panel is drawn from, shared by the candidate drawer and by
 * Strategy's People preview, so a person reads the same before and after they are filed.
 */

/** LinkedIn's own mark beside the name — the one place a drawer shows the profile link. */
function HeaderProfileLink({ linkedinUrl }: { linkedinUrl: string | null }) {
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

/** Who they work for as the header names them; `onOpen` opens that company's own drawer where the page has one. */
export interface HeaderEmployer {
  name: string | null;
  logoUrl?: string | null;
  onOpen?: () => void;
}

/**
 * Both person drawers' header: the photo, then one column for who they are — name, title, employer and location —
 * then, set apart, how to reach them, and everything the drawer puts under them, so the two headers cannot drift
 * apart.
 */
export function PersonHeaderLayout({
  avatar,
  fullName,
  linkedinUrl,
  nameAction,
  title,
  contacts,
  onEditContacts,
  onShowContacts,
  onWriteEmail,
  emailNote,
  employer,
  location,
  children,
}: {
  avatar: ReactNode;
  fullName: string;
  linkedinUrl: string | null;
  nameAction?: ReactNode;
  title: string | null;
  contacts: CandidateContacts;
  /** Opens the contact details window: the pencil, "+N" and the Add links. Absent for a reader who may not edit. */
  onEditContacts?: () => void;
  /** Where "+N" goes when nothing may be edited here: the Contact tab. */
  onShowContacts?: () => void;
  /** Writes to the primary email in Uncava's composer; absent leaves the address as text, `emailNote` saying why. */
  onWriteEmail?: () => void;
  emailNote?: string;
  employer: HeaderEmployer;
  location: string;
  children: ReactNode;
}) {
  return (
    <div className="group flex items-start gap-3">
      <div className="flex flex-none">{avatar}</div>
      <div className="min-w-0 flex-1">
        <div className="pe-8">
          <span className="flex min-w-0 items-center gap-1.5">
            <h2 className="truncate font-sans text-base font-semibold text-u-text">{fullName}</h2>
            <HeaderProfileLink linkedinUrl={linkedinUrl} />
            {nameAction}
          </span>
          <p className="mt-0.5 truncate font-mono text-note text-u-text2">{title ?? "—"}</p>
          <HeaderEmployerLine employer={employer} location={location} />
          <HeaderContacts
            contacts={contacts}
            onEditContacts={onEditContacts}
            onShowContacts={onShowContacts}
            onWriteEmail={onWriteEmail}
            emailNote={emailNote}
          />
        </div>
        {children}
      </div>
    </div>
  );
}

/**
 * The primary email and phone — the server lists each channel's first — then "+N" for the rest and the pencil.
 * The email writes in Uncava's own composer where `onWriteEmail` is given; a phone is only read. A channel with
 * nothing on record offers to add one to whoever may edit.
 */
function HeaderContacts({
  contacts,
  onEditContacts,
  onShowContacts,
  onWriteEmail,
  emailNote,
}: {
  contacts: CandidateContacts;
  onEditContacts?: () => void;
  onShowContacts?: () => void;
  onWriteEmail?: () => void;
  emailNote?: string;
}) {
  const email = contacts.emails[0]?.address;
  const phone = contacts.phones[0]?.number;
  if (!email && !phone && !onEditContacts) return null;
  const more = Math.max(contacts.emails.length - 1, 0) + Math.max(contacts.phones.length - 1, 0);
  const onMore = onEditContacts ?? onShowContacts;
  return (
    <div className="mt-3 flex min-w-0 flex-wrap items-center gap-x-4 gap-y-1 font-mono text-meta text-u-text2">
      {email ? (
        <HeaderContactValue icon={ICONS.mail}>
          {onWriteEmail ? (
            <button
              type="button"
              onClick={onWriteEmail}
              aria-label={`Write email to ${email}`}
              title="Write email"
              className="min-w-0 truncate transition hover:text-u-accent hover:underline"
            >
              {email}
            </button>
          ) : (
            <span title={emailNote} className="min-w-0 truncate">
              {email}
            </span>
          )}
        </HeaderContactValue>
      ) : (
        onEditContacts && <AddContactLink label="Add email" onClick={onEditContacts} />
      )}
      {phone ? (
        <HeaderContactValue icon={ICONS.phone}>
          <span className="min-w-0 truncate">{phone}</span>
        </HeaderContactValue>
      ) : (
        onEditContacts && <AddContactLink label="Add phone" onClick={onEditContacts} />
      )}
      {more > 0 &&
        (onMore ? (
          <button
            type="button"
            onClick={onMore}
            aria-label={`${more} more ${more === 1 ? "contact" : "contacts"}`}
            className="text-u-text3 transition hover:text-u-text hover:underline"
          >
            +{more}
          </button>
        ) : (
          <span className="text-u-text3">+{more}</span>
        ))}
      {onEditContacts && (email || phone) && <SectionEditButton label="contact details" onClick={onEditContacts} />}
    </div>
  );
}

function HeaderContactValue({ icon, children }: { icon: string; children: ReactNode }) {
  return (
    <span className="flex min-w-0 items-center gap-1.5">
      <span className="flex flex-none text-u-text3">
        <Icon d={icon} size={13} />
      </span>
      {children}
    </span>
  );
}

function AddContactLink({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center gap-1 text-u-text3 transition hover:text-u-accent"
    >
      <Icon d={ICONS.plus} size={12} />
      {label}
    </button>
  );
}

function HeaderEmployerLine({ employer, location }: { employer: HeaderEmployer; location: string }) {
  if (!employer.name && !location) {
    return <p className="mt-1 truncate font-mono text-meta text-u-text3">No employer or location recorded</p>;
  }
  return (
    <p className="mt-1 flex min-w-0 items-center gap-1.5 font-mono text-meta text-u-text3">
      {employer.name && (
        <>
          <CompanyLogo name={employer.name} logo={employer.logoUrl ?? null} size={18} />
          {employer.onOpen ? (
            <button
              type="button"
              onClick={employer.onOpen}
              className="min-w-0 truncate font-semibold text-u-text2 transition hover:text-u-text hover:underline"
            >
              {employer.name}
            </button>
          ) : (
            <span className="min-w-0 truncate font-semibold text-u-text2">{employer.name}</span>
          )}
        </>
      )}
      {employer.name && location && <span aria-hidden="true">·</span>}
      {location && <span className="min-w-0 truncate">{location}</span>}
    </p>
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

const PILL_ROW_LINES = 2;
const PILL =
  "inline-flex max-w-full items-center rounded-full border border-u-border-strong bg-u-raised px-2.5 py-1 font-mono text-[12px] font-medium text-u-text2";
const MORE_PILL =
  "inline-flex flex-none items-center rounded-full border border-transparent px-2.5 py-1 font-mono text-note font-semibold text-u-accent";

/** The mockup's language pills, reused for skills, held to two lines until "+N more" unfolds them. */
export function PillRow({ label, values, empty }: { label: string; values: readonly string[]; empty?: string }) {
  const slot = useRef<HTMLDivElement>(null);
  const chip = useRef<HTMLSpanElement>(null);
  const [isExpanded, setIsExpanded] = useState(false);
  const fitting = useFittingCount({ slot, chip, lines: PILL_ROW_LINES, contentKey: values.join("|") });
  const hidden = Math.max(0, values.length - fitting);

  return (
    <div className="mt-3">
      <div className="mb-1.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-u-text3">
        {label}
      </div>
      {values.length === 0 ? (
        <p className="font-mono text-[12.5px] text-u-text3">{empty}</p>
      ) : (
        <div ref={slot} className="relative flex flex-wrap gap-1.5">
          {values.map((value, index) => {
            const isLeftOut = !isExpanded && index >= fitting;
            return (
              <span
                key={`${index}:${value}`}
                {...FITTING_PILL}
                aria-hidden={isLeftOut || undefined}
                className={cn(PILL, isLeftOut && LEFT_OUT_PILL)}
              >
                {value}
              </span>
            );
          })}
          {hidden > 0 && (
            <button
              type="button"
              aria-expanded={isExpanded}
              onClick={() => setIsExpanded((current) => !current)}
              className={cn(MORE_PILL, "transition hover:bg-u-accent-tint")}
            >
              {isExpanded ? "See less" : `+${hidden} more`}
            </button>
          )}
          <span ref={chip} aria-hidden className={cn(MORE_PILL, LEFT_OUT_PILL)}>
            +{values.length} more
          </span>
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
