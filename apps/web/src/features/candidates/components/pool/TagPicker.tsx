import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { useToast } from "../../../../components/ui";
import { Popover } from "../../../../components/ui/Popover";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { CandidateTag, PersonRecord } from "../../api/types";
import { TagPill } from "./TagPill";

/**
 * The drawer's "+ Tag": find a tag and toggle it on this person, or create one when nothing matches.
 * A retired tag is never offered; one the person already holds can still be taken off here.
 */
export function TagPicker({
  person,
  tags,
  onChanged,
}: {
  person: PersonRecord;
  tags: CandidateTag[];
  onChanged: (person: PersonRecord) => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [query, setQuery] = useState("");
  const typed = query.trim();
  const shown = tags.filter(
    (tag) =>
      (!tag.retired || person.tagIds.includes(tag.id)) &&
      tag.label.toLowerCase().includes(typed.toLowerCase()),
  );
  const exact = tags.some((tag) => tag.label.toLowerCase() === typed.toLowerCase());

  const refresh = (updated: PersonRecord) => {
    onChanged(updated);
    void queryClient.invalidateQueries({ queryKey: poolApi.TAGS_KEY });
  };
  const toggling = useMutation({
    mutationFn: (tag: CandidateTag) =>
      person.tagIds.includes(tag.id)
        ? poolApi.untagPerson(person.personId, tag.id)
        : poolApi.tagPerson(person.personId, tag.id),
    onSuccess: refresh,
    onError: (error) => toast(messageFor(error)),
  });
  const creating = useMutation({
    mutationFn: async (label: string) => {
      const tag = await poolApi.createTag(label);
      return poolApi.tagPerson(person.personId, tag.id);
    },
    onSuccess: (updated, label) => {
      toast(`Created the tag "${label}" for your team`);
      setQuery("");
      refresh(updated);
    },
    onError: (error) => toast(messageFor(error)),
  });

  return (
    <Popover
      label="Add a tag"
      width={260}
      triggerClassName="flex items-center gap-1 rounded-full border border-dashed border-u-border-strong px-2 py-px font-mono text-[11px] text-u-text3 hover:text-u-text2"
      trigger={() => (
        <>
          <Icon d={ICONS.plus} size={11} />
          Tag
        </>
      )}
    >
      {() => (
        <div className="p-2">
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Find or create a tag…"
            aria-label="Find or create a tag"
            maxLength={40}
            className="mb-1.5 w-full rounded-[6px] border border-u-border-strong bg-u-raised px-2 py-1 text-[13px] text-u-text outline-none"
          />
          <ul className="max-h-[220px] overflow-y-auto">
            {shown.map((tag) => {
              const on = person.tagIds.includes(tag.id);
              return (
                <li key={tag.id}>
                  <button
                    type="button"
                    role="checkbox"
                    aria-checked={on}
                    onClick={() => toggling.mutate(tag)}
                    className="flex w-full items-center gap-2 rounded-[5px] px-1.5 py-1 text-start hover:bg-u-raised"
                  >
                    <span
                      className={cn(
                        "grid size-3.5 flex-none place-items-center rounded-[3px] border",
                        on ? "border-u-accent bg-u-accent text-white" : "border-u-border-strong",
                      )}
                    >
                      {on && <Icon d={ICONS.check} size={10} />}
                    </span>
                    <TagPill tag={tag} />
                    <span className="ms-auto font-mono text-[10.5px] text-u-text3">{tag.holders}</span>
                  </button>
                </li>
              );
            })}
          </ul>
          {typed && !exact && (
            <button
              type="button"
              disabled={creating.isPending}
              onClick={() => creating.mutate(typed)}
              className="mt-1 w-full rounded-[5px] px-1.5 py-1 text-start font-mono text-[12px] text-u-accent hover:bg-u-raised"
            >
              Create &quot;{typed}&quot;
            </button>
          )}
          <p className="mt-2 border-t border-u-border pt-2 text-[11px]/[1.5] text-u-text3">
            Tags are shared by your whole team. Rename or retire them in{" "}
            <Link to="/settings/candidate-tags" className="text-u-accent hover:underline">
              Settings
            </Link>
            .
          </p>
        </div>
      )}
    </Popover>
  );
}
