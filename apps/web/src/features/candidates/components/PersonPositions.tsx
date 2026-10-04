import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Select, useToast } from "../../../components/ui";
import { formatInstantDate } from "../../../lib/format";
import { messageFor } from "../../../lib/errorCodes";
import { changeCandidateStatus, CANDIDATES_KEY_PREFIX } from "../api/candidatesApi";
import * as poolApi from "../api/poolApi";
import type { CandidateStatus, PersonPosition } from "../api/types";
import { CANDIDATE_SOURCE_STYLES, CANDIDATE_STATUSES, candidateStatusStyle } from "../lib/candidateVocabulary";
import { TabSectionHeading } from "./PersonSections";

/** The positions a person sits on, in either drawer's Records tab. */
export function PositionsSection({
  title,
  positions,
  action,
  empty,
  onStatusChanged,
}: {
  title: string;
  positions: readonly PersonPosition[];
  action?: ReactNode;
  empty?: ReactNode;
  onStatusChanged?: () => void;
}) {
  return (
    <section aria-label={title} className="border-t border-u-border py-4">
      <TabSectionHeading title={title} action={action} />
      {positions.length === 0 ? (
        empty
      ) : (
        <ul className="flex flex-col gap-2">
          {positions.map((position) => (
            <PositionCard key={position.candidateId} position={position} onStatusChanged={onStatusChanged} />
          ))}
        </ul>
      )}
    </section>
  );
}

function PositionCard({
  position,
  onStatusChanged,
}: {
  position: PersonPosition;
  onStatusChanged?: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const status = candidateStatusStyle(position.status);
  const moving = useMutation({
    mutationFn: (next: CandidateStatus) => changeCandidateStatus(position.projectId, position.candidateId, next),
    onSuccess: (_, next) => {
      toast(`Marked ${candidateStatusStyle(next).label} on ${position.positionTitle ?? "the position"}`);
      void queryClient.invalidateQueries({ queryKey: poolApi.POOL_KEY });
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(position.projectId) });
      onStatusChanged?.();
    },
    onError: (error) => toast(messageFor(error)),
  });

  return (
    <li className="flex items-center gap-3 rounded-[8px] border border-u-border px-3 py-2.5">
      <Icon d={ICONS.position} size={15} className="flex-none text-u-text3" />
      <div className="min-w-0 flex-1">
        <Link
          to={`/projects/${position.projectId}/companies`}
          className="block truncate text-[13px] font-semibold text-u-text hover:underline"
        >
          {position.positionTitle ?? "Untitled position"}
        </Link>
        <span className="block truncate font-mono text-[11px] text-u-text3">
          Added by {position.addedByName ?? "someone"} · {formatInstantDate(position.addedAt)} ·{" "}
          {CANDIDATE_SOURCE_STYLES[position.source]?.label ?? position.source}
        </span>
      </div>
      {position.workable ? (
        <Select
          aria-label={`Status on ${position.positionTitle ?? "this position"}`}
          value={position.status}
          disabled={moving.isPending}
          onChange={(event) => moving.mutate(event.target.value as CandidateStatus)}
          className="w-auto py-1 text-[12.5px]"
        >
          {CANDIDATE_STATUSES.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
      ) : (
        <span className={`flex-none rounded-full px-2 py-px font-mono text-[10px] font-semibold ${status.className}`}>
          {status.label}
        </span>
      )}
    </li>
  );
}
