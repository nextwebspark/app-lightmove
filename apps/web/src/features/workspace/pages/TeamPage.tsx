import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar, Button } from "../../../components/ui";
import { titleCase } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import * as projectsApi from "../../projects/api/projectsApi";
import { isActive } from "../../projects/lib/filtering";
import * as workspaceApi from "../api/workspaceApi";
import { InviteModal } from "../components/InviteModal";

/** The roster as colleagues see it: who's here, their role, and how many mandates they carry. */
export function TeamPage() {
  const { user } = useAuth();
  const isAdmin = user?.workspace?.role === "ADMIN";
  const [inviteOpen, setInviteOpen] = useState(false);

  const { data: members = [] } = useQuery({
    queryKey: workspaceApi.MEMBERS_KEY,
    queryFn: workspaceApi.members,
  });
  const { data: projects = [] } = useQuery({
    queryKey: projectsApi.PROJECTS_KEY,
    queryFn: projectsApi.projects,
  });

  const activeCount = (memberId: string) =>
    projects.filter((p) => isActive(p) && p.team.some((seat) => seat.memberId === memberId)).length;

  return (
    <>
      <PageHeader
        title="Team"
        subtitle={`${members.length} ${members.length === 1 ? "member" : "members"} · roles apply per project`}
        action={
          isAdmin && (
            <Button
              variant="secondary"
              className="!px-3.5 !py-[7px] !text-[13px]"
              onClick={() => setInviteOpen(true)}
            >
              <Icon d={ICONS.plus} size={15} />
              Invite
            </Button>
          )
        }
      />

      {members.map((member) => {
        const count = activeCount(member.memberId);
        return (
          <div key={member.memberId} className="flex items-center gap-2.5 rounded-[7px] p-2 hover:bg-panel2">
            <Avatar id={member.memberId} name={member.fullName} />
            <div>
              <div className="text-[13px]">{member.fullName}</div>
              <div className="font-mono text-[11px] text-text3">{titleCase(member.role)}</div>
            </div>
            <div className="ml-auto font-mono text-[11px] text-text3">
              {count} active {count === 1 ? "project" : "projects"}
            </div>
          </div>
        );
      })}

      {inviteOpen && <InviteModal open onClose={() => setInviteOpen(false)} />}
    </>
  );
}
