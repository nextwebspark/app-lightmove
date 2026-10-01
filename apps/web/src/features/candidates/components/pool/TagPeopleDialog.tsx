import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, Modal, useToast } from "../../../../components/ui";
import { SegmentedControl } from "../../../../components/ui/SegmentedControl";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { CandidateTag } from "../../api/types";
import { TagPill } from "./TagPill";

const TAG_MODES = [
  { value: "add", label: "Add tags" },
  { value: "remove", label: "Remove tags" },
] as const;

/** Puts tags on every person ticked, or takes them off. Mount it only while open. */
export function TagPeopleDialog({
  open,
  onClose,
  personIds,
  holdersByTag,
  tags,
  onDone,
}: {
  open: boolean;
  onClose: () => void;
  personIds: string[];
  /** How many of the people each tag is already on. */
  holdersByTag: Map<string, number>;
  tags: CandidateTag[];
  onDone?: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [remove, setRemove] = useState(false);
  const [chosen, setChosen] = useState<string[]>([]);
  const target = `${personIds.length} ${personIds.length === 1 ? "person" : "people"}`;

  const saving = useMutation({
    mutationFn: () => poolApi.retagPeople(personIds, chosen, remove),
    onSuccess: () => {
      toast(`${remove ? "Untagged" : "Tagged"} ${target}`);
      void queryClient.invalidateQueries({ queryKey: poolApi.POOL_KEY });
      void queryClient.invalidateQueries({ queryKey: poolApi.TAGS_KEY });
      onDone?.();
      onClose();
    },
    onError: (error) => toast(messageFor(error)),
  });

  const toggle = (tagId: string) =>
    setChosen((current) => (current.includes(tagId) ? current.filter((id) => id !== tagId) : [...current, tagId]));

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Tag ${target}`}
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={chosen.length === 0} loading={saving.isPending} onClick={() => saving.mutate()}>
            {remove ? "Remove" : "Add"} {chosen.length === 1 ? "1 tag" : `${chosen.length} tags`}
          </Button>
        </div>
      }
    >
      <SegmentedControl
        label="Add or remove"
        options={TAG_MODES}
        value={remove ? "remove" : "add"}
        onChange={(mode) => setRemove(mode === "remove")}
        className="mb-3"
      />
      <ul className="flex flex-col gap-1">
        {tags.map((tag) => (
          <li key={tag.id}>
            <button
              type="button"
              role="checkbox"
              aria-checked={chosen.includes(tag.id)}
              onClick={() => toggle(tag.id)}
              className="flex w-full items-center gap-2 rounded-[6px] px-2 py-1.5 text-start hover:bg-u-raised"
            >
              <span
                className={cn(
                  "grid size-4 flex-none place-items-center rounded-[4px] border",
                  chosen.includes(tag.id) ? "border-u-accent bg-u-accent text-white" : "border-u-border-strong",
                )}
              >
                {chosen.includes(tag.id) && <Icon d={ICONS.check} size={11} />}
              </span>
              <TagPill tag={tag} />
              <span className="ms-auto font-mono text-[11px] text-u-text3">
                {holdersByTag.get(tag.id) ?? 0} of {personIds.length} have it
              </span>
            </button>
          </li>
        ))}
      </ul>
    </Modal>
  );
}
