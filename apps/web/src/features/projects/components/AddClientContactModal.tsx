import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Avatar, Button, Field, Input, Modal, useToast } from "../../../components/ui";
import { isValidEmail } from "../../../lib/email";
import { messageFor } from "../../../lib/errorCodes";
import * as clientsApi from "../../clients/api/clientsApi";
import type { ClientRepresentative } from "../../clients/api/types";
import * as projectsApi from "../api/projectsApi";
import type { Project } from "../api/types";

type InviteMode = "existing" | "new";

/**
 * The "Add client contact" modal (Project.dc.html): pick one of the client's people, or invite a new
 * address — which creates the representative on the client and attaches them to this mandate in one
 * go. Attaching an INVITED person parks the seat server-side; the footer copy on the page explains
 * they join automatically on accept.
 */
export function AddClientContactModal({
  project,
  roster,
  onClose,
}: {
  project: Project;
  /** The client's representatives from the registry — the "Existing person" tab's list. */
  roster: ClientRepresentative[];
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [mode, setMode] = useState<InviteMode>("existing");

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
    void queryClient.invalidateQueries({ queryKey: clientsApi.clientKey(project.clientId) });
    void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
  };

  const attach = useMutation({
    mutationFn: (representativeId: string) =>
      projectsApi.attachRepresentative(project.id, representativeId),
    onSuccess: () => {
      invalidate();
      toast("Contact added to this mandate");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const attachedIds = new Set(project.representatives.map((rep) => rep.representativeId));

  return (
    <Modal open onClose={onClose} title="Add client contact" className="w-[490px]">
      <p className="-mt-3 mb-4 font-mono text-xs text-text3">
        Invite someone from {project.clientName} to this project
      </p>

      <div className="mb-4 flex gap-1.5 rounded-[9px] bg-panel2 p-1">
        <TabButton active={mode === "existing"} onClick={() => setMode("existing")}>
          Existing person
        </TabButton>
        <TabButton active={mode === "new"} onClick={() => setMode("new")}>
          Invite by email
        </TabButton>
      </div>

      {mode === "existing" ? (
        <>
          {roster.length > 0 ? (
            <>
              <div className="flex max-h-[260px] flex-col gap-[7px] overflow-y-auto">
                {roster.map((person) => (
                  <div
                    key={person.id}
                    className="flex items-center gap-2.5 rounded-[9px] border border-line px-[11px] py-[9px]"
                  >
                    <Avatar id={person.id} name={person.fullName} size="lg" />
                    <div className="min-w-0 flex-1">
                      <div className="text-[13px] font-medium">{person.fullName}</div>
                      <div className="truncate font-mono text-[11px] text-text3">
                        {[person.position, person.email].filter(Boolean).join(" · ")}
                      </div>
                    </div>
                    {attachedIds.has(person.id) ? (
                      <span className="px-2.5 py-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.05em] text-text3">
                        Added
                      </span>
                    ) : (
                      <Button
                        className="px-3 py-1.5 text-xs"
                        loading={attach.isPending}
                        onClick={() => attach.mutate(person.id)}
                      >
                        Invite
                      </Button>
                    )}
                  </div>
                ))}
              </div>
              <p className="mt-3 font-mono text-[11px] text-text3">
                Selecting a person sends their invite automatically — no email needed.
              </p>
            </>
          ) : (
            <p className="px-4 py-[26px] text-center font-mono text-[12.5px] text-text3">
              No people on record for this client yet. Use{" "}
              <b className="text-text2">Invite by email</b> to add the first contact.
            </p>
          )}
          <div className="mt-[18px] flex justify-end">
            <Button variant="secondary" onClick={onClose}>
              Done
            </Button>
          </div>
        </>
      ) : (
        <InviteByEmail project={project} onDone={onClose} onSettled={invalidate} />
      )}
    </Modal>
  );
}

function InviteByEmail({
  project,
  onDone,
  onSettled,
}: {
  project: Project;
  onDone: () => void;
  onSettled: () => void;
}) {
  const toast = useToast();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [position, setPosition] = useState("");
  const [error, setError] = useState<string | null>(null);

  const invite = useMutation({
    // One decision, two calls: the representative is created on the client, then attached to this
    // mandate — so an accepted invite lands them exactly here, per the modal's promise.
    mutationFn: async () => {
      const representative = await clientsApi.inviteRepresentative(project.clientId, {
        fullName: fullName.trim(),
        position: position.trim() || undefined,
        email: email.trim(),
      });
      return projectsApi.attachRepresentative(project.id, representative.id);
    },
    onSuccess: () => {
      onSettled();
      toast(`Invite sent to ${email.trim()}`);
      onDone();
    },
    onError: (mutationError) => setError(messageFor(mutationError)),
  });

  const submit = () => {
    setError(null);
    if (!fullName.trim() || !email.trim()) {
      setError("Name and email are required.");
      return;
    }
    if (!isValidEmail(email)) {
      setError("Enter a valid work email address.");
      return;
    }
    invite.mutate();
  };

  return (
    <>
      {error && <p className="mb-2 font-mono text-[11px] text-red">{error}</p>}
      <Field label="Full name">
        <Input
          value={fullName}
          onChange={(event) => setFullName(event.target.value)}
          placeholder="e.g. Amir Haddad"
        />
      </Field>
      <Field label="Email address">
        <Input
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="name@company.com"
        />
      </Field>
      <Field label="Title (optional)">
        <Input
          value={position}
          onChange={(event) => setPosition(event.target.value)}
          placeholder="e.g. Chief People Officer"
        />
      </Field>
      <p className="font-mono text-[11px] text-text3">
        We&apos;ll email an invite. They join the project as a client contact once they accept.
      </p>
      <div className="mt-[18px] flex justify-end gap-2">
        <Button variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button loading={invite.isPending} onClick={submit}>
          Send invite
        </Button>
      </div>
    </>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex-1 rounded-md py-[7px] text-[12.5px] font-semibold transition ${
        active ? "bg-panel text-text shadow-panel" : "text-text2 hover:text-text"
      }`}
    >
      {children}
    </button>
  );
}
