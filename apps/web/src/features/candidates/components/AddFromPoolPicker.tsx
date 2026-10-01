import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Modal, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { useDebouncedValue } from "../../../lib/useComboboxList";
import * as projectsApi from "../../projects/api/projectsApi";
import { CANDIDATES_KEY_PREFIX } from "../api/candidatesApi";
import * as poolApi from "../api/poolApi";
import type { PoolRow } from "../api/types";
import { NO_POOL_FILTERS } from "../lib/poolFilters";
import { usePoolLookups } from "../lib/usePoolLookups";
import { PersonAvatar } from "./pool/PersonAvatar";
import { TagPill } from "./pool/TagPill";

const PICKER_PAGE_SIZE = 25;

/**
 * Picks people the team already mapped elsewhere and adds them to this position as Identified, through
 * the same write the Candidates page's selection bar uses. Someone already here is shown, never picked.
 */
export function AddFromPoolPicker({
  projectId,
  onClose,
}: {
  projectId: string;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { tagsById } = usePoolLookups();
  const [query, setQuery] = useState("");
  const debouncedQuery = useDebouncedValue(query.trim(), 250);
  const [picked, setPicked] = useState<Map<string, string>>(new Map());

  const filters = { ...NO_POOL_FILTERS, q: debouncedQuery };
  const people = useQuery({
    queryKey: poolApi.POOL_PAGE_KEY(filters, 0, PICKER_PAGE_SIZE),
    queryFn: ({ signal }) => poolApi.listPool(filters, 0, PICKER_PAGE_SIZE, signal),
    placeholderData: keepPreviousData,
  });

  const adding = useMutation({
    mutationFn: () => poolApi.addToPosition(projectId, [...picked.keys()]),
    onSuccess: (result) => {
      const onlyName = picked.size === 1 ? [...picked.values()][0] : null;
      toast(
        result.added === 0
          ? "Everyone picked was already in this position"
          : `Added ${result.added === 1 && onlyName ? onlyName : `${result.added} people`} to this position as Identified`,
      );
      void queryClient.invalidateQueries({ queryKey: poolApi.POOL_KEY });
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(projectId) });
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      onClose();
    },
    onError: (error) => toast(messageFor(error)),
  });

  const handleToggle = (person: PoolRow) =>
    setPicked((current) => {
      const next = new Map(current);
      if (next.has(person.personId)) next.delete(person.personId);
      else next.set(person.personId, person.fullName);
      return next;
    });

  const rows = people.data?.people ?? [];

  return (
    <Modal
      open
      onClose={onClose}
      title="Add from your candidates"
      className="md:w-[620px]"
      footer={
        <div className="flex items-center justify-end gap-2">
          <span className="me-auto font-mono text-[12px] text-u-text3">
            {picked.size === 0 ? "Pick one or more" : `${picked.size} selected`}
          </span>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={picked.size === 0} loading={adding.isPending} onClick={() => adding.mutate()}>
            {picked.size === 0
              ? "Add to this position"
              : picked.size === 1
                ? "Add 1 person"
                : `Add ${picked.size} people`}
          </Button>
        </div>
      }
    >
      <p className="mb-3 text-[13px] text-u-text2">
        People your team already mapped on other positions. They come in as Identified, with their notes, contacts
        and history.
      </p>
      <input
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Search name, title, company…"
        aria-label="Search your candidates"
        autoFocus
        className="mb-3 w-full rounded-[8px] border border-u-border bg-u-raised px-3 py-2 font-mono text-[13px] text-u-text outline-none focus:border-u-accent"
      />
      {people.isError ? (
        <p role="alert" className="py-6 text-center text-[13px] text-u-text3">
          {messageFor(people.error)}
        </p>
      ) : people.isSuccess && rows.length === 0 ? (
        <p className="py-6 text-center font-mono text-[12.5px] text-u-text3">
          {debouncedQuery ? "Nobody in your candidates matches." : "Your team has no candidates yet."}
        </p>
      ) : (
        <ul aria-label="Your candidates" className="flex max-h-[360px] flex-col gap-1.5 overflow-y-auto">
          {rows.map((person) => {
            const isHere = person.positions.some((position) => position.projectId === projectId);
            const isPicked = picked.has(person.personId);
            const elsewhere = person.positions.filter((position) => position.projectId !== projectId);
            return (
              <li key={person.personId}>
                <label
                  className={cn(
                    "flex items-center gap-3 rounded-[8px] border px-3 py-2",
                    isHere ? "cursor-default opacity-55" : "cursor-pointer",
                    isPicked ? "border-u-accent bg-u-accent-tint/40" : "border-u-border",
                  )}
                >
                  <input
                    type="checkbox"
                    checked={isPicked}
                    disabled={isHere}
                    onChange={() => handleToggle(person)}
                    aria-label={`Pick ${person.fullName}`}
                    className="accent-u-accent"
                  />
                  <PersonAvatar person={person} size="sm" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[13px] font-semibold text-u-text">{person.fullName}</span>
                    <span className="block truncate font-mono text-[11.5px] text-u-text3">
                      {[person.title, person.companyName].filter(Boolean).join(" · ")}
                    </span>
                    {person.tagIds.length > 0 && (
                      <span className="mt-1 flex flex-wrap gap-1">
                        {person.tagIds.map((tagId) => {
                          const tag = tagsById.get(tagId);
                          return tag ? <TagPill key={tagId} tag={tag} /> : null;
                        })}
                      </span>
                    )}
                  </span>
                  <span className="flex-none text-end font-mono text-[11px] text-u-text3">
                    {isHere
                      ? "In this position"
                      : elsewhere.length > 0
                        ? (elsewhere[0].positionTitle ?? "Another position") +
                          (elsewhere.length > 1 ? ` +${elsewhere.length - 1}` : "")
                        : "In no position"}
                  </span>
                </label>
              </li>
            );
          })}
        </ul>
      )}
    </Modal>
  );
}
