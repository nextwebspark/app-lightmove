import { Avatar } from "../../../components/ui/Avatar";
import { PanelCloseButton } from "../../../components/ui/PanelCloseButton";
import { CareerTimeline } from "../../candidates/components/CareerTimeline";
import { ClampedText, EducationList } from "../../candidates/components/ProfileParts";
import type { SuggestedPerson } from "../api/sourcingApi";
import { reasonChip } from "../lib/suggestionReasons";

/** A found person's profile, opened inside the panel: what was read of them, and the box that ticks them to add. */
export function ProfilePane({
  person,
  ticked,
  onToggle,
  onClose,
  countryName,
}: {
  person: SuggestedPerson;
  ticked: boolean;
  onToggle: () => void;
  onClose: () => void;
  countryName: (code: string) => string;
}) {
  const profile = person.person;
  const name = profile.fullName ?? profile.linkedinSlug;
  const chips = person.reasons
    .map((reason) => reasonChip(reason, countryName))
    .filter((chip): chip is string => chip !== null);
  return (
    <aside
      aria-label={`${name}'s profile`}
      className="flex w-full flex-none flex-col border-l border-u-border bg-u-surface md:w-[384px]"
    >
      <div className="relative min-h-0 flex-1 overflow-y-auto px-[18px] py-4">
        <PanelCloseButton onClose={onClose} />
        <div className="flex items-start gap-3 pe-8">
          <Avatar id={profile.linkedinSlug} name={name} src={profile.photoUrl} size="lg" />
          <div className="min-w-0">
            <h3 className="text-lead font-semibold">{name}</h3>
            <p className="mt-0.5 text-body text-u-text2">
              {[profile.title ?? "Title not on record", profile.companyName].filter(Boolean).join(" · ")}
            </p>
            {profile.location && <p className="mt-0.5 font-mono text-note text-u-text3">{profile.location}</p>}
          </div>
        </div>
        {chips.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-[5px]">
            {chips.map((chip) => (
              <span key={chip} className="rounded bg-u-sunken px-[7px] py-px text-meta font-medium text-u-text2">
                {chip}
              </span>
            ))}
          </div>
        )}
        {profile.career.length > 0 && (
          <section className="mt-[18px]">
            <h4 className="text-meta font-semibold uppercase tracking-[.06em] text-u-text3">Career</h4>
            <CareerTimeline career={profile.career} />
          </section>
        )}
        {profile.about && (
          <section className="mt-4">
            <h4 className="text-meta font-semibold uppercase tracking-[.06em] text-u-text3">About</h4>
            <ClampedText text={profile.about} className="mt-1.5 text-note text-u-text2" />
          </section>
        )}
        {profile.education.length > 0 && (
          <section className="mt-4">
            <h4 className="text-meta font-semibold uppercase tracking-[.06em] text-u-text3">Education</h4>
            <EducationList education={profile.education} />
          </section>
        )}
        <section className="mt-4">
          <h4 className="text-meta font-semibold uppercase tracking-[.06em] text-u-text3">Contact</h4>
          <p className="mt-1.5 text-note text-u-text2">A work email and a phone can be found once added.</p>
        </section>
      </div>
      {person.candidateId === null && (
        <div className="flex-none border-t border-u-border px-[18px] py-3">
          <button
            type="button"
            onClick={onToggle}
            className="h-[34px] w-full rounded-md border border-u-border-strong text-body font-medium hover:bg-u-raised"
          >
            {ticked ? "Ticked for adding · untick" : "Tick for adding"}
          </button>
        </div>
      )}
    </aside>
  );
}
