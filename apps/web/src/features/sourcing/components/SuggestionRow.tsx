import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar } from "../../../components/ui/Avatar";
import { SelectionCheckbox } from "../../../components/ui/SelectionCheckbox";
import { cn } from "../../../lib/cn";
import type { SuggestedPerson } from "../api/sourcingApi";
import { reasonChip, whyNot } from "../lib/suggestionReasons";

const BAND = {
  STRONG: { label: "Strong", className: "bg-u-direct-tint text-u-direct" },
  GOOD: { label: "Good", className: "bg-u-accent-tint text-u-accent" },
  POSSIBLE: { label: "Possible", className: "bg-u-sunken text-u-text2" },
} as const;

const MAX_CHIPS = 3;

/**
 * One person a search found: ticked to add, their current title and where they live, and why they rank where they
 * do — or, for someone who doesn't fit, why not. Someone the position already holds can't be ticked.
 */
export function SuggestionRow({
  person,
  ticked,
  onToggle,
  onOpen,
  highlighted,
  countryName,
}: {
  person: SuggestedPerson;
  ticked: boolean;
  onToggle: () => void;
  onOpen: () => void;
  highlighted: boolean;
  countryName: (code: string) => string;
}) {
  const profile = person.person;
  const name = profile.fullName ?? profile.linkedinSlug;
  const onPosition = person.candidateId !== null;
  const chips = person.reasons
    .map((reason) => reasonChip(reason, countryName))
    .filter((chip): chip is string => chip !== null)
    .slice(0, MAX_CHIPS);
  const band = person.band ? BAND[person.band] : null;
  return (
    <div
      className={cn(
        "flex items-start gap-2.5 border-t border-u-border px-3 py-[11px]",
        highlighted && "bg-u-accent-tint",
        onPosition && "opacity-75",
      )}
    >
      <span className="mt-1 flex-none">
        <SelectionCheckbox
          checked={ticked}
          disabled={onPosition}
          label={onPosition ? `${name} is on this position` : `Add ${name}`}
          onChange={onToggle}
        />
      </span>
      <Avatar id={profile.linkedinSlug} name={name} src={profile.photoUrl} size="sm" className="mt-0.5" />
      <button type="button" onClick={onOpen} className="min-w-0 flex-1 text-left">
        <span className="flex flex-wrap items-center gap-1.5">
          <span className="text-body font-semibold text-u-text">{name}</span>
          {onPosition && (
            <span className="rounded-full bg-u-direct-tint px-2 py-px text-meta font-semibold text-u-direct">
              On this position
            </span>
          )}
        </span>
        <span className={cn("mt-0.5 block text-body", profile.title ? "text-u-text" : "text-u-text3")}>
          {profile.title ?? "Title not on record"}
        </span>
        <span className="mt-0.5 block font-mono text-note text-u-text3">
          {[profile.companyName, profile.location].filter(Boolean).join(" · ")}
        </span>
        {person.verdict === "FITS" && chips.length > 0 && (
          <span className="mt-[7px] flex flex-wrap gap-[5px]">
            {chips.map((chip, index) => (
              <span
                key={chip}
                className={cn(
                  "rounded px-[7px] py-px text-meta font-medium",
                  index === 0 ? "bg-u-accent-tint text-u-text" : "bg-u-sunken text-u-text2",
                )}
              >
                {chip}
              </span>
            ))}
          </span>
        )}
      </button>
      <span className="flex flex-none flex-col items-end gap-[7px]">
        {band && (
          <span className={cn("rounded-full px-2.5 py-0.5 text-meta font-semibold", band.className)}>
            {band.label}
          </span>
        )}
        {person.verdict === "NOT_FIT" && (
          <span className="max-w-[220px] text-right text-note font-medium text-u-signal">{whyNot(person)}</span>
        )}
        {profile.profileUrl && (
          <a
            href={profile.profileUrl}
            target="_blank"
            rel="noreferrer"
            aria-label={`${name} on LinkedIn`}
            className="text-u-text3 hover:text-u-text"
          >
            <Icon d={ICONS.externalLink} size={13} />
          </a>
        )}
      </span>
    </div>
  );
}
