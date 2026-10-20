import { useMutation, useQueryClient, type UseQueryResult } from "@tanstack/react-query";
import { useState } from "react";
import { Button, LinesSkeleton, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { NoteCard, NoteKindPicker } from "../../candidates/components/NoteParts";
import { useNoteCardActions } from "../../candidates/lib/useNoteCardActions";
import { NoteEditor } from "../../notes/components/NoteEditor";
import { NOTE_MAX_STORED, NOTE_TOO_LONG } from "../../tasks/lib/mentions";
import * as companyNotesApi from "../api/companyNotesApi";
import type { CompanyNote, CompanyNoteKind } from "../api/companyNotesApi";
import { TRIAGE_KEY_PREFIX } from "../api/triageApi";
import type { TriageCompany } from "../api/types";

const PLACEHOLDERS: Record<CompanyNoteKind, string> = {
  general: "What your team should know about this company…",
  call: "Who you spoke to there, and what happens next…",
  meeting: "Where you met, what came out of it…",
  email: "What was sent or received, and the reply…",
};

/**
 * The company drawer's Notes tab: the team's notes on this company for this position, pinned first, and a composer
 * above them. Staff-only, like a person's notes; the drawer draws no tab for a client seat.
 */
export function CompanyNotesTab({
  projectId,
  company,
  notes,
}: {
  projectId: string;
  company: TriageCompany;
  notes: UseQueryResult<CompanyNote[]>;
}) {
  const toast = useToast();
  const refresh = useCompanyNotesRefresh(projectId, company.id);
  const [kind, setKind] = useState<CompanyNoteKind>("general");
  const [draft, setDraft] = useState("");
  const body = draft.trim();
  const tooLong = body.length > NOTE_MAX_STORED;

  const saving = useMutation({
    mutationFn: () => companyNotesApi.writeCompanyNote(projectId, company.id, { kind, body, format: "markdown" }),
    onSuccess: () => {
      setDraft("");
      refresh();
      toast("Note saved");
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const canSave = body.length > 0 && !tooLong && !saving.isPending;
  const save = () => canSave && saving.mutate();
  const list = notes.data ?? [];

  return (
    <div className="flex flex-col gap-3 py-4">
      <div className="rounded-u-control border border-u-border p-2.5">
        <NoteKindPicker value={kind} onChange={setKind} />
        <div className="mt-2">
          <NoteEditor
            value={draft}
            format="markdown"
            onChange={setDraft}
            teammates={null}
            ariaLabel="New note"
            placeholder={PLACEHOLDERS[kind]}
            onSubmit={save}
            submitLabel="Save note"
            canSubmit={canSave}
            expandTitle={`Note on ${company.companyName}`}
          />
        </div>
        {tooLong && (
          <p role="alert" className="mt-1.5 text-meta text-u-offlimits">
            {NOTE_TOO_LONG}
          </p>
        )}
        <div className="mt-2 flex justify-end">
          <Button size="xs" disabled={!canSave} loading={saving.isPending} onClick={save}>
            Save note
          </Button>
        </div>
      </div>

      {notes.isError ? (
        <p className="text-note text-u-text3">{messageFor(notes.error)}</p>
      ) : notes.isPending ? (
        <LinesSkeleton />
      ) : list.length === 0 ? (
        <p className="text-note text-u-text3">
          No notes yet. What your team learns about this company for this position sits here. Client contacts never
          see notes.
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {list.map((note) => (
            <CompanyNoteCard key={note.id} projectId={projectId} companyId={company.id} note={note} />
          ))}
        </ul>
      )}
    </div>
  );
}

function CompanyNoteCard({ projectId, companyId, note }: { projectId: string; companyId: string; note: CompanyNote }) {
  const actions = useNoteCardActions(
    note,
    {
      pin: (noteId, pinned) => companyNotesApi.pinCompanyNote(projectId, companyId, noteId, pinned),
      revise: (current, body) =>
        companyNotesApi.reviseCompanyNote(projectId, companyId, current.id, {
          kind: current.kind,
          body,
          format: "markdown",
        }),
      remove: (noteId) => companyNotesApi.removeCompanyNote(projectId, companyId, noteId),
      restore: (noteId) => companyNotesApi.restoreCompanyNote(projectId, companyId, noteId),
    },
    useCompanyNotesRefresh(projectId, companyId),
  );
  return <NoteCard note={note} actions={actions} />;
}

/** A note changes the company's notes and the grid's count of them; nothing else about the company. */
function useCompanyNotesRefresh(projectId: string, companyId: string) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: companyNotesApi.COMPANY_NOTES_KEY(projectId, companyId) });
    void queryClient.invalidateQueries({ queryKey: TRIAGE_KEY_PREFIX(projectId) });
  };
}
