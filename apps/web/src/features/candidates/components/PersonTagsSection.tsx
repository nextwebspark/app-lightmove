import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as poolApi from "../api/poolApi";
import type { PersonRecord } from "../api/types";
import { usePersonRecordUpdate } from "../lib/usePersonRecordUpdate";
import { TagPicker } from "./pool/TagPicker";
import { TagPill } from "./pool/TagPill";

/**
 * The team's tags on a person, as one line under either drawer's header — a fact about the person like
 * their owner, so it is on view whichever tab is open. Staff-only, like the person record.
 */
export function PersonTagsSection({ person, className }: { person: PersonRecord; className?: string }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const changed = usePersonRecordUpdate();
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

  return (
    <section aria-label="Tags" className={className}>
      <div className="flex flex-wrap items-center gap-1.5">
        <Icon d={ICONS.tag} size={12} className="me-0.5 flex-none text-u-text3" />
        {held.map((tag) => (
          <span key={tag.id} className="flex items-center">
            <TagPill tag={tag} className="pe-1" />
            <button
              type="button"
              aria-label={`Remove tag ${tag.label}`}
              onClick={() => untagging.mutate(tag.id)}
              disabled={untagging.isPending}
              className="-ms-1 grid size-4 place-items-center rounded-full text-u-text3 hover:text-u-text disabled:opacity-50"
            >
              <Icon d={ICONS.close} size={10} />
            </button>
          </span>
        ))}
        <TagPicker person={person} tags={tags} onChanged={changed} />
      </div>
    </section>
  );
}
