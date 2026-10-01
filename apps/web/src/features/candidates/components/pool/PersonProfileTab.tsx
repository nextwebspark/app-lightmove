import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Select, useToast } from "../../../../components/ui";
import { CollapsibleSection } from "../../../../components/ui/CollapsibleSection";
import { DetailGrid, DetailTile } from "../../../../components/ui/DetailList";
import { formatInstantDate } from "../../../../lib/format";
import { messageFor } from "../../../../lib/errorCodes";
import { changeCandidateStatus, CANDIDATES_KEY_PREFIX } from "../../api/candidatesApi";
import * as poolApi from "../../api/poolApi";
import type { CandidateStatus, PersonPosition, PersonRecord } from "../../api/types";
import { CANDIDATE_SOURCE_STYLES, CANDIDATE_STATUSES, candidateGenderLabel, candidateStatusStyle } from "../../lib/candidateVocabulary";
import { usePoolLookups } from "../../lib/usePoolLookups";
import { CareerTimeline } from "../CareerTimeline";
import { CompensationSummary } from "../CompensationSummary";
import { AddToPositionDialog } from "./AddToPositionDialog";

type ProfileSection = "summary" | "experience" | "compensation" | "background" | "contact";

/**
 * The Profile tab: the positions the person is in, then the profile every position shares — read here,
 * edited from a position's own drawer.
 */
export function PersonProfileTab({ person }: { person: PersonRecord }) {
  const lookups = usePoolLookups();
  const [open, setOpen] = useState<Set<ProfileSection>>(new Set(["summary", "experience", "contact"]));
  const [adding, setAdding] = useState(false);
  const toggle = (section: ProfileSection) =>
    setOpen((current) => {
      const next = new Set(current);
      if (next.has(section)) next.delete(section);
      else next.add(section);
      return next;
    });
  const background = [person.nationality, candidateGenderLabel(person.gender),
    person.yearsExperience != null ? `${person.yearsExperience} years` : null].filter(Boolean);
  const current = person.career[0];

  return (
    <div className="flex flex-col gap-1">
      <section aria-label="Positions" className="mb-3">
        <div className="mb-2 flex items-center gap-2">
          <h3 className="type-label text-u-text3">Positions</h3>
          <span className="rounded-full bg-u-raised px-1.5 font-mono text-[10.5px] text-u-text3">
            {person.positions.length}
          </span>
          <button
            type="button"
            onClick={() => setAdding(true)}
            className="ms-auto flex items-center gap-1 font-mono text-[11.5px] font-semibold text-u-accent hover:underline"
          >
            <Icon d={ICONS.plus} size={11} />
            Add to position
          </button>
        </div>
        {person.positions.length === 0 ? (
          <p className="text-[13px] text-u-text3">
            Not in any position right now. Everything below stays on file for the next search.
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {person.positions.map((position) => (
              <PositionCard key={position.candidateId} position={position} />
            ))}
          </ul>
        )}
      </section>

      <CollapsibleSection
        id="pool-summary"
        title="Summary"
        open={open.has("summary")}
        onToggle={() => toggle("summary")}
        summary={person.summary ? truncate(person.summary, 90) : "Not written yet"}
      >
        <p className="whitespace-pre-wrap pb-3 text-[13px]/[1.6] text-u-text2">
          {person.summary ?? "No summary written yet."}
        </p>
      </CollapsibleSection>
      <CollapsibleSection
        id="pool-experience"
        title="Experience"
        open={open.has("experience")}
        onToggle={() => toggle("experience")}
        summary={current ? [current.title, current.company].filter(Boolean).join(" at ") : null}
      >
        <div className="pb-3">
          <CareerTimeline career={person.career} />
        </div>
      </CollapsibleSection>
      <CollapsibleSection
        id="pool-compensation"
        title="Compensation"
        open={open.has("compensation")}
        onToggle={() => toggle("compensation")}
        summary={person.compensation.baseSalary != null ? "On file" : "Nothing on file"}
      >
        <div className="pb-3">
          <CompensationSummary compensation={person.compensation} />
          <p className="mt-2 text-[12px] text-u-text3">
            One package on file for this person, read by every position they are in.
          </p>
        </div>
      </CollapsibleSection>
      <CollapsibleSection
        id="pool-background"
        title="Background"
        open={open.has("background")}
        onToggle={() => toggle("background")}
        summary={background.length > 0 ? background.join(" · ") : "Nothing on file"}
      >
        <div className="pb-3">
          <DetailGrid>
            <DetailTile label="Nationality" value={person.nationality} />
            <DetailTile label="Gender" value={candidateGenderLabel(person.gender)} />
            <DetailTile
              label="Experience"
              value={person.yearsExperience != null ? `${person.yearsExperience} years` : null}
            />
          </DetailGrid>
        </div>
      </CollapsibleSection>
      <CollapsibleSection
        id="pool-contact"
        title="Contact"
        open={open.has("contact")}
        onToggle={() => toggle("contact")}
        summary={
          [person.contacts.emails[0]?.address, person.contacts.phones[0]?.number].filter(Boolean).join(" · ") ||
          "No email or phone"
        }
      >
        <div className="pb-3">
          {person.contacts.emails.length === 0 && person.contacts.phones.length === 0 ? (
            <p className="text-[13px] text-u-text3">No email or phone on file.</p>
          ) : (
            <ul className="flex flex-col gap-1.5">
              {person.contacts.emails.map((email) => (
                <ContactLine key={email.address} icon={ICONS.mail} value={email.address} kind={email.kind} verified={email.verified} />
              ))}
              {person.contacts.phones.map((phone) => (
                <ContactLine key={phone.number} icon={ICONS.phone} value={phone.number} kind={phone.kind} verified={phone.verified} />
              ))}
            </ul>
          )}
          {person.doNotContact && (
            <p className="mt-2 text-[12px] text-u-offlimits">Do not contact is set — lookups are off for this person.</p>
          )}
        </div>
      </CollapsibleSection>

      <p className="mt-3 font-mono text-[11px] text-u-text3">
        Added to the candidates {formatInstantDate(person.addedAt)} by {person.addedByName ?? "someone"} ·{" "}
        {CANDIDATE_SOURCE_STYLES[person.source]?.label ?? person.source}
      </p>

      <AddToPositionDialog
        open={adding}
        onClose={() => setAdding(false)}
        personIds={[person.personId]}
        targetName={person.fullName}
        alreadyInByPosition={new Map(person.positions.map((position) => [position.projectId, 1]))}
        positions={lookups.positions}
      />
    </div>
  );
}

