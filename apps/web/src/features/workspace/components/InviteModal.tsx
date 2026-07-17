import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Field, FormError, Input, Modal, Select, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { titleCase } from "../../../lib/format";
import type { WorkspaceRole } from "../../auth/api/types";
import { INVITE_ROLES } from "../../auth/schemas";
import * as workspaceApi from "../api/workspaceApi";

/** Invite one colleague from the Team or Members screens. Batch rows live in signup step 3. */
export function InviteModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<WorkspaceRole>("MEMBER");
  const [error, setError] = useState<string | null>(null);

  const send = useMutation({
    mutationFn: () => workspaceApi.invite([{ email: email.trim(), role }]),
    onSuccess: ({ sent }) => {
      void queryClient.invalidateQueries({ queryKey: workspaceApi.INVITATIONS_KEY });
      toast(sent > 0 ? "Invitation sent" : "They're already a member");
      onClose();
    },
    onError: (mutationError) => setError(messageFor(mutationError)),
  });

  const submit = () => {
    setError(null);
    if (!email.trim()) {
      setError("Enter an email address");
      return;
    }
    send.mutate();
  };

  return (
    <Modal open={open} onClose={onClose} title="Invite a colleague">
      <FormError message={error} />

      <Field label="Email" hint="Invitees get access immediately — your naming them is the approval.">
        <Input
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="colleague@firm.com"
          autoFocus
        />
      </Field>

      <Field label="Role">
        <Select value={role} onChange={(event) => setRole(event.target.value as WorkspaceRole)}>
          {INVITE_ROLES.map((option) => (
            <option key={option} value={option}>
              {titleCase(option)}
            </option>
          ))}
        </Select>
      </Field>

      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button loading={send.isPending} onClick={submit}>
          Send invite
        </Button>
      </div>
    </Modal>
  );
}
