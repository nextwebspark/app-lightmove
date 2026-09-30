import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Avatar } from "../../../../components/ui/Avatar";
import { CompanyLinks } from "../../../../components/ui/CompanyLink";
import { CompanyLogo } from "../../../../components/ui/CompanyLogo";
import { DetailPill } from "../../../../components/ui/DetailList";
import { SelectionCheckbox } from "../../../../components/ui/SelectionCheckbox";
import { cn } from "../../../../lib/cn";
import type { PersonResult } from "../../api/types";
import { currentTenure, previousRoles, signalsOf, totalYears } from "../../lib/personHighlights";
import { ContactAvailabilityMarks, PersonLinks } from "./PersonLinks";

/**
 * One person as a card: a face large enough to recognise, the seat held now and for how long, where
 * they came from, and whether they can be reached — what decides a closer look, without the drawer.
 *
 * <p>The name is the card's one button, stretched over the whole card, so a click anywhere opens the
 * profile; the tick box and the link icons sit above it and act on their own.
 */
export function PersonCard({
  person,
  selected,
  onToggle,
  onOpen,
}: {
  person: PersonResult;
  selected: boolean;
  /** Absent for a person already in the mandate, who cannot be ticked. */
  onToggle?: () => void;
  onOpen: (person: PersonResult) => void;
}) {
  const name = person.fullName ?? person.linkedinSlug;
  const headline = person.details?.headline && person.details.headline !== person.title ? person.details.headline : null;
  const tenure = currentTenure(person.career);
  const years = totalYears(person.career);
  const previous = previousRoles(person.career, 2);
  const signals = signalsOf(person);
  const candidateFacts: { icon: string; text: string | null }[] = [
    { icon: ICONS.mapPin, text: person.location },
    { icon: ICONS.briefcase, text: years === null ? null : `${years} yrs experience` },
    { icon: ICONS.trendingUp, text: person.details?.seniority ?? null },
  ];
  const facts = candidateFacts.filter((fact): fact is { icon: string; text: string } => Boolean(fact.text));

  return (
    <article
      className={cn(
        "group relative flex w-full min-w-0 flex-col rounded-[12px] border bg-u-surface p-4 transition",
        selected ? "border-u-accent bg-u-accent-tint" : "border-u-border-strong hover:border-u-accent/50",
        person.held && "opacity-80",
      )}
    >
      <div className="flex min-h-5 items-center justify-between gap-2">
        {onToggle ? (
          <span
            className={cn(
              "relative z-10 transition",
              !selected && "opacity-100 md:opacity-0 md:group-focus-within:opacity-100 md:group-hover:opacity-100",
            )}
          >
            <SelectionCheckbox checked={selected} label={`Select ${name}`} onChange={onToggle} />
          </span>
        ) : (
          <span />
        )}
        <span className="flex items-center gap-2">
          <ContactAvailabilityMarks person={person} />
          {person.held && <DetailPill label="In mandate" className="bg-u-accent-tint text-u-accent" />}
        </span>
      </div>

      <div className="mt-2 flex items-start gap-3.5">
        <Avatar
          id={person.linkedinSlug}
          name={name}
          src={person.photoUrl}
          className="size-[72px] flex-none rounded-[14px] border border-u-border-strong text-xl"
        />
        <div className="min-w-0 flex-1">
          <button
            type="button"
            onClick={() => onOpen(person)}
            className="line-clamp-2 text-left font-sans text-[15px] font-semibold text-u-text outline-none after:absolute after:inset-0 after:rounded-[12px] focus-visible:after:ring-2 focus-visible:after:ring-u-accent"
          >
            {name}
          </button>
          <p className="mt-0.5 line-clamp-2 font-mono text-[12.5px] text-u-text2">{person.title ?? "—"}</p>
          {headline && <p className="mt-0.5 truncate text-[12px] text-u-text3">{headline}</p>}
        </div>
      </div>

      {person.companyName && (
        <div className="mt-3 rounded-[8px] border border-u-border bg-u-raised px-2.5 py-2">
          <div className="flex min-w-0 items-center gap-2">
            <CompanyLogo name={person.companyName} logo={person.companyLogoUrl} size={20} />
            <span className="min-w-0 flex-1 truncate font-sans text-[13px] font-medium text-u-text">
              {person.companyName}
            </span>
            <span className="relative z-10">
              <CompanyLinks
                companyName={person.companyName}
                website={person.details?.company?.website ?? null}
                linkedinUrl={person.companyLinkedinUrl}
              />
            </span>
          </div>
          {tenure && (
            <p className="mt-1 font-mono text-[11px] text-u-text3">
              In role since {tenure.since}
              {tenure.length && ` · ${tenure.length}`}
            </p>
          )}
        </div>
      )}

      {facts.length > 0 && (
        <ul className="mt-3 flex flex-col gap-1">
          {facts.map((fact) => (
            <li key={fact.text} className="flex min-w-0 items-center gap-1.5 text-[12.5px] text-u-text2">
              <Icon d={fact.icon} size={13} className="flex-none text-u-text3" />
              <span className="truncate">{fact.text}</span>
            </li>
          ))}
        </ul>
      )}

      {previous.length > 0 && (
        <div className="mt-3">
          <div className="mb-1 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-u-text3">
            Previously
          </div>
          <ul className="flex flex-col gap-0.5">
            {previous.map((post, index) => (
              <li key={`${post.company}-${post.title}-${index}`} className="truncate text-[12.5px] text-u-text2">
                {[post.title, post.company].filter(Boolean).join(" · ")}
              </li>
            ))}
          </ul>
        </div>
      )}

      {signals.chips.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {signals.chips.map((chip) => (
            <span
              key={chip}
              className="max-w-full truncate rounded-full border border-u-border-strong bg-u-raised px-2 py-0.5 font-mono text-[11px] text-u-text2"
            >
              {chip}
            </span>
          ))}
          {signals.overflow > 0 && (
            <span className="px-1 py-0.5 font-mono text-[11px] text-u-text3">+{signals.overflow}</span>
          )}
        </div>
      )}

      <div className="mt-auto flex items-center justify-between gap-2 pt-3">
        <span className="relative z-10">
          <PersonLinks person={person} />
        </span>
        <span aria-hidden className="inline-flex items-center gap-0.5 text-[12px] font-medium text-u-accent">
          View profile
          <Icon d={ICONS.chevronRight} size={13} />
        </span>
      </div>
    </article>
  );
}

/** A card's outline while a page loads, the same height as the real thing so nothing jumps. */
export function PersonCardSkeleton() {
  return (
    <div className="flex h-[300px] flex-col gap-3 rounded-[12px] border border-u-border bg-u-surface p-4">
      <div className="mt-6 flex gap-3.5">
        <div className="size-[72px] flex-none animate-pulse rounded-[14px] bg-u-raised" />
        <div className="flex flex-1 flex-col gap-2 pt-1">
          <div className="h-3.5 w-2/3 animate-pulse rounded bg-u-raised" />
          <div className="h-3 w-1/2 animate-pulse rounded bg-u-raised" />
        </div>
      </div>
      <div className="h-11 animate-pulse rounded-[8px] bg-u-raised" />
      <div className="h-3 w-3/4 animate-pulse rounded bg-u-raised" />
      <div className="h-3 w-1/2 animate-pulse rounded bg-u-raised" />
    </div>
  );
}
