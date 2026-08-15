import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Avatar, Button, Modal, Select, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import * as projectsApi from "../api/projectsApi";
import { STAFF_ROLES, type Project, type StaffRole } from "../api/types";
import { ROLE_STYLES } from "./ProjectRoleChips";

/**
 * The "Add team member" modal (Project.dc.html): the firm directory, minus whoever already staffs this
 * mandate, each row carrying the role they would join as. Adding is per row and leaves the list open —
 * staffing a search is usually more than one person.
 *
 * The directory is the staff roster, so a pure client never appears in it. A dual-role colleague who
 * also represents the client does, and seating them keeps their CLIENT seat — the server preserves it.
 */
export function AddTeamMemberModal({ project, onClose }: { project: Project; onClose: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [roleFor, setRoleFor] = useState<Record<string, StaffRole>>({});

  // The roster is staff-only server-side, which is exactly who may be seated; a lead reading it is
  // authorised by @workspaceAuth.staff, not by anything project-scoped.
  const { data: directory = [] } = useQuery({
    queryKey: workspaceApi.MEMBERS_KEY,
    queryFn: workspaceApi.members,
  });

  const add = useMutation({
    mutationFn: (memberId: string) =>
      projectsApi.putProjectMember(project.id, memberId, roleFor[memberId] ?? "RESEARCHER"),
    onSuccess: (_project, memberId) => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      const person = directory.find((candidate) => candidate.memberId === memberId);
      toast(`${person?.fullName ?? "Member"} added as ${ROLE_STYLES[roleFor[memberId] ?? "RESEARCHER"].label}`);
    },
    onError: (error) => toast(messageFor(error)),
  });

  // Only a staff seat means "already on the team" — a CLIENT-only seat is a client contact.
  const staffed = new Set(
    project.team
      .filter((member) => member.projectRoles.some((role) => role !== "CLIENT"))
      .map((member) => member.memberId),
  );
  const addable = directory.filter((person) => !staffed.has(person.memberId));

  return (
    <Modal open onClose={onClose} title="Add team member" className="w-[480px]">
      <p className="-mt-3 mb-4 font-mono text-xs text-text3">
        Pick from the firm directory and set the role they join with
      </p>

      {addable.length > 0 ? (
        <div className="flex max-h-[300px] flex-col gap-[7px] overflow-y-auto">
          {addable.map((person) => {
            const role = roleFor[person.memberId] ?? "RESEARCHER";
            return (
              <div
                key={person.memberId}
                className="flex items-center gap-2.5 rounded-[9px] border border-line px-[11px] py-[9px]"
              >
                <Avatar id={person.memberId} name={person.fullName} src={person.avatarUrl} size="lg" />
                <div className="min-w-0 flex-1">
                  <div className="text-[13px] font-medium">{person.fullName}</div>
                  <div className="truncate font-mono text-[11px] text-text3">
                    Joins as {ROLE_STYLES[role].label}
                  </div>
                </div>
                <Select
                  aria-label={`Role for ${person.fullName}`}
                  value={role}
                  onChange={(event) =>
                    setRoleFor((current) => ({
                      ...current,
                      [person.memberId]: event.target.value as StaffRole,
                    }))
                  }
                  className="w-auto px-2 py-1.5 text-xs"
                >
                  {STAFF_ROLES.map((candidate) => (
                    <option key={candidate} value={candidate}>
                      {ROLE_STYLES[candidate].label}
                    </option>
                  ))}
                </Select>
                <Button
                  className="px-3 py-1.5 text-xs"
                  // `isPending` alone is shared by every row — `variables` is the id in flight, so one
                  // click spins one button.
                  loading={add.isPending && add.variables === person.memberId}
                  disabled={add.isPending}
                  onClick={() => add.mutate(person.memberId)}
                >
                  Add
                </Button>
              </div>
            );
          })}
        </div>
      ) : (
        <p className="px-4 py-[26px] text-center font-mono text-[12.5px] text-text3">
          Everyone in the directory is already on this project.
        </p>
      )}

      <div className="mt-[18px] flex justify-end">
        <Button variant="secondary" onClick={onClose}>
          Done
        </Button>
      </div>
    </Modal>
  );
}
