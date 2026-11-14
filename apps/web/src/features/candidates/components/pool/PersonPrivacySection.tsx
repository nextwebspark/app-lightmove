import { useMutation } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, Select, useToast } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { formatInstantDate, formatInstantDateTime } from "../../../../lib/datetime";
import { messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { LawfulBasis, PersonRecord } from "../../api/types";
import { LAWFUL_BASES, lawfulBasisLabel, noticeChannelLabel } from "../../lib/candidatePrivacy";

const DEFAULT = "default";

/**
 * The drawer's Privacy block (#923, `Candidates.dc.html`): the lawful basis the firm records for holding this person,
 * and whether they were told. Staff-only, as the whole drawer is; a client seat never reads it.
 */
export function PersonPrivacySection({
  person,
  onChanged,
  onSendNotice,
  onMarkNotified,
}: {
  person: PersonRecord;
  onChanged: (record: PersonRecord) => void;
  onSendNotice: () => void;
  onMarkNotified: () => void;
}) {
  const toast = useToast();
  const privacy = person.privacy;
  const recording = useMutation({
    mutationFn: (basis: LawfulBasis | null) => poolApi.setLawfulBasis(person.personId, basis),
    onSuccess: (record) => {
      onChanged(record);
      toast.success("Lawful basis recorded");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const notified = !!privacy.notifiedAt;

  return (
    <section aria-label="Privacy" className="mb-4 flex gap-3 rounded-u-card border border-u-border bg-u-raised p-3">
      <span className="grid size-7 flex-none place-items-center rounded-full bg-u-surface text-u-text3">
        <Icon d={ICONS.lock} size={14} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="type-label mb-1.5 text-u-text3">Privacy</div>
        <label className="flex flex-wrap items-center gap-2">
          <span className="text-note text-u-text2">Lawful basis</span>
          <Select
            aria-label="Lawful basis"
            density="compact"
            className="w-auto"
            disabled={recording.isPending}
            value={privacy.basisIsDefault ? DEFAULT : privacy.lawfulBasis}
            onChange={(event) =>
              recording.mutate(event.target.value === DEFAULT ? null : (event.target.value as LawfulBasis))
            }
          >
            <option value={DEFAULT}>
              {privacy.basisIsDefault ? `${lawfulBasisLabel(privacy.lawfulBasis)} (workspace default)` : "Workspace default"}
            </option>
            {LAWFUL_BASES.map((basis) => (
              <option key={basis.value} value={basis.value}>
                {basis.label}
              </option>
            ))}
          </Select>
        </label>
        {!privacy.basisIsDefault && privacy.basisSetByName && (
          <div className="mt-1 font-mono text-meta text-u-text3">
            Recorded by {privacy.basisSetByName}
            {privacy.basisSetAt && ` · ${formatInstantDateTime(privacy.basisSetAt)}`}
          </div>
        )}
        <div className="mt-2.5 flex flex-wrap items-center gap-2">
          <span
            className={cn(
              "type-tag rounded px-1.5 py-0.5",
              notified ? "bg-u-direct-tint text-u-direct" : "bg-u-signal-tint text-u-signal",
            )}
          >
            {notified ? "Notified" : "Not notified"}
          </span>
          <span className="font-mono text-meta text-u-text3">
            {notified && privacy.noticeChannel
              ? `${formatInstantDate(privacy.notifiedAt)} · ${noticeChannelLabel(privacy.noticeChannel)}`
              : `On file since ${formatInstantDate(person.addedAt)}`}
          </span>
        </div>
        {!notified && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            <Button variant="secondary" size="xs" onClick={onSendNotice}>
              <Icon d={ICONS.mail} size={12} />
              Send privacy notice
            </Button>
            <Button variant="secondary" size="xs" onClick={onMarkNotified}>
              Mark as notified
            </Button>
          </div>
        )}
      </div>
    </section>
  );
}
