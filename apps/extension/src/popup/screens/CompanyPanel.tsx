import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { FIELD_LIMITS, cappedAt } from "../../api/fieldLimits";
import type { NoteKind, TriagedCompany } from "../../api/types";
import { CAPTURED_COMPANY_STAGE, TRIAGE_STAGE_LABELS } from "../../domain/triageDestination";
import { colourPillClass } from "../../domain/pipelineStatus";
import { Button } from "../components/Button";
import { FieldSection } from "../components/FieldSection";
import { Icon } from "../components/Icon";
import { RecordBanner } from "../components/RecordBanner";
import { SubjectCard } from "../components/SubjectCard";
import { useCaptureCompany, useUndoCapture, type FiledCapture } from "../hooks/useCaptureSequence";
import { useCaptureSettings } from "../hooks/useCaptureSettings";
import { useCloseAfterSave } from "../hooks/useCloseAfterSave";
import { heldCompanyKey, useHeldCompany } from "../hooks/useHeldRecord";
import { useSeededField } from "../hooks/useSeededField";
import { cn } from "../lib/cn";
import { ICONS } from "../lib/icons";
import { shortWhen } from "../lib/shortWhen";
import { NoteComposer } from "../notes/components/NoteComposer";
import { NotesSection } from "../notes/components/NotesSection";
import { CheckingPosition, RefusalNote } from "./panelStates";
import type { SubjectPanelProps } from "./subjectPanelProps";
import { DEFAULT_NOTE_KIND } from "../notes/lib/noteKinds";

/**
 * A LinkedIn company page against the chosen position. A company the position holds — by its page or its name — opens
 * on its record and notes; otherwise the form files it into the universe, and only there.
 */
export function CompanyPanel({ page, projects, user }: SubjectPanelProps) {
  const project = projects.selectedProject;
  const projectId = project?.id ?? null;
  const linkedinUrl = page.company?.linkedinUrl ?? null;
  const readName = page.company?.companyName ?? null;
  const [filed, setFiled] = useState<FiledCapture<TriagedCompany> | null>(null);
  const held = useHeldCompany(projectId, linkedinUrl, readName);
  const queryClient = useQueryClient();
  const { settings } = useCaptureSettings();

  useEffect(() => setFiled(null), [projectId]);
  useCloseAfterSave(Boolean(filed), settings.closesAfterSave);

  if (!project) {
    return null;
  }
  if (held.isPending && held.fetchStatus !== "idle") {
    return <CheckingPosition />;
  }
  const record = held.data ?? filed?.filed ?? null;
  if (record) {
    return (
      <CompanyRecord
        company={record}
        projectId={project.id}
        positionTitle={project.positionTitle}
        filed={filed?.filed.id === record.id ? filed : null}
        onUndone={() => {
          queryClient.setQueryData(heldCompanyKey(project.id, linkedinUrl, readName), null);
          setFiled(null);
        }}
      />
    );
  }
  return (
    <CompanyForm
      page={page}
      projects={projects}
      user={user}
      onFiled={(capture) => {
        setFiled(capture);
        queryClient.setQueryData(heldCompanyKey(project.id, linkedinUrl, readName), capture.filed);
      }}
      onHeldElsewhere={() => void held.refetch()}
    />
  );
}

