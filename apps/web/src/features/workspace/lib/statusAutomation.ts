import { useQuery } from "@tanstack/react-query";
import * as pipelineApi from "../api/pipelineApi";
import type { PipelineCatalog, PipelineStatus, StatusAutomation, StatusAutomationTrigger } from "../api/types";

export interface AutomationTriggerCopy {
  trigger: StatusAutomationTrigger;
  /** The row's sentence on the automation card. */
  when: string;
  /** The chip a status row wears when the automation lands there. */
  short: string;
}

export const AUTOMATION_TRIGGERS: readonly AutomationTriggerCopy[] = [
  { trigger: "emailSent", when: "An outreach email is sent", short: "Email sent" },
  { trigger: "replied", when: "They reply to outreach", short: "Reply" },
  { trigger: "callBooked", when: "A call is booked", short: "Call booked" },
];

export function triggerCopyOf(trigger: StatusAutomationTrigger): AutomationTriggerCopy {
  return AUTOMATION_TRIGGERS.find((copy) => copy.trigger === trigger) ?? AUTOMATION_TRIGGERS[0];
}

/** The workspace's automations; staff only, so callers a client seat reaches pass `enabled: false`. */
export function useStatusAutomations(enabled = true) {
  return useQuery({
    queryKey: pipelineApi.STATUS_AUTOMATIONS_KEY,
    queryFn: ({ signal }) => pipelineApi.statusAutomations(signal),
    staleTime: 60_000,
    enabled,
  });
}

/** The switched-on automations that move people to `statusId`. */
export function automationsInto(automations: readonly StatusAutomation[], statusId: string): StatusAutomation[] {
  return automations.filter((automation) => automation.enabled && automation.statusId === statusId);
}

export function statusById(catalog: PipelineCatalog, statusId: string | null): PipelineStatus | null {
  if (!statusId) return null;
  for (const stage of catalog.stages) {
    const status = stage.statuses.find((candidate) => candidate.id === statusId);
    if (status) return status;
  }
  return null;
}

/**
 * Whether an automation moving to `targetId` would move someone at `fromKey`: forward only by the pipeline's
 * order, and never from a status out of process — the server's own rule, mirrored for what a toast promises.
 */
export function wouldMoveForward(catalog: PipelineCatalog, fromKey: string, targetId: string): boolean {
  const rank = (match: (status: PipelineStatus) => boolean) => {
    for (const stage of catalog.stages) {
      const status = stage.statuses.find(match);
      if (status) return { out: status.outOfProcess, order: [stage.position, status.position] as const };
    }
    return null;
  };
  const from = rank((status) => status.key === fromKey);
  const to = rank((status) => status.id === targetId);
  if (!from || !to || from.out || to.out) return false;
  return from.order[0] < to.order[0] || (from.order[0] === to.order[0] && from.order[1] < to.order[1]);
}
