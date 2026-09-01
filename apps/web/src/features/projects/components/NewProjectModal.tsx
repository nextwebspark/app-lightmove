import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, DateInput, Field, FormError, Input, Modal, Select, useToast } from "../../../components/ui";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import * as clientsApi from "../../clients/api/clientsApi";
import type { Client } from "../../clients/api/types";
import * as positionApi from "../../position/api/positionApi";
import { RoleTitleCombobox } from "../../position/components/RoleTitleCombobox";
import * as projectsApi from "../api/projectsApi";

const NEW_CLIENT = "__new__";

/**
 * The New-project modal: client (pick or create inline), position (typed free, or picked from the
 * role-template library — the same combobox as the brief's step one), target date. There is no lead to
 * choose — whoever creates the mandate is seated as its admin (and lead) by the server, and delegates
 * from the project drawer afterwards. A 409 on the inline client quietly resolves to the existing
 * record — the user meant that client.
 *
 * Opened from a client's drawer, the entrance has already decided the client: the field is shown
 * locked and `lockedClientId` — not state — is what gets submitted, so the mandate cannot land on a
 * different client than the drawer behind the modal. Picking or creating a client belongs to the
 * other entrance (Projects → New project), which passes no `lockedClientId`.
 */
export function NewProjectModal({
  open,
  onClose,
  clients,
  lockedClientId,
}: {
  open: boolean;
  onClose: () => void;
  clients: Client[];
  /** Locks the client to this one — set when opening from that client's drawer ("New mandate"). */
  lockedClientId?: string;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();

  // null until the user picks: seeding from `clients` at mount mirrors server state, and the list is
  // still empty on the render where Projects opens this modal before its clients query has settled.
  const [pickedClientId, setPickedClientId] = useState<string | null>(null);
  const [newClientName, setNewClientName] = useState("");
  const [positionTitle, setPositionTitle] = useState("");
  const [targetDate, setTargetDate] = useState("");
  const [error, setError] = useState<string | null>(null);

  // The picker's options, sharing the Position page's cache. A failed read leaves the field a plain
  // typeable input — the same degradation as there.
  const { data: templates = [] } = useQuery({
    queryKey: positionApi.POSITION_TEMPLATES_KEY,
    queryFn: ({ signal }) => positionApi.listTemplates(signal),
    staleTime: 5 * 60 * 1000,
  });

  // Derived from the prop on every render, never seeded into state: a mount-time seed goes stale the
  // moment this modal is rendered always-mounted with `open` toggled, and would then aim the mandate
  // at whichever client's drawer was opened before. One `locked` value decides both what is rendered
  // and what is submitted — two tests of the same prop are how the shown client and the sent one drift
  // apart, which is the bug this lock exists to close.
  const locked = lockedClientId ? clients.find((client) => client.id === lockedClientId) : undefined;
  const clientId = lockedClientId || pickedClientId || clients[0]?.id || NEW_CLIENT;

  const creatingClient = clientId === NEW_CLIENT;

  const create = useMutation({
    mutationFn: async () => {
      let resolvedClientId = clientId;
      if (creatingClient) {
        try {
          // A quick custom client from the project flow; the full registry create lives on Clients.
          resolvedClientId = (await clientsApi.createClient({ customName: newClientName })).id;
        } catch (clientError) {
          if (codeOf(clientError) !== "CLIENT_ALREADY_EXISTS") throw clientError;
          // The user meant that client. Re-fetch rather than trust the prop — a colleague may have
          // created it after this modal's list was cached.
          const fresh = await clientsApi.clients();
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
        targetDate: targetDate || undefined,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
      toast("Project created — you're its admin and lead");
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
    create.mutate();
  };

  return (
    // overflow-visible: the dialog's own scroll clips the template list at the modal's edge; this
    // form is short enough to never need the scroll, and the list must float over the boundary.
    <Modal open={open} onClose={onClose} title="New project" className="overflow-visible">
      <FormError message={error} />

      {/* The hint carries the name because a disabled <select> is skipped in a screen reader's forms
          mode — Field renders the hint inside the wrapping <label>, so it reaches the accessible name
          even when the control itself never gets focus. */}
      <Field
        label="Client"
        hint={locked ? `This mandate belongs to ${locked.name}.` : undefined}
      >
        {lockedClientId ? (
          // Disabled rather than replaced by plain text: the user still sees which client the mandate
          // is for, and the label keeps a control to name.
          <Select value={lockedClientId} disabled className="cursor-not-allowed opacity-60">
            <option value={lockedClientId}>{locked?.name ?? "Selected client"}</option>
          </Select>
        ) : (
          <Select value={clientId} onChange={(event) => setPickedClientId(event.target.value)}>
            {clients.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
            <option value={NEW_CLIENT}>＋ New client…</option>
          </Select>
        )}
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

      {/* Picking a template only fills the title: creation seeds the brief from the title on the
          server, through the same keyword match a typed one gets, so no id travels with the form. */}
      <Field label="Position">
        <RoleTitleCombobox
          value={positionTitle}
          templates={templates}
          busy={false}
          onChange={setPositionTitle}
          onPick={(template) => setPositionTitle(template.title)}
        />
      </Field>

      <Field label="Target date">
        <DateInput value={targetDate} onChange={setTargetDate} />
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
