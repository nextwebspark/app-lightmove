import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, Input, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import * as poolApi from "../../candidates/api/poolApi";
import type { CandidateTag, CandidateTagColour } from "../../candidates/api/types";
import { TagPill } from "../../candidates/components/pool/TagPill";
import { TAG_COLOURS } from "../../candidates/lib/tagStyle";

/**
 * Settings → Candidate tags: the workspace's own labels on its people. Anyone on the team tags a person
 * or creates a tag from the candidate drawer; an admin renames, recolours and retires them here.
 */
export function SettingsCandidateTagsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [label, setLabel] = useState("");
  const tags = useQuery({ queryKey: poolApi.TAGS_KEY, queryFn: ({ signal }) => poolApi.tagCatalog(signal) });
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: poolApi.TAGS_KEY });
    void queryClient.invalidateQueries({ queryKey: poolApi.POOL_KEY });
  };

  const adding = useMutation({
    mutationFn: () => poolApi.createTag(label.trim()),
    onSuccess: () => {
      setLabel("");
      toast("Tag added for the whole team");
      refresh();
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <>
      <PageHeader
        title="Candidate tags"
        subtitle="Your team's own labels on candidates, shared by every position. Anyone on the team tags a person or creates a tag from the candidate drawer; an admin renames, recolours and retires them here."
      />

      <form
        className="mb-4 flex gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          if (label.trim()) adding.mutate();
        }}
      >
        <Input
          value={label}
          onChange={(event) => setLabel(event.target.value)}
          placeholder="New tag, e.g. Board-ready"
          aria-label="New tag"
          maxLength={40}
          className="max-w-[320px]"
        />
        <Button type="submit" disabled={!label.trim()} loading={adding.isPending}>
          Add tag
        </Button>
      </form>

      {tags.isError ? (
        <p className="text-[13px] text-u-text3">{messageFor(tags.error)}</p>
      ) : tags.isPending ? (
        <p className="text-[13px] text-u-text3">Loading…</p>
      ) : (
        <ul className="divide-y divide-u-border rounded-[10px] border border-u-border bg-u-surface">
          {tags.data.map((tag) => (
            <TagRow key={tag.id} tag={tag} onChanged={refresh} />
          ))}
        </ul>
      )}

      <p className="mt-3 max-w-[680px] text-[12px]/[1.6] text-u-text3">
        A retired tag stays on the people who have it and in their timeline, but can no longer be picked. A rename
        reaches every person at once, because a person holds the tag, not its spelling.
      </p>
    </>
  );
}

function TagRow({ tag, onChanged }: { tag: CandidateTag; onChanged: () => void }) {
  const toast = useToast();
  const [renaming, setRenaming] = useState(false);
  const [draft, setDraft] = useState(tag.label);

  const updating = useMutation({
    mutationFn: (change: { label?: string; colour?: CandidateTagColour; retired?: boolean }) =>
      poolApi.updateTag(tag.id, change),
    onSuccess: (_, change) => {
      if (change.label !== undefined) {
        setRenaming(false);
        toast("Renamed on every person who has it");
      }
      onChanged();
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <li className={cn("flex flex-wrap items-center gap-3 px-4 py-3", tag.retired && "opacity-55")}>
      <div role="radiogroup" aria-label={`Colour of ${tag.label}`} className="flex gap-1">
        {TAG_COLOURS.map((colour) => (
          <button
            key={colour.value}
            type="button"
            role="radio"
            aria-checked={tag.colour === colour.value}
            aria-label={colour.label}
            disabled={updating.isPending}
            onClick={() => tag.colour !== colour.value && updating.mutate({ colour: colour.value })}
            className={cn(
              "size-4 rounded-full",
              colour.swatch,
              tag.colour === colour.value && "ring-2 ring-u-accent ring-offset-1 ring-offset-u-surface",
            )}
          />
        ))}
      </div>
      {renaming ? (
        <form
          id={`rename-${tag.id}`}
          className="contents"
          onSubmit={(event) => {
            event.preventDefault();
            if (draft.trim()) updating.mutate({ label: draft.trim() });
          }}
        >
          <Input
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => event.key === "Escape" && setRenaming(false)}
            aria-label={`Rename ${tag.label}`}
            maxLength={40}
            autoFocus
            className="max-w-[240px] py-1"
          />
        </form>
      ) : (
        <TagPill tag={tag} />
      )}
      <span className="font-mono text-[11.5px] text-u-text3">
        {tag.holders === 1 ? "1 person" : `${tag.holders} people`}
        {tag.retired && " · retired"}
      </span>
      <span className="ms-auto flex gap-2">
        {renaming ? (
          <>
            <Button variant="ghost" className="px-3 py-1 text-[12px]" onClick={() => setRenaming(false)}>
              Cancel
            </Button>
            <Button
              type="submit"
              form={`rename-${tag.id}`}
              variant="secondary"
              className="px-3 py-1 text-[12px]"
              disabled={!draft.trim()}
              loading={updating.isPending}
            >
              Save
            </Button>
          </>
        ) : (
          <Button
            variant="ghost"
            className="px-3 py-1 text-[12px]"
            onClick={() => {
              setDraft(tag.label);
              setRenaming(true);
            }}
          >
            Rename
          </Button>
        )}
        <Button
          variant="ghost"
          className="px-3 py-1 text-[12px]"
          title={tag.retired ? "Offer this tag again" : "Stop offering this tag; people keep it"}
          disabled={updating.isPending}
          onClick={() => updating.mutate({ retired: !tag.retired })}
        >
          {tag.retired ? "Restore" : "Retire"}
        </Button>
      </span>
    </li>
  );
}
