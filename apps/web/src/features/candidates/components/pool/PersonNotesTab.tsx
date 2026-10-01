import { useMutation, useQueryClient, type UseQueryResult } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Avatar } from "../../../../components/ui/Avatar";
import { Button, Select, TextArea, useToast } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import { useSubmitShortcut } from "../../../../lib/useSubmitShortcut";
import * as poolApi from "../../api/poolApi";
import type { PersonNote, PersonNoteKind, PersonRecord } from "../../api/types";
import { shortWhen } from "../../lib/candidateActivity";

const NOTE_KINDS: { value: PersonNoteKind; label: string; placeholder: string }[] = [
  { value: "general", label: "Note", placeholder: "What your team should know about this person…" },
  { value: "call", label: "Call", placeholder: "What was said on the call, and what happens next…" },
  { value: "meeting", label: "Meeting", placeholder: "Where you met, what came out of it…" },
  { value: "email", label: "Email", placeholder: "What was sent or received, and the reply…" },
];

const KIND_LABELS = Object.fromEntries(NOTE_KINDS.map((kind) => [kind.value, kind.label]));

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
  const queryClient = useQueryClient();
  const toast = useToast();
  const [kind, setKind] = useState<PersonNoteKind>("call");
  const [about, setAbout] = useState("");
  const [text, setText] = useState("");
  const refresh = () => void queryClient.invalidateQueries({ queryKey: poolApi.POOL_KEY });

  const saving = useMutation({
    mutationFn: () =>
      poolApi.writePoolNote(person.personId, { kind, body: text.trim(), projectId: about || null }),
    onSuccess: () => {
      setText("");
      toast("Note saved — your team sees it on every position");
      refresh();
    },
    onError: (error) => toast(messageFor(error)),
  });
  const canSave = text.trim().length > 0 && !saving.isPending;
  const handleKeyDown = useSubmitShortcut(() => canSave && saving.mutate());
  const list = notes.data ?? [];

  return (
    <div className="flex flex-col gap-3">
      <div className="rounded-[8px] border border-u-border p-2.5">
        <div role="radiogroup" aria-label="Kind of note" className="mb-2 flex flex-wrap gap-1.5">
          {NOTE_KINDS.map((option) => (
            <button
              key={option.value}
              type="button"
              role="radio"
              aria-checked={kind === option.value}
              onClick={() => setKind(option.value)}
              className={cn(
                "rounded-full border px-2.5 py-0.5 font-mono text-[11px] transition",
                kind === option.value
                  ? "border-u-accent bg-u-accent-tint text-u-accent"
                  : "border-u-border text-u-text3 hover:text-u-text2",
              )}
            >
              {option.label}
            </button>
          ))}
        </div>
        <TextArea
          value={text}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={handleKeyDown}
          rows={3}
          maxLength={4000}
          aria-label="New note"
          placeholder={NOTE_KINDS.find((option) => option.value === kind)?.placeholder}
          className="font-sans text-[13px]/[1.55]"
        />
        <div className="mt-2 flex items-center gap-2">
          <Select
            aria-label="Position this note is about"
            value={about}
            onChange={(event) => setAbout(event.target.value)}
            className="w-auto min-w-0 flex-1 py-1 text-[12.5px]"
          >
            <option value="">No position — about the person</option>
            {person.positions.map((position) => (
              <option key={position.projectId} value={position.projectId}>
                {position.positionTitle ?? "A position"}
              </option>
            ))}
          </Select>
          <Button className="px-3 py-1.5 text-[12.5px]" disabled={!canSave} loading={saving.isPending} onClick={() => saving.mutate()}>
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
        <p className="text-[13px] text-u-text3">Loading…</p>
      ) : list.length === 0 ? (
        <p className="text-[13px]/[1.6] text-u-text3">
          No notes yet. The first call or meeting you log will sit here for the whole team.
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {list.map((note) => (
            <NoteCard key={note.id} personId={person.personId} note={note} onChanged={refresh} />
          ))}
        </ul>
      )}
    </div>
  );
}

