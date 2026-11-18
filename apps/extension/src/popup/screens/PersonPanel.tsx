import { useEffect, useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { FIELD_LIMITS, cappedAt } from "../../api/fieldLimits";
import type { CapturedCandidate, HeldCandidate, NoteKind, SaveCandidateRequest, WorkspaceUser } from "../../api/types";
import { findStatus, startingStatusKey } from "../../domain/pipelineStatus";
import { Button } from "../components/Button";
import { FieldSection } from "../components/FieldSection";
import { Icon } from "../components/Icon";
import { RecordBanner } from "../components/RecordBanner";
import { StatusPicker } from "../components/StatusPicker";
import { SubjectCard } from "../components/SubjectCard";
import { TagPicker } from "../components/TagPicker";
import { useCandidatePhoto } from "../hooks/useCandidatePhoto";
import { usePersonTags, useTagCatalog } from "../hooks/useCandidateTags";
import { useCapturePerson, useUndoCapture, type FiledCapture } from "../hooks/useCaptureSequence";
import { useCaptureSettings } from "../hooks/useCaptureSettings";
import { useCloseAfterSave } from "../hooks/useCloseAfterSave";
import { useHeldCache, useHeldPerson } from "../hooks/useHeldRecord";
import { usePipelineCatalog } from "../hooks/usePipelineCatalog";
import { useSeededField } from "../hooks/useSeededField";
import { askOrThrow } from "../lib/askOrThrow";
import { ICONS } from "../lib/icons";
import { shortWhen } from "../lib/shortWhen";
import { NoteComposer } from "../notes/components/NoteComposer";
import type { NoteScope } from "../notes/components/NoteParts";
import { NotesSection } from "../notes/components/NotesSection";
import { teammatesOf } from "../notes/lib/teammates";
import { CheckingPosition, HeldCheckFailed, RefusalNote } from "./panelStates";
import type { SubjectPanelProps } from "./subjectPanelProps";
import { DEFAULT_NOTE_KIND } from "../notes/lib/noteKinds";

const RESEARCH_WAIT_MS = 60_000;

/**
 * A LinkedIn profile against the chosen position. The position is asked first whether it already maps them: if it
 * does, their record opens — status, tags, and the notes, ready for the next one; if not, the form files them, and
 * the record opens on what was filed.
 */
export function PersonPanel({ page, projects, user }: SubjectPanelProps) {
  const project = projects.selectedProject;
  const projectId = project?.id ?? null;
  const linkedinUrl = page.person?.linkedinUrl ?? null;
  const [filed, setFiled] = useState<FiledCapture<CapturedCandidate> | null>(null);
  const [filedAt, setFiledAt] = useState<number | null>(null);
  const held = useHeldPerson(projectId, linkedinUrl, filedAt);
  const cache = useHeldCache();
  const { settings } = useCaptureSettings();

  // A filing is this position's: another position asks afresh and shows its own record or form.
  useEffect(() => {
    setFiled(null);
    setFiledAt(null);
  }, [projectId]);

  // A note or tag that did not land is waiting on the record for a retry; closing would throw it away.
  useCloseAfterSave(Boolean(filed) && isFiledWhole(filed), settings.closesAfterSave);

  // Research is waited on for a minute — the poll's own window — and then the record stops saying it is coming.
  useEffect(() => {
    if (filedAt === null) return;
    const timer = setTimeout(() => setFiledAt(null), RESEARCH_WAIT_MS);
    return () => clearTimeout(timer);
  }, [filedAt]);

  if (!project) {
    return null;
  }
  if (held.isPending && held.fetchStatus !== "idle") {
    return <CheckingPosition />;
  }
  if (held.data) {
    return (
      <PersonRecord
        held={held.data}
        user={user}
        projectId={project.id}
        heldAtUrl={linkedinUrl}
        positionTitle={project.positionTitle}
        team={project.team}
        filed={filed?.filed.id === held.data.candidate.id ? filed : null}
        isAwaitingResearch={filedAt !== null && !held.data.candidate.enrichedAt}
        onUndone={() => {
          if (linkedinUrl) cache.setPerson(project.id, linkedinUrl, null);
          cache.forgetCompanies(project.id);
          setFiled(null);
          setFiledAt(null);
        }}
      />
    );
  }
  return (
    <PersonForm
      page={page}
      projects={projects}
      user={user}
      onRecheck={held.isError ? () => void held.refetch() : null}
      onFiled={(capture) => {
        setFiled(capture);
        setFiledAt(Date.now());
        if (linkedinUrl) {
          cache.setPerson(project.id, linkedinUrl, {
            candidate: capture.filed,
            addedBy: { userId: user.id, name: user.fullName, avatarUrl: null },
            companyLogoUrl: null,
          });
          void cache.refreshPerson(project.id, linkedinUrl);
        }
      }}
      onHeldElsewhere={() => {
        if (linkedinUrl) void cache.refreshPerson(project.id, linkedinUrl);
      }}
    />
  );
}

function PersonForm({
  page,
  projects,
  user,
  onFiled,
  onHeldElsewhere,
  onRecheck,
}: SubjectPanelProps & {
  onFiled: (capture: FiledCapture<CapturedCandidate>) => void;
  onHeldElsewhere: () => void;
  /** Set when the position could not be asked whether it already has them. */
  onRecheck: (() => void) | null;
}) {
  const project = projects.selectedProject!;
  const capture = useCapturePerson();
  const pipeline = usePipelineCatalog();
  const tagCatalog = useTagCatalog();
  const { value: fullName, edit: editFullName, hasBeenEdited } = useSeededField(page.person?.fullName ?? null, page.pageKey);
  const isNameLocked = Boolean(page.person?.fullName) && !hasBeenEdited;

  // A status, tags and a note are judgements about the person on screen, so they go when the person does.
  const [statusKey, setStatusKey] = useState<string | null>(null);
  const [tagIds, setTagIds] = useState<string[]>([]);
  const [kind, setKind] = useState<NoteKind>(DEFAULT_NOTE_KIND);
  const [scope, setScope] = useState<NoteScope>("position");
  const [draft, setDraft] = useState("");
  useEffect(() => {
    setStatusKey(null);
    setTagIds([]);
    setKind(DEFAULT_NOTE_KIND);
    setScope("position");
    setDraft("");
    capture.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page.pageKey]);

  const startingKey = pipeline.data ? startingStatusKey(pipeline.data) : null;
  const teammates = useMemo(() => teammatesOf(project.team, user.id), [project.team, user.id]);
  const noteBody = draft.trim();
  const canSave = Boolean(fullName.trim()) && noteBody.length <= FIELD_LIMITS.noteBody && !capture.isPending;

  const save = () => {
    if (!canSave) return;
    capture.mutate(
      {
        projectId: project.id,
        candidate: toCandidate(fullName, statusKey, page.person?.linkedinUrl ?? null, page.sourceUrl),
        note: noteBody
          ? { kind, body: noteBody, format: "markdown", ...(scope === "everywhere" ? { allPositions: true } : {}) }
          : null,
        tagIds,
      },
      {
        onSuccess: onFiled,
        onError: (refusal) => {
          if (isHeldElsewhere(refusal.code)) onHeldElsewhere();
        },
      },
    );
  };

  return (
    <>
      <div className="min-h-0 flex-1 overflow-y-auto p-3.5">
        {onRecheck && <HeldCheckFailed subject="person" onRetry={onRecheck} />}
        <SubjectCard
          shape="person"
          name={fullName}
          isNameLocked={isNameLocked}
          onNameChange={editFullName}
          linkedinUrl={page.person?.linkedinUrl ?? null}
          imageSrc={null}
          isReading={page.isReading}
          onRescan={() => void page.rescan()}
        />

        <FieldSection label="Status" hint="Your workspace's pipeline">
          <StatusPicker
            catalog={pipeline.data}
            value={statusKey ?? startingKey}
            startingKey={startingKey}
            onChange={setStatusKey}
            inProcessOnly
          />
        </FieldSection>

        <FieldSection label="Tags" hint="On the person, every position">
          <TagPicker
            catalog={tagCatalog.tags}
            selectedIds={tagIds}
            onToggle={(tagId, on) => setTagIds((held) => (on ? [...held, tagId] : held.filter((id) => id !== tagId)))}
            onCreate={(label) => tagCatalog.create.mutate(label, { onSuccess: (tag) => setTagIds((held) => [...held, tag.id]) })}
            isCreating={tagCatalog.create.isPending}
          />
          <TagCreateError error={tagCatalog.create.error} />
        </FieldSection>

        <FieldSection label="Note" hint="Optional · your team only">
          <NoteComposer
            kind={kind}
            onKindChange={setKind}
            draft={draft}
            onDraftChange={setDraft}
            scope={scope}
            onScopeChange={setScope}
            teammates={teammates}
            positionTitle={project.positionTitle}
            expandTitle={`Note on ${fullName.trim() || "this person"}`}
            onSubmit={save}
          />
        </FieldSection>
      </div>

      <footer className="flex flex-none flex-col gap-2 border-t border-u-border px-3.5 py-3">
        {capture.error && (
          <RefusalNote refusal={capture.error} projectId={isHeldElsewhere(capture.error.code) ? project.id : undefined} />
        )}
        <Button type="button" size="md" className="w-full" disabled={!canSave} loading={capture.isPending} onClick={save}>
          <Icon d={ICONS.plus} />
          {capture.isPending ? "Adding…" : "Add to position"}
          <kbd className="ms-1.5 rounded-[4px] bg-white/15 px-1.5 font-mono text-[10.5px] font-medium">⌘↵</kbd>
        </Button>
      </footer>
    </>
  );
}

function PersonRecord({
  held,
  user,
  projectId,
  heldAtUrl,
  positionTitle,
  team,
  filed,
  isAwaitingResearch,
  onUndone,
}: {
  held: HeldCandidate;
  user: WorkspaceUser;
  projectId: string;
  /** The address the record was looked up at, which the cache is keyed on — not always how the row spells it. */
  heldAtUrl: string | null;
  positionTitle: string;
  team: SubjectPanelProps["projects"]["projects"][number]["team"];
  filed: FiledCapture<CapturedCandidate> | null;
  isAwaitingResearch: boolean;
  onUndone: () => void;
}) {
  const candidate = held.candidate;
  const pipeline = usePipelineCatalog();
  const tagCatalog = useTagCatalog();
  const personTags = usePersonTags(candidate.personId);
  const photo = useCandidatePhoto(projectId, candidate.id, candidate.enrichedAt);
  const undo = useUndoCapture();
  const cache = useHeldCache();
  const teammates = useMemo(() => teammatesOf(team, user.id), [team, user.id]);
  const changeStatus = useMutation({
    mutationFn: (status: string) => askOrThrow({ kind: "changeCandidateStatus", projectId, candidateId: candidate.id, status }),
    onSuccess: (updated) => {
      // Keyed on the row's own spelling, a person filed elsewhere as linkedin.com/in/x kept showing the old status.
      const keyUrl = heldAtUrl ?? candidate.linkedinUrl;
      if (keyUrl) cache.setPerson(projectId, keyUrl, { ...held, candidate: updated });
    },
  });

  const statusKey = candidate.status;
  const current = pipeline.data ? findStatus(pipeline.data, statusKey) : null;
  const stageLabel = current?.stage.label ?? candidate.pipelineStage?.label ?? null;
  const statusLabel = current?.status.label ?? candidate.statusLabel ?? statusKey;
  const headline = [candidate.title, candidate.companyName].filter(Boolean).join(" · ");
  const isAdded = filed !== null;
  const unsent = filed?.unsentNote
    ? {
        body: filed.unsentNote.note.body,
        kind: filed.unsentNote.note.kind,
        scope: (filed.unsentNote.note.allPositions ? "everywhere" : "position") as NoteScope,
        reason: filed.unsentNote.reason,
      }
    : null;

  return (
    <>
      <RecordBanner
        variant={isAdded ? "added" : "held"}
        title={isAdded ? "Added to the position" : "Already on this position"}
        detail={
          isAdded
            ? `${positionTitle} · ${statusLabel}`
            : [
                `Added${candidate.addedAt ? ` ${shortWhen(candidate.addedAt)}` : ""}${held.addedBy ? ` by ${held.addedBy.name}` : ""}`,
                stageLabel,
              ]
                .filter(Boolean)
                .join(" · ")
        }
        projectId={projectId}
        isUndoing={undo.isPending}
        onUndo={() =>
          undo.mutate(
            { projectId, candidateId: candidate.id, noteId: filed?.filedNote?.id ?? null },
            { onSuccess: onUndone },
          )
        }
      />
      <div className="min-h-0 flex-1 overflow-y-auto p-3.5 pb-16">
        {undo.error && (
          <div className="mb-3">
            <RefusalNote refusal={undo.error} projectId={projectId} />
          </div>
        )}
        <SubjectCard
          shape="person"
          name={candidate.fullName}
          isNameLocked
          headline={headline || (isAwaitingResearch ? "Researching the profile — photo and career follow" : null)}
          employerLogo={candidate.companyName ? { name: candidate.companyName, src: held.companyLogoUrl } : null}
          linkedinUrl={candidate.linkedinUrl}
          imageSrc={photo.data}
          isImagePending={isAwaitingResearch}
        />

        <FieldSection label="Status" hint="Changes it on this position">
          <StatusPicker
            catalog={pipeline.data}
            value={statusKey}
            startingKey={pipeline.data ? startingStatusKey(pipeline.data) : null}
            onChange={(key) => key !== statusKey && changeStatus.mutate(key)}
            disabled={changeStatus.isPending}
            inProcessOnly
          />
          {changeStatus.error && <p role="alert" className="mt-1.5 text-note text-u-offlimits">{changeStatus.error.message}</p>}
        </FieldSection>

        <FieldSection label="Tags" hint="On the person, every position">
          <TagPicker
            catalog={tagCatalog.tags}
            selectedIds={personTags.tagIds}
            onToggle={(tagId, on) => personTags.toggle.mutate({ tagId, on })}
            onCreate={(label) =>
              tagCatalog.create.mutate(label, { onSuccess: (tag) => personTags.toggle.mutate({ tagId: tag.id, on: true }) })
            }
            isCreating={tagCatalog.create.isPending}
            disabled={personTags.toggle.isPending}
          />
          <TagCreateError error={tagCatalog.create.error} />
          {filed && filed.untaggedTagIds.length > 0 && (
            <p role="alert" className="mt-1.5 text-note text-u-offlimits">
              Some tags were not added. Add them again above.
            </p>
          )}
        </FieldSection>

        <NotesSection
          target={{ subject: "person", projectId, candidateId: candidate.id }}
          subjectName={candidate.fullName}
          teammates={teammates}
          positionTitle={positionTitle}
          unsent={unsent}
        />
      </div>
    </>
  );
}

/** Both say the position has them already — the second from a save that raced another for the same profile. */
function isHeldElsewhere(code: string): boolean {
  return code === "CANDIDATE_ALREADY_MAPPED" || code === "PERSON_PROFILE_HELD";
}

function isFiledWhole(capture: FiledCapture<CapturedCandidate> | null): boolean {
  return capture !== null && !capture.unsentNote && capture.untaggedTagIds.length === 0;
}

/** A fixed sentence, never the server's own words: the panel reads a refusal's code, not its detail. */
function TagCreateError({ error }: { error: Error | null }) {
  return error ? (
    <p role="alert" className="mt-1.5 text-note text-u-offlimits">
      That tag was not created. Try again, or add it in Uncava.
    </p>
  ) : null;
}

/** The name, the status if one was chosen, and the URLs the page cannot lie about — the note and tags follow it. */
function toCandidate(
  fullName: string,
  statusKey: string | null,
  linkedinUrl: string | null,
  sourceUrl: string | null,
): SaveCandidateRequest {
  return {
    fullName: cappedAt(fullName, FIELD_LIMITS.fullName) ?? "",
    linkedinUrl: cappedAt(linkedinUrl, FIELD_LIMITS.linkedinUrl),
    source: "extension",
    // Left out unless chosen, so the server files them at the workspace's own starting status.
    status: statusKey ?? undefined,
    sourceUrl: cappedAt(sourceUrl, FIELD_LIMITS.sourceUrl),
  };
}