function CompanyForm({
  page,
  projects,
  onFiled,
  onHeldElsewhere,
}: SubjectPanelProps & {
  onFiled: (capture: FiledCapture<TriagedCompany>) => void;
  onHeldElsewhere: () => void;
}) {
  const project = projects.selectedProject!;
  const capture = useCaptureCompany();
  const { value: companyName, edit: editCompanyName, hasBeenEdited } = useSeededField(
    page.company?.companyName ?? null,
    page.pageKey,
  );
  const isNameLocked = Boolean(page.company?.companyName) && !hasBeenEdited;
  const [kind, setKind] = useState<NoteKind>(DEFAULT_NOTE_KIND);
  const [draft, setDraft] = useState("");
  useEffect(() => {
    setKind(DEFAULT_NOTE_KIND);
    setDraft("");
    capture.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page.pageKey]);

  const noteBody = draft.trim();
  const canSave = Boolean(companyName.trim()) && noteBody.length <= FIELD_LIMITS.noteBody && !capture.isPending;

  const save = () => {
    if (!canSave) return;
    capture.mutate(
      {
        projectId: project.id,
        capture: {
          source: "extension",
          status: CAPTURED_COMPANY_STAGE,
          companyName: cappedAt(companyName, FIELD_LIMITS.companyName) ?? "",
          companyLinkedinUrl: cappedAt(page.company?.linkedinUrl, FIELD_LIMITS.companyLinkedinUrl),
          sourceUrl: cappedAt(page.sourceUrl, FIELD_LIMITS.sourceUrl),
        },
        note: noteBody ? { kind, body: noteBody, format: "markdown" } : null,
      },
      {
        onSuccess: onFiled,
        onError: (refusal) => {
          if (refusal.code === "TRIAGE_COMPANY_ALREADY_HELD") onHeldElsewhere();
        },
      },
    );
  };

  return (
    <>
      <div className="min-h-0 flex-1 overflow-y-auto p-3.5">
        <SubjectCard
          shape="company"
          name={companyName}
          isNameLocked={isNameLocked}
          onNameChange={editCompanyName}
          linkedinUrl={page.company?.linkedinUrl ?? null}
          imageSrc={null}
          isReading={page.isReading}
          onRescan={() => void page.rescan()}
        />

        <FieldSection label="Note" hint="Optional · your team only">
          <NoteComposer
            kind={kind}
            onKindChange={setKind}
            draft={draft}
            onDraftChange={setDraft}
            teammates={null}
            positionTitle={project.positionTitle}
            expandTitle={`Note on ${companyName.trim() || "this company"}`}
            onSubmit={save}
          />
        </FieldSection>

        <p className="mt-3 flex gap-2 text-[11.5px] leading-[1.5] text-u-text3">
          <Icon d={ICONS.info} size={13} className="mt-0.5 flex-none" />
          The company goes into this position's universe. Shortlist or decline it later from the Companies screen.
        </p>
      </div>

      <footer className="flex flex-none flex-col gap-2 border-t border-u-border px-3.5 py-3">
        {capture.error && <RefusalNote refusal={capture.error} projectId={project.id} />}
        <Button type="button" size="md" className="w-full" disabled={!canSave} loading={capture.isPending} onClick={save}>
          <Icon d={ICONS.plus} />
          {capture.isPending ? "Adding…" : "Add to universe"}
          <kbd className="ms-1.5 rounded-[4px] bg-white/15 px-1.5 font-mono text-[10.5px] font-medium">⌘↵</kbd>
        </Button>
      </footer>
    </>
  );
}

function CompanyRecord({
  company,
  projectId,
  positionTitle,
  filed,
  onUndone,
}: {
  company: TriagedCompany;
  projectId: string;
  positionTitle: string;
  filed: FiledCapture<TriagedCompany> | null;
  onUndone: () => void;
}) {
  const undo = useUndoCapture();
  const place = [company.companyCity, company.companyCountry].filter(Boolean).join(", ");
  const headline = [company.industry, place].filter(Boolean).join(" · ");
  const stage = TRIAGE_STAGE_LABELS[company.status] ?? company.status;
  const isAdded = filed !== null;
  const unsent = filed?.unsentNote
    ? { body: filed.unsentNote.note.body, kind: filed.unsentNote.note.kind, scope: "position" as const, reason: filed.unsentNote.reason }
    : null;

  return (
    <>
      <RecordBanner
        variant={isAdded ? "added" : "held"}
        title={isAdded ? "Added to the universe" : "Already in this position's universe"}
        detail={
          isAdded
            ? `${positionTitle} · ${stage}`
            : `Added${company.addedAt ? ` ${shortWhen(company.addedAt)}` : ""}${company.addedByName ? ` by ${company.addedByName}` : ""} · ${stage}`
        }
        projectId={projectId}
        isUndoing={undo.isPending}
        onUndo={() => undo.mutate({ projectId, triageCompanyId: company.id }, { onSuccess: onUndone })}
      />
      <div className="min-h-0 flex-1 overflow-y-auto p-3.5 pb-16">
        {undo.error && (
          <div className="mb-3">
            <RefusalNote refusal={undo.error} projectId={projectId} />
          </div>
        )}
        <SubjectCard
          shape="company"
          name={company.companyName}
          isNameLocked
          headline={headline || null}
          linkedinUrl={company.companyLinkedinUrl}
          imageSrc={company.logoUrl}
        >
          <span
            className={cn(
              "mt-2 inline-flex items-center gap-1.5 rounded-full border border-u-border-strong px-2.5 py-0.5 text-[11.5px] font-medium",
              colourPillClass("neutral"),
            )}
          >
            <span className="size-1.5 rounded-full bg-u-text3" />
            {stage}
          </span>
        </SubjectCard>

        <NotesSection
          target={{ subject: "company", projectId, triageCompanyId: company.id }}
          subjectName={company.companyName}
          teammates={[]}
          positionTitle={positionTitle}
          unsent={unsent}
        />
      </div>
    </>
  );
}
