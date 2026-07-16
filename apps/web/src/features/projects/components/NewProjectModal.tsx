import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Field, FormError, Input, Modal, Select, useToast } from "../../../components/ui";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import type { Member } from "../../workspace/api/types";
import * as projectsApi from "../api/projectsApi";
import type { Client } from "../api/types";

const NEW_CLIENT = "__new__";

/**
 * The mockup's New-project modal: client (pick or create inline), position, lead, target date.
 * A 409 on the inline client quietly resolves to the existing record — the user meant that client.
 */
export function NewProjectModal({
  open,
  onClose,
  clients,
  members,
  defaultLeadMemberId,
}: {
  open: boolean;
  onClose: () => void;
  clients: Client[];
  members: Member[];
  defaultLeadMemberId?: string;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const [clientId, setClientId] = useState(clients[0]?.id ?? NEW_CLIENT);
  const [newClientName, setNewClientName] = useState("");
  const [positionTitle, setPositionTitle] = useState("");
  const [leadMemberId, setLeadMemberId] = useState(defaultLeadMemberId ?? members[0]?.memberId ?? "");
  const [targetDate, setTargetDate] = useState("");
  const [error, setError] = useState<string | null>(null);

  const creatingClient = clientId === NEW_CLIENT;

  const create = useMutation({
    mutationFn: async () => {
      let resolvedClientId = clientId;
      if (creatingClient) {
        try {
          resolvedClientId = (await projectsApi.createClient({ name: newClientName })).id;
        } catch (clientError) {
          if (codeOf(clientError) !== "CLIENT_ALREADY_EXISTS") throw clientError;
          // The user meant that client. Re-fetch rather than trust the prop — a colleague may have
          // created it after this modal's list was cached.
          const fresh = await projectsApi.clients();
          const existing = fresh.find(
            (c) => c.name.toLowerCase() === newClientName.trim().toLowerCase(),
          );
          if (!existing) throw clientError;
          resolvedClientId = existing.id;
        }
      }
      return projectsApi.createProject({
        clientId: resolvedClientId,
        positionTitle: positionTitle.trim(),
        leadMemberId,
        targetDate: targetDate || undefined,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      void queryClient.invalidateQueries({ queryKey: projectsApi.CLIENTS_KEY });
      toast("Project created — starts at Brief");
      onClose();
    },
    onError: (mutationError) => setError(messageFor(mutationError)),
  });

  const submit = () => {
    setError(null);
    if (creatingClient && !newClientName.trim()) {
      setError("Enter the client's name");
      return;
    }
    if (!positionTitle.trim()) {
      setError("Enter the position title");
      return;
    }
    if (!leadMemberId) {
      setError("Choose a lead");
      return;
    }
    create.mutate();
  };

  return (
    <Modal open={open} onClose={onClose} title="New project">
      <FormError message={error} />

      <Field label="Client">
        <Select value={clientId} onChange={(event) => setClientId(event.target.value)}>
          {clients.map((client) => (
            <option key={client.id} value={client.id}>
              {client.name}
            </option>
          ))}
          <option value={NEW_CLIENT}>＋ New client…</option>
        </Select>
      </Field>

      {creatingClient && (
        <Field label="Client name">
          <Input
            value={newClientName}
            onChange={(event) => setNewClientName(event.target.value)}
            placeholder="e.g. Meridian Energy Group"
            autoFocus
          />
        </Field>
      )}

      <Field label="Position">
        <Input
          value={positionTitle}
          onChange={(event) => setPositionTitle(event.target.value)}
          placeholder="e.g. Chief Financial Officer"
        />
      </Field>

      <Field label="Lead">
        <Select value={leadMemberId} onChange={(event) => setLeadMemberId(event.target.value)}>
          {members.map((member) => (
            <option key={member.memberId} value={member.memberId}>
              {member.fullName}
            </option>
          ))}
        </Select>
      </Field>

      <Field label="Target date">
        <Input type="date" value={targetDate} onChange={(event) => setTargetDate(event.target.value)} />
      </Field>

      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button loading={create.isPending} onClick={submit}>
          Create project
        </Button>
      </div>
    </Modal>
  );
}
