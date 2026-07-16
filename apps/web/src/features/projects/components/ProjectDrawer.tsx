import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Avatar, Drawer, LeadBadge, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { formatDate, titleCase } from "../../../lib/format";
import type { Member } from "../../workspace/api/types";
import * as projectsApi from "../api/projectsApi";
import type { Project } from "../api/types";
import { STAGE_ORDER } from "../lib/filtering";
import { stageLabel } from "../../../components/ui";

/**
 * The right slide-over: pipeline stats, display-only stage gates, and the team toggles — the one
 * place a member is put on or taken off a mandate. The lead's toggle refuses with a toast; the lead
 * is replaced via project settings, never removed.
 */
export function ProjectDrawer({
  project,
  members,
  onClose,
}: {
  project: Project | null;
  members: Member[];
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const toggle = useMutation({
    mutationFn: ({ memberId, on }: { memberId: string; on: boolean }) =>
      on
        ? projectsApi.addProjectMember(project!.id, memberId)
        : projectsApi.removeProjectMember(project!.id, memberId),
    onSuccess: (_, { on }) => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      toast(on ? "Added to project" : "Removed from project");
    },
    onError: (error) => toast(messageFor(error)),
  });

  if (!project) return null;

  const seatOf = (memberId: string) => project.team.find((seat) => seat.memberId === memberId);
  const currentStage = STAGE_ORDER.indexOf(project.stage);
  const gates = STAGE_ORDER.filter((stage) => stage !== "CLOSED");

  return (
    <Drawer open onClose={onClose}>
      <div className="relative border-b border-line-soft px-5 pb-3.5 pt-[18px]">
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="absolute right-3.5 top-3.5 rounded-md p-1.5 text-text3 hover:bg-panel2 hover:text-text"
        >
          ✕
        </button>
        <div className="font-mono text-[11px] font-medium uppercase tracking-[0.08em] text-text3">
          {project.clientName}
        </div>
        <div className="mt-1 text-[17px] font-semibold">{project.positionTitle}</div>
      </div>

      <div className="flex-1 overflow-y-auto px-5 py-[18px]">
        <SectionLabel>Pipeline</SectionLabel>
        <div className="flex gap-2.5">
          <StatTile value={String(project.companies)} label="Companies" />
          <StatTile value={String(project.candidates)} label="Candidates" />
          <StatTile value={formatDate(project.targetDate).slice(0, 6)} label="Target" />
        </div>

        <SectionLabel className="mt-[18px]">Stage gates</SectionLabel>
        {gates.map((stage, index) => {
          const done = index < currentStage;
          const now = index === currentStage;
          return (
            <div
              key={stage}
              className={`flex items-center gap-2.5 py-[7px] font-mono text-[12.5px] ${
                now ? "font-semibold text-amber" : done ? "text-text2" : "text-text3"
              }`}
            >
              <span
                className={`grid size-3.5 flex-none place-items-center rounded-full border-[1.5px] ${
                  done ? "border-green bg-green-dim" : now ? "border-amber" : "border-line"
                }`}
              >
                <span className={`size-1.5 rounded-full ${done ? "bg-green" : now ? "bg-amber" : ""}`} />
              </span>
              {stageLabel(stage)}
            </div>
          );
        })}

        <SectionLabel className="mt-[18px]">Team</SectionLabel>
        {members.map((member) => {
          const seat = seatOf(member.memberId);
          const isLead = seat?.projectRole === "LEAD";
          const on = Boolean(seat);
          return (
            <div key={member.memberId} className="flex items-center gap-2.5 rounded-[7px] px-2 py-[7px] hover:bg-panel2">
              <Avatar id={member.memberId} name={member.fullName} />
              <div>
                <div className="text-[13px]">{member.fullName}</div>
                <div className="font-mono text-[11px] text-text3">{titleCase(member.role)}</div>
              </div>
              <div className="ml-auto flex items-center gap-2">
                {isLead && <LeadBadge />}
                <button
                  type="button"
                  role="switch"
                  aria-checked={on}
                  aria-label={`Toggle ${member.fullName}`}
                  disabled={toggle.isPending}
                  onClick={() => {
                    if (isLead) {
                      toast("Lead cannot be removed");
                      return;
                    }
                    toggle.mutate({ memberId: member.memberId, on: !on });
                  }}
                  className={`relative h-[18px] w-8 flex-none rounded-full transition ${on ? "bg-amber-btn" : "bg-line"}`}
                >
                  <span
                    className={`absolute left-0.5 top-0.5 size-3.5 rounded-full transition-transform ${
                      on ? "translate-x-3.5 bg-on-amber" : "bg-text3"
                    }`}
                  />
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </Drawer>
  );
}

function SectionLabel({ children, className = "" }: { children: string; className?: string }) {
  return (
    <div className={`mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3 ${className}`}>
      {children}
    </div>
  );
}

function StatTile({ value, label }: { value: string; label: string }) {
  return (
    <div className="flex-1 rounded-lg border border-line-soft bg-panel2 px-3 py-2.5">
      <b className="block font-mono text-[17px] font-semibold text-text">{value}</b>
      <span className="font-mono text-[10.5px] uppercase tracking-[0.06em] text-text3">{label}</span>
    </div>
  );
}
