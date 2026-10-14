import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { Popover } from "../../../components/ui/Popover";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { FITTING_PILL, LEFT_OUT_PILL, useFittingCount } from "../../../lib/useFittingCount";
import * as poolApi from "../api/poolApi";
import type { CandidateTag, PersonRecord } from "../api/types";
import { usePersonRecordUpdate } from "../lib/usePersonRecordUpdate";
import { TagPicker } from "./pool/TagPicker";
import { TagPill } from "./pool/TagPill";

const MORE_CHIP = "rounded-full bg-u-raised px-1.5 font-mono text-meta font-semibold text-u-text2";

/**
 * The team's tags on a person, as one line under either drawer's header — a fact about the person like
 * their owner, so it is on view whichever tab is open. Tags past the line fold into a "+N" chip, so the
 * header never grows. Staff-only, like the person record.
 */
export function PersonTagsSection({ person, className }: { person: PersonRecord; className?: string }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const changed = usePersonRecordUpdate();
  const slot = useRef<HTMLDivElement>(null);
  const chip = useRef<HTMLSpanElement>(null);
  const picker = useRef<HTMLSpanElement>(null);
  const catalog = useQuery({ queryKey: poolApi.TAGS_KEY, queryFn: ({ signal }) => poolApi.tagCatalog(signal) });
  const tags = catalog.data ?? [];
  const held = person.tagIds
    .map((id) => tags.find((tag) => tag.id === id))
    .filter((tag) => tag !== undefined);
  const untagging = useMutation({
    mutationFn: (tagId: string) => poolApi.untagPerson(person.personId, tagId),
    onSuccess: (updated) => {
      changed(updated);
      void queryClient.invalidateQueries({ queryKey: poolApi.TAGS_KEY });
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const fitting = useFittingCount({
    slot,
    chip,
    tail: picker,
    lines: 1,
    contentKey: held.map((tag) => `${tag.id}:${tag.label}`).join("|"),
  });
  const hidden = Math.max(0, held.length - fitting);

  const removablePill = (tag: CandidateTag) => (
    <TagPill
      tag={tag}
      onRemove={() => untagging.mutate(tag.id)}
      removing={untagging.isPending}
    />
  );

  return (
    <section aria-label="Tags" className={cn("flex items-center gap-1.5", className)}>
      <Icon d={ICONS.tag} size={12} className="me-0.5 flex-none text-u-text3" />
      <div ref={slot} className="relative flex min-w-0 flex-1 items-center gap-1.5">
        {held.map((tag, index) => {
          const isLeftOut = index >= fitting;
          return (
            <span
              key={tag.id}
              {...FITTING_PILL}
              aria-hidden={isLeftOut || undefined}
              inert={isLeftOut}
              className={cn("flex min-w-0", isLeftOut && LEFT_OUT_PILL)}
            >
              {removablePill(tag)}
            </span>
          );
        })}
        {hidden > 0 && (
          <Popover
            width={280}
            hasPopup="dialog"
            label={`Show all ${held.length} tags`}
            triggerClassName={cn(MORE_CHIP, "flex-none hover:text-u-text")}
            trigger={() => `+${hidden}`}
          >
            {() => (
              <div role="dialog" aria-label="All tags" className="p-2">
                <div className="flex items-center gap-2 px-1 pb-2">
                  <h3 className="text-body font-semibold text-u-text">Tags</h3>
                  <span className="font-mono text-meta text-u-text3">{held.length}</span>
                </div>
                <div className="flex flex-wrap gap-1.5 px-1 pb-1">
                  {held.map((tag) => (
                    <span key={tag.id} className="flex min-w-0">
                      {removablePill(tag)}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </Popover>
        )}
        <span ref={picker} className="flex flex-none">
          <TagPicker person={person} tags={tags} onChanged={changed} />
        </span>
        <span ref={chip} aria-hidden className={cn(MORE_CHIP, LEFT_OUT_PILL)}>
          +99
        </span>
      </div>
    </section>
  );
}
