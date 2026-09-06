import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Field, FormError, Input, Modal, useToast } from "../../../components/ui";
import { isValidEmail } from "../../../lib/email";
import { messageFor } from "../../../lib/errorCodes";
import * as clientsApi from "../api/clientsApi";
import { CompanyPicker, createPayloadFor, type CompanyPick } from "./CompanyPicker";

/**
 * The New-client modal — company-database-first, matching Clients.dc.html.
 *
 * Stage one picks the company through the shared {@link CompanyPicker}: search the universe, or add a
 * custom record when it isn't there. Stage two adds an optional primary contact, who is invited as a
 * representative immediately.
 */
export function NewClientModal({
  open,
  onClose,
  existingNames,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  /** Lower-cased names of the current clients — a search hit already on the books shows a CLIENT badge. */
  existingNames: Set<string>;
  onCreated: (client: { id: string; name: string }) => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const [pick, setPick] = useState<CompanyPick | null>(null);
  const [contactName, setContactName] = useState("");
  const [contactPosition, setContactPosition] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [error, setError] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () => {
      const primaryContact = contactEmail.trim()
        ? {
            fullName: contactName.trim(),
            position: contactPosition.trim() || undefined,
            email: contactEmail.trim(),
          }
        : null;

      return clientsApi.createClient({ ...createPayloadFor(pick!), primaryContact });
    },
    onSuccess: (client) => {
      void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
      toast(
        contactEmail.trim()
          ? `${client.name} added — invite sent to ${contactEmail.trim()}`
          : `${client.name} added as client`,
      );
      onCreated(client);
      onClose();
    },
    onError: (mutationError) => setError(messageFor(mutationError)),
  });

  const handlePick = (next: CompanyPick | null) => {
    setPick(next);
    setError(null);
  };

  const submit = () => {
    setError(null);
    // The three contact fields move together: any one filled requires all three (and a valid email).
    const anyContact = contactName.trim() || contactPosition.trim() || contactEmail.trim();
    if (anyContact && (!contactName.trim() || !contactPosition.trim() || !contactEmail.trim())) {
      setError("All three fields are required to send an invite.");
      return;
    }
    if (contactEmail.trim() && !isValidEmail(contactEmail)) {
      setError("Enter a valid work email address.");
      return;
    }
    create.mutate();
  };

  const createLabel = contactEmail.trim() ? "Create client & send invite" : "Create client";

  return (
    <Modal open={open} onClose={onClose} title="New client">
      <p className="-mt-2 mb-4 font-mono text-[11.5px] text-text3">
        Search the company database first — most clients already exist as records.
      </p>
      <FormError message={error} />

      <CompanyPicker
        pick={pick}
        onPick={handlePick}
        existingNames={existingNames}
        onRejectExisting={(name) => toast(`${name} is already a client`)}
        autoFocus
      />

      {pick && (
        <>
          <div className="mb-3 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
            Primary contact
            <span className="ml-1 font-normal normal-case tracking-normal text-text3">
              · optional — gets an invite as a representative. Add more from the client panel later.
            </span>
          </div>
          <div className="flex gap-2.5">
            <div className="flex-1">
              <Field label="Full name">
                <Input
                  value={contactName}
                  onChange={(event) => setContactName(event.target.value)}
                  placeholder="e.g. Khalid Al-Otaibi"
                />
              </Field>
            </div>
            <div className="flex-1">
              <Field label="Position">
                <Input
                  value={contactPosition}
                  onChange={(event) => setContactPosition(event.target.value)}
                  placeholder="e.g. Group CHRO"
                />
              </Field>
            </div>
          </div>
          <Field label="Work email">
            <Input
              type="email"
              value={contactEmail}
              onChange={(event) => setContactEmail(event.target.value)}
              placeholder="name@company.com"
            />
          </Field>

          <div className="mt-5 flex justify-end gap-2">
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button loading={create.isPending} onClick={submit}>
              {createLabel}
            </Button>
          </div>
        </>
      )}
    </Modal>
  );
}