function PositionCard({ position }: { position: PersonPosition }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const status = candidateStatusStyle(position.status);
  const moving = useMutation({
    mutationFn: (next: CandidateStatus) => changeCandidateStatus(position.projectId, position.candidateId, next),
    onSuccess: (_, next) => {
      toast(`Marked ${candidateStatusStyle(next).label} on ${position.positionTitle ?? "the position"}`);
      void queryClient.invalidateQueries({ queryKey: poolApi.POOL_KEY });
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(position.projectId) });
    },
    onError: (error) => toast(messageFor(error)),
  });

  return (
    <li className="flex items-center gap-3 rounded-[8px] border border-u-border px-3 py-2.5">
      <Icon d={ICONS.position} size={15} className="flex-none text-u-text3" />
      <div className="min-w-0 flex-1">
        <Link
          to={`/projects/${position.projectId}/companies`}
          className="block truncate text-[13px] font-semibold text-u-text hover:underline"
        >
          {position.positionTitle ?? "Untitled position"}
        </Link>
        <span className="block truncate font-mono text-[11px] text-u-text3">
          Added by {position.addedByName ?? "someone"} · {formatInstantDate(position.addedAt)} ·{" "}
          {CANDIDATE_SOURCE_STYLES[position.source]?.label ?? position.source}
        </span>
      </div>
      {position.workable ? (
        <Select
          aria-label={`Status on ${position.positionTitle ?? "this position"}`}
          value={position.status}
          disabled={moving.isPending}
          onChange={(event) => moving.mutate(event.target.value as CandidateStatus)}
          className="w-auto py-1 text-[12.5px]"
        >
          {CANDIDATE_STATUSES.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
      ) : (
        <span className={`flex-none rounded-full px-2 py-px font-mono text-[10px] font-semibold ${status.className}`}>
          {status.label}
        </span>
      )}
    </li>
  );
}

function ContactLine({
  icon,
  value,
  kind,
  verified,
}: {
  icon: string;
  value: string;
  kind: "work" | "personal" | null;
  verified: boolean;
}) {
  return (
    <li className="flex items-center gap-2 text-[13px] text-u-text2">
      <Icon d={icon} size={13} className="text-u-text3" />
      <span className="min-w-0 truncate">{value}</span>
      {kind && (
        <span
          className={`rounded-[4px] px-1.5 py-px font-mono text-[9.5px] font-semibold uppercase ${
            kind === "work" ? "bg-u-adjacent-tint text-u-adjacent" : "bg-u-accent-tint text-u-accent"
          }`}
        >
          {kind}
        </span>
      )}
      {verified && (
        <span title="Verified" className="text-u-direct">
          <Icon d={ICONS.check} size={12} />
        </span>
      )}
    </li>
  );
}

function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max - 1).trimEnd()}…` : text;
}
