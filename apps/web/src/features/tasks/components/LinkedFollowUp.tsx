import { DateInput, Input } from "../../../components/ui";
import type { FollowUpDraft } from "../lib/followUp";
import type { TeammateOption } from "../lib/team";
import { AssigneePicker } from "./AssigneePicker";

/** The composer's "Create a linked follow-up" (`Position.dc.html`): its title, owner (me first) and due date. */
export function LinkedFollowUp({
  draft,
  onChange,
  teammates,
}: {
  draft: FollowUpDraft;
  onChange: (draft: FollowUpDraft) => void;
  teammates: readonly TeammateOption[];
}) {
  return (
    <div className="mt-2.5 rounded-u-control border border-u-border p-3">
      <label className="flex cursor-pointer items-center gap-2">
        <input
          type="checkbox"
          checked={draft.on}
          onChange={(event) => onChange({ ...draft, on: event.target.checked })}
          className="size-4 accent-u-accent-solid"
        />
        <span className="text-note font-semibold text-u-text">Create a linked follow-up</span>
        <span className="text-meta text-u-text3">Optional</span>
      </label>
      {draft.on && (
        <>
          <p className="mt-2 text-meta text-u-text3">A separate task linked to this note.</p>
          <Input
            aria-label="Follow-up task"
            placeholder="Follow-up task"
            maxLength={200}
            value={draft.title}
            onChange={(event) => onChange({ ...draft, title: event.target.value })}
            className="mt-2 py-2 font-sans text-note"
          />
          <div className="mt-2.5 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <span className="text-meta font-medium text-u-text3">Owner</span>
              <AssigneePicker
                label="Owner"
                compact
                options={teammates}
                value={draft.assigneeUserId}
                onChange={(assigneeUserId) => onChange({ ...draft, assigneeUserId })}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <span className="text-meta font-medium text-u-text3">Due date</span>
              <DateInput
                ariaLabel="Follow-up due date"
                value={draft.date}
                onChange={(date) => onChange({ ...draft, date })}
                className="py-2"
              />
            </div>
          </div>
        </>
      )}
    </div>
  );
}
