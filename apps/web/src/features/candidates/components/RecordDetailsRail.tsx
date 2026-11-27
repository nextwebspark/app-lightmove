import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { DetailBlock } from "../../../components/ui/DetailList";
import { cn } from "../../../lib/cn";
import { formatInstantDate } from "../../../lib/format";
import { toBrowsableUrl } from "../../../lib/url";
import { ContactChannels } from "../../contactlookup/components/ContactPanel";
import type { PhoneReach } from "../../contactlookup/components/PhoneActions";
import type { Candidate, LastCall, PersonRecord } from "../api/types";
import { CANDIDATE_SOURCE_STYLES } from "../lib/candidateVocabulary";
import { pipelineSwatchClass } from "../lib/pipelineStatus";
import { PersonTagsSection } from "./PersonTagsSection";

const BLOCK = "gap-1.5 pt-3.5";

/**
 * The record full page's Details rail (`PositionExecutiveRail.dc.html`): what holds on every tab, so the header keeps
 * who they are and the tabs keep the work: the status first, the actions held in a fixed foot. Drawn from 1280px wide;
 * narrower, the header and footer carry it.
 */
export function RecordDetailsRail({
  projectId,
  candidate,
  canWrite,
  person,
  status,
  actions,
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
  /** The header's status and stage, moved here. */
  status: ReactNode;
  /** The header's actions, moved to the foot. */
  actions?: ReactNode;
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
      className="hidden w-80 flex-none flex-col border-s border-u-border bg-u-bg xl:flex"
    >
      <div className="flex min-h-0 flex-1 flex-col gap-3.5 overflow-y-auto px-5 pb-4 pt-1 [&>section:first-child]:border-t-0">
        <DetailBlock title="Status" className={BLOCK}>
          <div className="flex min-w-0 flex-col items-start gap-1.5">{status}</div>
        </DetailBlock>

        {nextAction && (
          <DetailBlock title="Next action" className={BLOCK}>
            {nextAction}
          </DetailBlock>
        )}

        <DetailBlock
          title="Contact"
          className={BLOCK}
          aside={
            onEditContacts && (
              <button
                type="button"
                aria-label="Edit contact details"
                title="Edit contact details"
                onClick={onEditContacts}
                className="-my-1 flex rounded-md p-1 text-u-text3 transition hover:bg-u-raised hover:text-u-text"
              >
                <Icon d={ICONS.pencil} size={13} />
              </button>
            )
          }
        >
          <ContactChannels
            contacts={candidate.contacts}
            reach={reach}
            lastCall={lastCall}
            onAddMissing={onEditContacts}
            dense
          />
        </DetailBlock>

        {canWrite && person && (
          <DetailBlock title="Tags" className={BLOCK}>
            <PersonTagsSection person={person} />
          </DetailBlock>
        )}

        {/* Staff only: whether the client sees them, and the mandates a client seat may not know of. */}
        {canWrite && (
          <DetailBlock title="Process" className={BLOCK}>
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

        <section aria-label="Record" className="border-t border-u-border pt-3 font-mono text-meta text-u-text3">
          {[
            `Added ${formatInstantDate(candidate.addedAt)}${canWrite && person?.addedByName ? ` by ${person.addedByName}` : ""}`,
            CANDIDATE_SOURCE_STYLES[candidate.source]?.label,
            candidate.enrichedAt && `Researched ${formatInstantDate(candidate.enrichedAt)}`,
          ]
            .filter(Boolean)
            .join(" · ")}
          {capturedFrom && (
            <>
              {" · "}
              <a
                href={capturedFrom}
                target="_blank"
                rel="noreferrer noopener"
                className="break-all hover:text-u-text hover:underline"
              >
                {new URL(capturedFrom).hostname}
              </a>
            </>
          )}
        </section>
      </div>
      {actions && <div className="flex-none border-t border-u-border bg-u-bg px-5 py-3">{actions}</div>}
    </aside>
  );
}
