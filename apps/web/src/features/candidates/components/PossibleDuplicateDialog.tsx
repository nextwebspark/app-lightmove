import { useQueries } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Modal } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { formatInstantDate } from "../../../lib/format";
import * as poolApi from "../api/poolApi";
import type { PersonRecord } from "../api/types";
import { CANDIDATE_SOURCE_STYLES, candidateStatusStyle } from "../lib/candidateVocabulary";
import { usePoolLookups } from "../lib/usePoolLookups";
import { PersonAvatar } from "./pool/PersonAvatar";
import { TagPill } from "./pool/TagPill";

/**
 * Asked when a hand-typed add names someone the workspace already holds at that employer by name alone.
 * A matching LinkedIn URL or email is never a question — the server simply maps that person — so this
 * only ever offers the people the 409 named, read from the pool.
 */
export function PossibleDuplicateDialog({
  fullName,
  employerName,
  personIds,
  isSaving,
  onUseExisting,
  onAddAsNew,
  onClose,
}: {
  fullName: string;
  employerName: string;
  personIds: string[];
  isSaving: boolean;
  onUseExisting: (personId: string) => void;
  onAddAsNew: () => void;
  onClose: () => void;
}) {
  const [chosenId, setChosenId] = useState(personIds[0]);
  const people = useQueries({
    queries: personIds.map((personId) => ({
      queryKey: poolApi.PERSON_RECORD_KEY(personId),
      queryFn: ({ signal }: { signal: AbortSignal }) => poolApi.getPerson(personId, signal),
    })),
  });
  const chosen = people.find((person) => person.data?.personId === chosenId)?.data;

  return (
    <Modal
      open
      onClose={onClose}
      title="Is this someone already in your candidates?"
      className="md:w-[520px]"
      footer={
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={onAddAsNew} disabled={isSaving}>
            No — add a different person
          </Button>
          <Button onClick={() => onUseExisting(chosenId)} loading={isSaving}>
            Yes — add {chosen?.fullName ?? fullName} here
          </Button>
        </div>
      }
    >
      <p className="mb-3.5 text-[13px] text-u-text2">
        You&apos;re adding <b className="text-u-text">{fullName}</b> at <b className="text-u-text">{employerName}</b>.
        Your team already has {personIds.length === 1 ? "someone" : `${personIds.length} people`} with that name at
        that employer.
      </p>
      <div role={personIds.length > 1 ? "radiogroup" : undefined} aria-label="Existing person" className="flex flex-col gap-2">
        {people.map((query, index) =>
          query.data ? (
            <ExistingPersonCard
              key={query.data.personId}
              person={query.data}
              isChoosable={personIds.length > 1}
              isChosen={query.data.personId === chosenId}
              onChoose={() => setChosenId(query.data.personId)}
            />
          ) : (
            <div
              key={personIds[index]}
              className="h-[92px] animate-pulse rounded-[10px] border border-u-border bg-u-raised"
              aria-busy="true"
            />
          ),
        )}
      </div>
      <p className="mt-3 font-mono text-[11.5px] text-u-text3">
        A matching LinkedIn URL, email or phone is never asked about — that person is simply added here.
      </p>
    </Modal>
  );
}

function ExistingPersonCard({
  person,
  isChoosable,
  isChosen,
  onChoose,
}: {
  person: PersonRecord;
  isChoosable: boolean;
  isChosen: boolean;
  onChoose: () => void;
}) {
  const { tagsById, membersByUserId } = usePoolLookups();
  const owner = person.ownerUserId ? membersByUserId.get(person.ownerUserId)?.fullName : null;
  const place = [person.title, person.companyName, person.locationCity].filter(Boolean).join(" · ");
  const provenance = [
    owner ? `Owner ${owner}` : null,
    `added ${formatInstantDate(person.addedAt)}${person.addedByName ? ` by ${person.addedByName}` : ""}`,
    CANDIDATE_SOURCE_STYLES[person.source]?.label,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <div
      role={isChoosable ? "radio" : undefined}
      aria-checked={isChoosable ? isChosen : undefined}
      tabIndex={isChoosable ? 0 : undefined}
      onClick={isChoosable ? onChoose : undefined}
      onKeyDown={(event) => {
        if (isChoosable && (event.key === " " || event.key === "Enter")) {
          event.preventDefault();
          onChoose();
        }
      }}
      className={cn(
        "flex gap-3 rounded-[10px] border bg-u-raised px-3.5 py-3",
        isChoosable && "cursor-pointer",
        isChoosable && isChosen ? "border-u-accent" : "border-u-border",
      )}
    >
      <PersonAvatar person={person} />
      <div className="min-w-0 flex-1">
        <div className="text-[14px] font-semibold text-u-text">{person.fullName}</div>
        {place && <div className="font-mono text-[12px] text-u-text2">{place}</div>}
        {(person.positions.length > 0 || person.tagIds.length > 0) && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {person.positions.map((position) => (
              <span
                key={position.candidateId}
                className="inline-flex items-center gap-1.5 rounded-[5px] border border-u-border bg-u-surface px-[7px] py-0.5 text-[11.5px] font-medium text-u-text2"
              >
                {position.positionTitle ?? "A position"} · {candidateStatusStyle(position.status).label}
              </span>
            ))}
            {person.tagIds.map((tagId) => {
              const tag = tagsById.get(tagId);
              return tag ? <TagPill key={tagId} tag={tag} /> : null;
            })}
          </div>
        )}
        <div className="mt-2 font-mono text-[11px] text-u-text3">{provenance}</div>
      </div>
    </div>
  );
}
