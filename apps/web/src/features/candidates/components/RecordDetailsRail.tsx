import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { DetailBlock, DetailRow } from "../../../components/ui/DetailList";
import { cn } from "../../../lib/cn";
import { formatInstantDate } from "../../../lib/format";
import { toBrowsableUrl } from "../../../lib/url";
import { ContactChannels } from "../../contactlookup/components/ContactPanel";
import type { PhoneReach } from "../../contactlookup/components/PhoneActions";
import type { Candidate, LastCall, PersonRecord } from "../api/types";
import { CANDIDATE_SOURCE_STYLES } from "../lib/candidateVocabulary";
import { pipelineSwatchClass } from "../lib/pipelineStatus";
import { PersonTagsSection } from "./PersonTagsSection";

/**
 * The record full page's Details rail (`PositionExecutiveRail.dc.html`): what holds on every tab, so the header keeps
 * who they are and the tabs keep the work. Drawn from 1280px wide; narrower, the header and footer carry it.
 */
export function RecordDetailsRail({
  projectId,
  candidate,
  canWrite,
  person,
  nextAction,
  clientVisibility,
  reach,
  lastCall,
  onEditContacts,
}: {
  projectId: string;
  candidate: Candidate;
  canWrite: boolean;
  /** The workspace's person, read for staff only: tags, who added them, their other positions. */
  person: PersonRecord | undefined;
  /** The footer's next action bar, moved here. */
  nextAction?: ReactNode;
  /** The Client visible switch, which the host draws. */
  clientVisibility?: ReactNode;
  reach?: PhoneReach;
  lastCall?: LastCall | null;
  onEditContacts?: () => void;
}) {
  const capturedFrom = toBrowsableUrl(candidate.sourceUrl);
  const otherPositions = (person?.positions ?? []).filter((position) => position.projectId !== projectId);
  return (
    <aside
      aria-label="Details"
      className="hidden w-80 flex-none overflow-y-auto border-s border-u-border bg-u-bg xl:block"
    >
      <div className="flex flex-col gap-5 px-5 pb-6 pt-1 [&>section:first-child]:border-t-0">
        {nextAction && <DetailBlock title="Next action">{nextAction}</DetailBlock>}

        <DetailBlock
          title="Contact"
          aside={
            onEditContacts && (
              <button
                type="button"
                aria-label="Edit contact details"
                title="Edit contact details"
                onClick={onEditContacts}
                className="flex rounded-md p-1.5 text-u-text3 transition hover:bg-u-raised hover:text-u-text"
              >
                <Icon d={ICONS.pencil} size={13} />
              </button>
            )
          }
        >
          <ContactChannels contacts={candidate.contacts} reach={reach} lastCall={lastCall} />
        </DetailBlock>

        {canWrite && person && (
          <DetailBlock title="Tags">
            <PersonTagsSection person={person} />
          </DetailBlock>
        )}

        {/* Staff only: whether the client sees them, and the mandates a client seat may not know of. */}
        {canWrite && (
          <DetailBlock title="Process">
            {clientVisibility}
            {otherPositions.length > 0 && (
              <div className="mt-2">
                <div className="type-micro-label text-u-text3">Also on</div>
                <ul className="mt-1.5 flex flex-col gap-2">
                  {otherPositions.map((position) => (
                    <li key={position.candidateId} className="min-w-0">
                      <div className="flex min-w-0 items-center gap-1.5">
                        <span
                          aria-hidden="true"
                          className={cn("size-1.5 flex-none rounded-full", pipelineSwatchClass(position.statusColour))}
                        />
                        <Link
                          to={`/projects/${position.projectId}/companies`}
                          className="min-w-0 truncate text-note font-medium text-u-text hover:underline"
                        >
                          {position.positionTitle ?? "Untitled position"}
                        </Link>
                        {position.statusLabel && (
                          <span className="ms-auto flex-none text-meta text-u-text2">{position.statusLabel}</span>
                        )}
                      </div>
                      <div className="ms-3 font-mono text-meta text-u-text3">
                        {[position.addedByName, `since ${formatInstantDate(position.addedAt)}`].filter(Boolean).join(" · ")}
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </DetailBlock>
        )}

        <DetailBlock title="Record">
          {canWrite && person?.addedByName && <DetailRow label="Added by">{person.addedByName}</DetailRow>}
          <DetailRow label="Added" mono>
            {formatInstantDate(candidate.addedAt)}
          </DetailRow>
          <DetailRow label="Source">{CANDIDATE_SOURCE_STYLES[candidate.source]?.label ?? "—"}</DetailRow>
          {candidate.enrichedAt && (
            <DetailRow label="Researched" mono>
              {formatInstantDate(candidate.enrichedAt)}
            </DetailRow>
          )}
          {capturedFrom && (
            <DetailRow label="Captured from">
              <a
                href={capturedFrom}
                target="_blank"
                rel="noreferrer noopener"
                className="break-all hover:text-u-text hover:underline"
              >
                {new URL(capturedFrom).hostname}
              </a>
            </DetailRow>
          )}
        </DetailBlock>
      </div>
    </aside>
  );
}
