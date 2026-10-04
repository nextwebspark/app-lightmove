import { Icon, ICONS } from "../../../../components/layout/Icon";
import { CompanyLink } from "../../../../components/ui/CompanyLink";
import { NetworkMark } from "../../../../components/ui/NetworkMark";
import type { PersonResult } from "../../api/types";

/**
 * A person's ways out, as icons: LinkedIn, then whatever else ContactOut named. X keeps its own mark;
 * GitHub has none in `public/brand/`, so it takes the plain link glyph rather than a redrawn logo.
 */
export function PersonLinks({ person, reserve = false }: { person: PersonResult; reserve?: boolean }) {
  const name = person.fullName ?? person.linkedinSlug;
  return (
    <span className="flex flex-none items-center gap-1">
      <CompanyLink
        url={person.profileUrl}
        icon={<NetworkMark network="linkedin" size={14} />}
        label="LinkedIn"
        companyName={name}
        reserve={reserve}
      />
      {person.details?.links.map((link) => (
        <CompanyLink
          key={link.url}
          url={link.url}
          icon={
            link.label === "Twitter" ? (
              <NetworkMark network="x" size={13} />
            ) : (
              <Icon d={ICONS.externalLink} size={13} />
            )
          }
          label={link.label}
          companyName={name}
        />
      ))}
    </span>
  );
}

/** Which contacts ContactOut holds for them, as the Contact section's own glyphs; nothing when it said none. */
export function ContactAvailabilityMarks({ person }: { person: PersonResult }) {
  const flags = person.details?.contactAvailability;
  if (!flags) return null;
  const hasEmail = flags.personalEmail || flags.workEmail;
  if (!hasEmail && !flags.phone) return null;
  return (
    <span className="flex items-center gap-2 text-u-text2">
      {hasEmail && (
        <span title={emailTitle(flags)} className="inline-flex items-center gap-1 font-mono text-[11px]">
          <Icon d={ICONS.mail} size={13} />✓
        </span>
      )}
      {flags.phone && (
        <span title="Phone on file at ContactOut" className="inline-flex items-center gap-1 font-mono text-[11px]">
          <Icon d={ICONS.phone} size={13} />✓
        </span>
      )}
    </span>
  );
}

function emailTitle(flags: { personalEmail: boolean; workEmail: boolean }): string {
  const kinds = [flags.workEmail && "work", flags.personalEmail && "personal"].filter(Boolean).join(" and ");
  return `A ${kinds} email on file at ContactOut`;
}