function NoteCard({ personId, note, onChanged }: { personId: string; note: PersonNote; onChanged: () => void }) {
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(note.body);

  const pinning = useMutation({
    mutationFn: () => poolApi.pinPoolNote(personId, note.id, !note.pinned),
    onSuccess: onChanged,
    onError: (error) => toast(messageFor(error)),
  });
  const revising = useMutation({
    mutationFn: () =>
      poolApi.revisePoolNote(personId, note.id, { kind: note.kind, body: draft.trim(), projectId: note.projectId }),
    onSuccess: () => {
      setEditing(false);
      onChanged();
    },
    onError: (error) => toast(messageFor(error)),
  });
  const removing = useMutation({
    mutationFn: () => poolApi.removePoolNote(personId, note.id),
    onSuccess: () => {
      toast("Note deleted — the timeline keeps a line saying so");
      onChanged();
    },
    onError: (error) => toast(messageFor(error)),
  });

  return (
    <li
      className={cn(
        "rounded-[8px] border px-3 py-2.5",
        note.pinned ? "border-u-accent/60 bg-u-raised" : "border-u-border",
      )}
    >
      <div className="flex items-center gap-2">
        <Avatar id={note.authorUserId} name={note.authorName ?? "Someone"} src={note.authorAvatarUrl} size="sm" />
        <span className="text-[12.5px] font-semibold">{note.authorName ?? "Someone"}</span>
        <span className="rounded-[4px] bg-u-raised px-1.5 py-px font-mono text-[9.5px] font-semibold uppercase tracking-[0.06em] text-u-text3">
          {KIND_LABELS[note.kind] ?? "Note"}
        </span>
        {note.pinned && (
          <span className="flex items-center gap-0.5 font-mono text-[10px] font-semibold uppercase tracking-[0.06em] text-u-accent">
            <Icon d={ICONS.pin} size={10} />
            Pinned
          </span>
        )}
        <time
          dateTime={note.createdAt}
          title={new Date(note.createdAt).toLocaleString()}
          className="ms-auto flex-none font-mono text-[11px] text-u-text3"
        >
          {shortWhen(note.createdAt)}
        </time>
      </div>
      {editing ? (
        <div className="mt-2">
          <TextArea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            rows={3}
            maxLength={4000}
            aria-label="Edit note"
            className="font-sans text-[13px]/[1.55]"
          />
          <div className="mt-2 flex justify-end gap-2">
            <Button variant="secondary" className="px-3 py-1 text-[12px]" onClick={() => setEditing(false)}>
              Cancel
            </Button>
            <Button
              className="px-3 py-1 text-[12px]"
              disabled={draft.trim().length === 0}
              loading={revising.isPending}
              onClick={() => revising.mutate()}
            >
              Save
            </Button>
          </div>
        </div>
      ) : (
        <p className="mt-1.5 whitespace-pre-wrap text-[13px]/[1.55] text-u-text2">{note.body}</p>
      )}
      <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[11px] text-u-text3">
        {note.projectTitle && <span>About {note.projectTitle}</span>}
        {note.editedAt && (
          <span title={new Date(note.editedAt).toLocaleString()}>Edited by {note.editedByName ?? "someone"}</span>
        )}
        <span className="ms-auto flex gap-3">
          <button type="button" onClick={() => pinning.mutate()} disabled={pinning.isPending} className="hover:text-u-text">
            {note.pinned ? "Unpin" : "Pin"}
          </button>
          {note.editable && !editing && (
            <button
              type="button"
              onClick={() => {
                setDraft(note.body);
                setEditing(true);
              }}
              className="hover:text-u-text"
            >
              Edit
            </button>
          )}
          {note.editable && (
            <button
              type="button"
              onClick={() => removing.mutate()}
              disabled={removing.isPending}
              className="hover:text-u-offlimits"
            >
              Delete
            </button>
          )}
        </span>
      </div>
    </li>
  );
}
