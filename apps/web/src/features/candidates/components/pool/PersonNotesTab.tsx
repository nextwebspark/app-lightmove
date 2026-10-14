import { useMutation, useQueryClient, type UseQueryResult } from "@tanstack/react-query";
import { useState } from "react";
import { Button, LinesSkeleton, Select, useToast } from "../../../../components/ui";
import { messageFor } from "../../../../lib/errorCodes";
import { useSubmitShortcut } from "../../../../lib/useSubmitShortcut";
import * as poolApi from "../../api/poolApi";
import type { PersonNote, PersonNoteKind, PersonRecord } from "../../api/types";
import { NOTE_KINDS } from "../../lib/noteKinds";
import { NoteCard, NoteKindPicker } from "../NoteParts";
import { taskNoteOf } from "../PersonSections";
import { MentionTextArea } from "../../../tasks/components/MentionTextArea";
import { NextActionModal, type NextActionTarget } from "../../../tasks/components/NextActionModal";
import {
  mentionsPresent,
  NOTE_MAX_STORED,
  NOTE_TOO_LONG,
  serializeMentions,
  type Mention,
} from "../../../tasks/lib/mentions";
import { usePositionTeam } from "../../../tasks/lib/usePositionTeam";

/**
 * The Notes tab: a composer about a position or about the person, and the team's notes, pinned first.
 * Pinning is anyone's; changing or deleting a note is its author's or an admin's.
 */
export function PersonNotesTab({
  person,
  notes,
}: {
  person: PersonRecord;
  notes: UseQueryResult<PersonNote[]>;
}) {
  const toast = useToast();
  const refresh = useNoteRefresh(person.personId);
  const [kind, setKind] = useState<PersonNoteKind>("call");
  const [about, setAbout] = useState("");
  const [text, setText] = useState("");
  const [mentions, setMentions] = useState<Mention[]>([]);
  const [nextAction, setNextAction] = useState<NextActionTarget | null>(null);
  const team = usePositionTeam(about || null);
  const mapping = person.positions.find((position) => position.projectId === about) ?? null;

  const changeAbout = (projectId: string) => {
    setAbout(projectId);
    setMentions([]);
  };

  const body = about ? serializeMentions(text.trim(), mentionsPresent(text, mentions)) : text.trim();
  const tooLong = body.length > NOTE_MAX_STORED;
  // The position travels with the save: the select may change before the answer comes back.
  const saving = useMutation({
    mutationFn: (_save: { nextActionOn: typeof mapping }) =>
      poolApi.writePoolNote(person.personId, { kind, body, projectId: about || null }),
    onSuccess: (note, { nextActionOn }) => {
      setText("");
      setMentions([]);
      refresh();
      if (nextActionOn) {
        toast("Note saved");
        setNextAction({
          projectId: nextActionOn.projectId,
          candidateId: nextActionOn.candidateId,
          personId: person.personId,
          executiveName: person.fullName,
          note: taskNoteOf(note),
        });
      } else {
        toast("Note saved — your team sees it on every position");
      }
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const canSave = text.trim().length > 0 && !tooLong && !saving.isPending;
  const handleKeyDown = useSubmitShortcut(() => canSave && saving.mutate({ nextActionOn: null }));
  const list = notes.data ?? [];

  return (
    <div className="flex flex-col gap-3">
      <div className="rounded-[8px] border border-u-border p-2.5">
        <NoteKindPicker value={kind} onChange={setKind} />
        <MentionTextArea
          value={text}
          onChange={setText}
          mentions={mentions}
          onMentionsChange={setMentions}
          teammates={about ? team.teammates : null}
          onKeyDown={handleKeyDown}
          rows={3}
          maxLength={NOTE_MAX_STORED}
          ariaLabel="New note"
          placeholder={NOTE_KINDS.find((option) => option.value === kind)?.placeholder}
          className="font-sans text-[13px]/[1.55]"
        />
        {tooLong && (
          <p role="alert" className="mt-1.5 text-meta text-u-offlimits">
            {NOTE_TOO_LONG}
          </p>
        )}
        <div className="mt-2 flex items-center gap-2">
          <Select
            aria-label="Position this note is about"
            value={about}
            onChange={(event) => changeAbout(event.target.value)}
            className="w-auto min-w-0 flex-1 py-1 text-[12.5px]"
          >
            <option value="">No position — about the person</option>
            {person.positions.map((position) => (
              <option key={position.projectId} value={position.projectId}>
                {position.positionTitle ?? "A position"}
              </option>
            ))}
          </Select>
          {mapping && (
            <Button
              size="xs"
              variant="secondary"
              disabled={!canSave}
              loading={saving.isPending && saving.variables.nextActionOn !== null}
              onClick={() => saving.mutate({ nextActionOn: mapping })}
            >
              Save & set next action
            </Button>
          )}
          <Button
            size="xs"
            disabled={!canSave}
            loading={saving.isPending && saving.variables.nextActionOn === null}
            onClick={() => saving.mutate({ nextActionOn: null })}
          >
            Save note
          </Button>
        </div>
      </div>
      <p className="text-[12px] text-u-text3">
        Shared with your whole team, on every position this person is in. Client contacts never see notes.
      </p>

      {notes.isError ? (
        <p className="text-[13px] text-u-text3">{messageFor(notes.error)}</p>
      ) : notes.isPending ? (
        <LinesSkeleton />
      ) : list.length === 0 ? (
        <p className="text-[13px]/[1.6] text-u-text3">
          No notes yet. The first call or meeting you log will sit here for the whole team.
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {list.map((note) => (
            <PoolNoteCard key={note.id} personId={person.personId} note={note} />
          ))}
        </ul>
      )}
      <NextActionModal target={nextAction} onClose={() => setNextAction(null)} />
    </div>
  );
}

function PoolNoteCard({ personId, note }: { personId: string; note: PersonNote }) {
  const toast = useToast();
  const refresh = useNoteRefresh(personId);
  const pinning = useMutation({
    mutationFn: () => poolApi.pinPoolNote(personId, note.id, !note.pinned),
    onSuccess: refresh,
    onError: (error) => toast.error(messageFor(error)),
  });
  const revising = useMutation({
    mutationFn: (body: string) =>
      poolApi.revisePoolNote(personId, note.id, { kind: note.kind, body, projectId: note.projectId }),
    onSuccess: refresh,
    onError: (error) => toast.error(messageFor(error)),
  });
  const removing = useMutation({
    mutationFn: () => poolApi.removePoolNote(personId, note.id),
    onSuccess: () => {
      toast("Note deleted — the timeline keeps a line saying so");
      refresh();
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <NoteCard
      note={note}
      actions={{
        onPin: () => pinning.mutate(),
        onRevise: (body) => revising.mutate(body),
        onRemove: () => removing.mutate(),
        busy: pinning.isPending || revising.isPending || removing.isPending,
      }}
    />
  );
}

/** A note changes the notes, the timeline and the feed; nothing else about the person. */
function useNoteRefresh(personId: string) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: poolApi.POOL_NOTES_KEY(personId) });
    void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "timeline", personId] });
    void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "activity"] });
    void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "page"] });
  };
}
