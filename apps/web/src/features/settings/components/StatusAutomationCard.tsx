import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Select, Toggle, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { liveStages, liveStatuses } from "../../candidates/lib/pipelineStatus";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type {
  PipelineCatalog,
  StatusAutomation,
  StatusAutomationChange,
  StatusAutomationTrigger,
} from "../../workspace/api/types";
import { AUTOMATION_TRIGGERS, statusById, useStatusAutomations } from "../../workspace/lib/statusAutomation";

export const AUTOMATION_CARD_ID = "pipeline-automations";

/**
 * Settings → Pipeline stages' "Move candidates automatically": for each outreach event, the status it moves
 * people forward to and whether it is on. Every staff member reads it; only an admin changes it. A rule whose
 * status was retired is drawn paused, asking for another.
 */
export function StatusAutomationCard({ catalog, isAdmin }: { catalog: PipelineCatalog; isAdmin: boolean }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const automations = useStatusAutomations();

  const saving = useMutation({
    mutationFn: ({ trigger, change }: { trigger: StatusAutomationTrigger; change: StatusAutomationChange }) =>
      pipelineApi.updateStatusAutomation(trigger, change),
    onSuccess: (next) => {
      queryClient.setQueryData(pipelineApi.STATUS_AUTOMATIONS_KEY, next);
      toast("Saved for the whole workspace");
    },
    onError: (error) => {
      toast.error(messageFor(error));
      void queryClient.invalidateQueries({ queryKey: pipelineApi.STATUS_AUTOMATIONS_KEY });
    },
  });

  const save = (trigger: StatusAutomationTrigger, change: StatusAutomationChange) =>
    saving.mutate({ trigger, change });

  return (
    <section
      id={AUTOMATION_CARD_ID}
      aria-label="Move candidates automatically"
      className="mt-6 rounded-u-card border border-u-border bg-u-raised p-5"
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-body font-semibold text-u-text">Move candidates automatically</span>
        {!isAdmin && (
          <span className="rounded bg-u-surface px-1.5 py-px font-mono text-meta font-medium text-u-text3">
            Admin
          </span>
        )}
      </div>
      <p className="mt-0.5 max-w-[620px] font-mono text-meta text-u-text3">
        When outreach moves on, Uncava moves the candidate to the status you choose, on every position. Only ever
        forward, and never from a status out of process: someone a consultant already moved further, or ruled
        out, keeps their place.
      </p>
      {automations.isError ? (
        <p className="mt-3 text-note text-u-text3">{messageFor(automations.error)}</p>
      ) : (
        <ul className="mt-2 divide-y divide-u-border">
          {AUTOMATION_TRIGGERS.map((copy) => {
            const automation = automations.data?.find((row) => row.trigger === copy.trigger);
            return (
              <AutomationRow
                key={copy.trigger}
                when={copy.when}
                trigger={copy.trigger}
                automation={automation}
                catalog={catalog}
                editable={isAdmin && !!automation && !saving.isPending}
                onSave={save}
              />
            );
          })}
        </ul>
      )}
    </section>
  );
}

function AutomationRow({
  when,
  trigger,
  automation,
  catalog,
  editable,
  onSave,
}: {
  when: string;
  trigger: StatusAutomationTrigger;
  automation: StatusAutomation | undefined;
  catalog: PipelineCatalog;
  editable: boolean;
  onSave: (trigger: StatusAutomationTrigger, change: StatusAutomationChange) => void;
}) {
  const target = statusById(catalog, automation?.statusId ?? null);
  const enabled = automation?.enabled ?? false;
  const broken = automation?.broken ?? false;
  const liveTargetId = target && !target.retired && !target.outOfProcess ? target.id : "";
  const offeredStages = liveStages(catalog);

  return (
    <li className="flex flex-col gap-1.5 py-3">
      <div className="flex flex-wrap items-center gap-3">
        <span className="min-w-0 flex-1 text-body text-u-text md:w-56 md:flex-none">{when}</span>
        <Icon d={ICONS.arrowRight} size={13} className="hidden flex-none text-u-text3 md:block" />
        <Select
          density="compact"
          aria-label={`${when}: move to`}
          value={liveTargetId}
          disabled={!editable}
          invalid={broken}
          className="w-full md:w-64"
          onChange={(event) =>
            onSave(trigger, event.target.value ? { enabled, statusId: event.target.value } : { enabled: false, statusId: null })
          }
        >
          <option value="">{broken ? "Choose another status" : "Choose a status"}</option>
          {offeredStages.map((stage) => (
            <optgroup key={stage.id} label={stage.label}>
              {liveStatuses(stage)
                .filter((status) => !status.outOfProcess)
                .map((status) => (
                  <option key={status.id} value={status.id}>
                    {status.label}
                  </option>
                ))}
            </optgroup>
          ))}
        </Select>
        {/* A paused rule stays switchable off; switching one on needs a live status. */}
        <Toggle
          checked={enabled}
          disabled={!editable || (!enabled && !liveTargetId)}
          label={`${when}: move automatically`}
          onChange={(on) => onSave(trigger, { enabled: on, statusId: automation?.statusId ?? null })}
        />
      </div>
      {broken && (
        <p role="status" className="flex items-center gap-1.5 text-note text-u-signal">
          <Icon d={ICONS.warning} size={12} />
          {target?.label ?? "Its status"} was{" "}
          {target?.outOfProcess && !target.retired ? "taken out of process" : "retired"}. This automation is paused
          until you choose another status or switch it off.
        </p>
      )}
    </li>
  );
}
