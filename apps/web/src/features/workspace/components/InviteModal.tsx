import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Field, FormError, Input, Modal, Select, useToast } from "../../../components/ui";
import { EMAIL_FIELD_ERROR_CODES, codeOf, messageFor } from "../../../lib/errorCodes";
import { fieldErrorsFrom } from "../../../lib/formErrors";
import { titleCase } from "../../../lib/format";
import type { WorkspaceRole } from "../../auth/api/types";
import { INVITE_ROLES } from "../../auth/schemas";
import { SeatChargeConfirm } from "../../billing/components/SeatChargeConfirm";
import { SeatCostNotice } from "../../billing/components/SeatCostNotice";
import { seatChargeOf } from "../../billing/lib/billingView";
import { useBillingRead } from "../../billing/lib/useBilling";
import * as workspaceApi from "../api/workspaceApi";

/** Invite one colleague from the Team or Members screens. Batch rows live in signup step 3. */
export function InviteModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<WorkspaceRole>("MEMBER");
  const [error, setError] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const billing = useBillingRead(role !== "CLIENT");
  const charge = role !== "CLIENT" && billing.data ? seatChargeOf(billing.data) : null;
  const pricing = role !== "CLIENT" && billing.isPending;

  const send = useMutation({
    mutationFn: () => workspaceApi.invite([{ email: email.trim(), role }]),
    onSuccess: ({ sent }) => {
      void queryClient.invalidateQueries({ queryKey: workspaceApi.INVITATIONS_KEY });
      toast(sent > 0 ? "Invitation sent" : "They're already a member");
      onClose();
    },
    onError: (mutationError) => {
      // A domain rule the client cannot check — consumer, disposable, no mailbox behind it — is still
      // a verdict on the address in the field, and arrives as a code rather than a fieldErrors entry.
      // (The list's fourth code, EMAIL_ALREADY_REGISTERED, is signup's alone: an address that already
      // has an account is skipped here by isAlreadyMember and surfaces as the `sent === 0` toast.)
      const code = codeOf(mutationError);
      if (code && EMAIL_FIELD_ERROR_CODES.includes(code)) {
        setConfirming(false);
        setEmailError(messageFor(mutationError));
        return;
      }

      // The endpoint takes a list, so a rejected address is attributed to its row, not to `email`.
      const { fields, formMessage } = fieldErrorsFrom(mutationError, {
        "requests.email": "email",
        requests: "email",
      });
      setConfirming(false);
      setEmailError(fields.email ?? null);
      setError(formMessage);
    },
  });

  const submit = () => {
    setError(null);
    setEmailError(null);
    if (!email.trim()) {
      setEmailError("Enter an email address");
      return;
    }
    if (charge && !confirming) {
      setConfirming(true);
      return;
    }
    send.mutate();
  };

  const confirmingCharge = confirming ? charge : null;

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={confirmingCharge ? "Add a paid seat?" : "Invite a colleague"}
      footer={
        confirmingCharge ? (
          <>
            <Button variant="secondary" onClick={() => setConfirming(false)}>
              Back
            </Button>
            <Button loading={send.isPending} onClick={submit}>
              Send and add seat
            </Button>
          </>
        ) : (
          <>
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button loading={send.isPending} disabled={pricing} onClick={submit}>
              Send invite
            </Button>
          </>
        )
      }
    >
      {confirmingCharge ? (
        <SeatChargeConfirm email={email.trim()} roleLabel={titleCase(role)} charge={confirmingCharge} />
      ) : (
        <>
          <FormError message={error} />

          <Field
            label="Email"
            error={emailError ?? undefined}
            hint="They'll get access when they accept."
          >
            <Input
              type="email"
              value={email}
              onChange={(event) => {
                setEmail(event.target.value);
                // Cleared on edit rather than only on the next submit, matching react-hook-form's
                // reValidateMode on every other form that renders an inline error.
                setEmailError(null);
              }}
              invalid={!!emailError}
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

          <SeatCostNotice role={role} />
        </>
      )}
    </Modal>
  );
}
