import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Modal, useToast } from "../../../../components/ui";
import { useRadioGroupKeys } from "../../../../components/ui/useRadioGroupKeys";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import * as projectsApi from "../../../projects/api/projectsApi";
import type { Project } from "../../../projects/api/types";
import { CANDIDATES_KEY_PREFIX } from "../../api/candidatesApi";
import * as poolApi from "../../api/poolApi";

/**
 * Adds the people named to a position as Identified, from the selection bar or one person's drawer.
 * Someone the position already holds stays as they are; the summary says how many that is before the
 * press, and the toast after it. Mount it only while open, so a cancelled choice is not kept.
 */
export function AddToPositionDialog({
  open,
  onClose,
  personIds,
  targetName,
  alreadyInByPosition,
  positions,
  onDone,
}: {
  open: boolean;
  onClose: () => void;
  personIds: string[];
  /** The one person's name when there is one, for the title and the toast. */
  targetName: string | null;
  /** How many of the people each position already holds. */
  alreadyInByPosition: Map<string, number>;
  positions: Project[];
  onDone?: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [chosen, setChosen] = useState<string | null>(null);
  const keys = useRadioGroupKeys(
    positions.map((position) => position.id),
    chosen,
    setChosen,
  );
  const target = targetName ?? `${personIds.length} ${personIds.length === 1 ? "person" : "people"}`;
  const alreadyIn = chosen ? (alreadyInByPosition.get(chosen) ?? 0) : 0;
  const toAdd = personIds.length - alreadyIn;

  const adding = useMutation({
    mutationFn: (projectId: string) => poolApi.addToPosition(projectId, personIds),
    onSuccess: (result, projectId) => {
      const title = positions.find((position) => position.id === projectId)?.positionTitle ?? "the position";
      toast(
        result.added === 0
          ? `Everyone was already in ${title}`
          : `Added ${result.added === 1 && targetName ? targetName : `${result.added} ${result.added === 1 ? "person" : "people"}`} to ${title}`,
      );
      void queryClient.invalidateQueries({ queryKey: poolApi.POOL_KEY });
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(projectId) });
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      onDone?.();
      onClose();
    },
    onError: (error) => toast(messageFor(error)),
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Add ${target} to a position`}
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            disabled={!chosen || toAdd <= 0}
            loading={adding.isPending}
            onClick={() => chosen && adding.mutate(chosen)}
          >
            {!chosen || toAdd <= 0 ? "Nothing to add" : toAdd === 1 ? "Add 1 person" : `Add ${toAdd} people`}
          </Button>
        </div>
      }
    >
      <p className="mb-3 text-[13px]/[1.55] text-u-text2">
        They are added as Identified and appear on that position&apos;s In universe page. Their notes, contacts and
        history come with them.
      </p>
      {positions.length === 0 && (
        <p className="text-[13px] text-u-text3">You are not on any position you can add people to.</p>
      )}
      <div
        ref={keys.ref}
        role="radiogroup"
        aria-label="Position"
        onKeyDown={keys.onKeyDown}
        className="flex max-h-[320px] flex-col gap-1.5 overflow-y-auto"
      >
        {positions.map((position, index) => {
          const held = alreadyInByPosition.get(position.id) ?? 0;
          return (
            <button
              key={position.id}
              type="button"
              role="radio"
              aria-checked={chosen === position.id}
              tabIndex={chosen === position.id || (chosen === null && index === 0) ? 0 : -1}
              onClick={() => setChosen(position.id)}
              className={cn(
                "flex items-center gap-2 rounded-[8px] border px-3 py-2 text-start",
                chosen === position.id ? "border-u-accent bg-u-accent-tint/40" : "border-u-border hover:bg-u-raised",
              )}
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[13px] font-semibold text-u-text">{position.positionTitle}</span>
                <span className="block truncate font-mono text-[11px] text-u-text3">{position.clientName}</span>
              </span>
              {held > 0 && (
                <span className="flex-none font-mono text-[11px] text-u-text3">
                  {personIds.length === 1 ? "Already in it" : `${held} already in it`}
                </span>
              )}
            </button>
          );
        })}
      </div>
      {chosen && (
        <p className="mt-3 rounded-[8px] bg-u-raised px-3 py-2 text-[12.5px] text-u-text2">
          {toAdd <= 0 ? "Nobody new" : toAdd === 1 ? "1 person" : `${toAdd} people`} will be added as Identified.
          {alreadyIn > 0 &&
            ` ${alreadyIn === 1 ? "1 is" : `${alreadyIn} are`} already in this position and ${alreadyIn === 1 ? "stays as is" : "stay as they are"}.`}
        </p>
      )}
    </Modal>
  );
}
