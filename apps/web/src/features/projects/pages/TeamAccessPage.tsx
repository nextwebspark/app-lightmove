import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { useToast } from "../../../components/ui";
import { Avatar } from "../../../components/ui";
import { initials } from "../../../lib/format";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient } from "../../auth/roles";
import * as clientsApi from "../../clients/api/clientsApi";
import * as projectsApi from "../api/projectsApi";
import type { AttachedRepresentative } from "../api/types";
import { AddClientContactModal } from "../components/AddClientContactModal";

/**
 * The Team & access tab's Client section (Project.dc.html): the linked client organisation and the
 * client-side contacts who may read this mandate. Contacts render from the project itself — a pure
 * client sees their own colleagues here without ever touching the staff-only client registry, which
 * is why the registry query is gated off for them. The staff team table is a later session.
 */
export function TeamAccessPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [addOpen, setAddOpen] = useState(false);

  const clientOnly = isPureClient(user?.workspace?.roles ?? []);
  const seat = project.team.find((member) => member.userId === user?.id);
  // Mirrors the server's PROJECT_EDIT gate: project admins and leads, with the workspace-admin bypass.
  const canManage =
    (user?.workspace?.roles.includes("ADMIN") ?? false) ||
    (seat?.projectRoles.some((role) => role === "ADMIN" || role === "LEAD") ?? false);

  // The registry supplies the sector line and the modal's roster. Staff-only — a pure client's page
  // renders entirely from the project, and firing this for them would just 403.
  const { data: client } = useQuery({
    queryKey: clientsApi.clientKey(project.clientId),
    queryFn: () => clientsApi.client(project.clientId),
    // Waits for the session: until `me` resolves the roles are unknown, and firing early would hit
    // the staff-only registry as a pure client — a guaranteed 403.
    enabled: Boolean(user) && !clientOnly,
  });

  const detach = useMutation({
    mutationFn: (representativeId: string) =>
      projectsApi.detachRepresentative(project.id, representativeId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      toast("Contact removed from this mandate");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const contacts = project.representatives;
  const contactCount = `${contacts.length} contact${contacts.length === 1 ? "" : "s"}`;

  return (
    <div className="animate-fade-up">
      <div className="mb-3.5">
        <h1 className="text-[19px] font-semibold leading-tight">Team &amp; access</h1>
        <p className="mt-1 font-mono text-xs text-text3">
          Who works this mandate and what they can do
        </p>
      </div>

      <div className="mb-3.5 mt-8 flex items-start gap-4">
        <div>
          <h2 className="text-base font-semibold leading-tight">Client</h2>
          <p className="mt-1 font-mono text-xs text-text3">
            The client organisation and the people we report to on their side
          </p>
        </div>
      </div>

      <div className="overflow-hidden rounded-[11px] border border-line">
        <div className="flex items-center gap-3 border-b border-line bg-panel2 px-4 py-[15px]">
          <span className="grid size-[38px] flex-none place-items-center rounded-[9px] bg-sky-dim font-mono text-[13px] font-bold text-sky">
            {initials(project.clientName)}
          </span>
          <div className="min-w-0 flex-1">
            <div className="text-sm font-semibold">{project.clientName}</div>
            <div className="mt-0.5 font-mono text-[11.5px] text-text3">
              {[client?.sector, contactCount].filter(Boolean).join(" · ")}
            </div>
          </div>
          {canManage && (
            <button
              type="button"
              onClick={() => setAddOpen(true)}
              className="inline-flex items-center gap-1.5 rounded-lg border border-amber-btn bg-amber-btn px-3 py-1.5 text-[12.5px] font-semibold text-on-amber hover:brightness-105"
            >
              <PlusIcon />
              Add contact
            </button>
          )}
        </div>

        {contacts.map((contact) => (
          <ContactRow
            key={contact.representativeId}
            contact={contact}
            canRemove={canManage}
            removing={detach.isPending}
            onRemove={() => detach.mutate(contact.representativeId)}
          />
        ))}

        <div className="px-4 py-[11px] font-mono text-[11px] text-text3">
          Invited contacts join automatically once they accept — no action needed on your side.
        </div>
      </div>

      {addOpen && (
        <AddClientContactModal
          project={project}
          roster={client?.representatives ?? []}
          onClose={() => setAddOpen(false)}
        />
      )}
    </div>
  );
}

const CONTACT_BADGE: Record<AttachedRepresentative["status"], { label: string; className: string }> = {
  ACTIVE: { label: "Active", className: "text-green bg-green-dim" },
  INVITED: { label: "Invite sent", className: "text-amber bg-amber-dim" },
};

function ContactRow({
  contact,
  canRemove,
  removing,
  onRemove,
}: {
  contact: AttachedRepresentative;
  canRemove: boolean;
  removing: boolean;
  onRemove: () => void;
}) {
  const badge = CONTACT_BADGE[contact.status];
  return (
    <div className="flex items-center gap-3 border-b border-line-soft px-4 py-[13px]">
      <Avatar id={contact.representativeId} name={contact.fullName} size="lg" />
      <div className="min-w-0 flex-1">
        <div className="text-[13.5px] font-medium">{contact.fullName}</div>
        <div className="mt-0.5 truncate font-mono text-[11.5px] text-text3">
          {[contact.position, contact.email].filter(Boolean).join(" · ")}
        </div>
      </div>
      <span
        className={`whitespace-nowrap rounded-full px-2 py-[3px] font-mono text-[9.5px] font-semibold uppercase tracking-[0.05em] ${badge.className}`}
      >
        {badge.label}
      </span>
      {canRemove && (
        <button
          type="button"
          title="Remove contact"
          aria-label={`Remove ${contact.fullName}`}
          disabled={removing}
          onClick={onRemove}
          className="rounded-md p-1.5 text-text3 hover:bg-red-dim hover:text-red disabled:opacity-50"
        >
          <TrashIcon />
        </button>
      )}
    </div>
  );
}

function PlusIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true">
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m2 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M10 11v6M14 11v6" />
    </svg>
  );
}
