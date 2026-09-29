import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Field, FormError, Input, Modal, useToast } from "../../../components/ui";
import { isValidEmail } from "../../../lib/email";
import { messageFor } from "../../../lib/errorCodes";
import { useWorkspaceMode, useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as clientsApi from "../api/clientsApi";
import type { Client } from "../api/types";
import type { CompanyPick } from "../lib/companyPick";
import { BusinessUnitCombobox } from "./BusinessUnitCombobox";
import { CompanyPicker } from "./CompanyPicker";

/** Mirrors `@Size(max = 160)` on CreateClientRequest.customName. */
const MAX_UNIT_NAME_LENGTH = 160;

/**
 * The New-client modal (Clients.dc.html). An agency picks the company from the database through
 * {@link CompanyPicker}, or adds one it does not carry; an in-house workspace names a business unit of
 * its own firm, which the company database has no row for. Either then adds an optional primary
 * contact, who is invited as a representative immediately.
 */
export function NewClientModal({
  open,
  onClose,
  clients,
  existingNames,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  clients: Client[];
  /** Lower-cased names of the current clients — a search hit already on the books shows a CLIENT badge. */
  existingNames: Set<string>;
  onCreated: (client: { id: string; name: string }) => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const vocabulary = useWorkspaceVocabulary();
  const isAgency = useWorkspaceMode() === "AGENCY";

  const [pick, setPick] = useState<CompanyPick | null>(null);
  const [unitName, setUnitName] = useState("");
  const [contactName, setContactName] = useState("");
  const [contactPosition, setContactPosition] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [error, setError] = useState<string | null>(null);

  const trimmedUnitName = unitName.trim();
  const unitExists = existingNames.has(trimmedUnitName.toLowerCase());
  const unitNameError = unitExists
    ? `${trimmedUnitName} is already a ${vocabulary.unitLower}`
    : trimmedUnitName.length > MAX_UNIT_NAME_LENGTH
      ? `That name is too long — keep it to ${MAX_UNIT_NAME_LENGTH} characters or fewer`
      : undefined;
  const isReady = isAgency ? pick !== null : trimmedUnitName !== "" && !unitNameError;

  const create = useMutation({
    mutationFn: () => {
      const primaryContact = contactEmail.trim()
        ? {
            fullName: contactName.trim(),
            position: contactPosition.trim() || undefined,
            email: contactEmail.trim(),
          }
        : null;

      return clientsApi.createClient({
        ...(isAgency ? clientsApi.createClientPayloadFor(pick!) : { customName: trimmedUnitName }),
        primaryContact,
      });
    },
    onSuccess: (client) => {
      void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
      toast(
        contactEmail.trim()
          ? `${client.name} added — invite sent to ${contactEmail.trim()}`
          : `${client.name} added as a ${vocabulary.unitLower}`,
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

  const createLabel = contactEmail.trim() ? "Create & send invite" : `Create ${vocabulary.unitLower}`;

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`New ${vocabulary.unitLower}`}
      footer={
        // The actions arrive with the pick: until a company is chosen there is nothing to create.
        isReady && (
          <>
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button loading={create.isPending} onClick={submit}>
              {createLabel}
            </Button>
          </>
        )
      }
    >
      <p className="-mt-2 mb-4 font-mono text-[11.5px] text-u-text3">
        {isAgency
          ? `Search the company database first — or add a ${vocabulary.unitLower} that isn't listed.`
          : `Name the ${vocabulary.unitLower} — it is added to your organisation.`}
      </p>
      <FormError message={error} />

      {isAgency ? (
        <CompanyPicker
          pick={pick}
          onPick={handlePick}
          existingNames={existingNames}
          onRejectExisting={(name) => toast(`${name} is already a ${vocabulary.unitLower}`)}
          autoFocus
        />
      ) : (
        <Field label={vocabulary.unit} error={trimmedUnitName ? unitNameError : undefined}>
          <BusinessUnitCombobox
            value={unitName}
            clients={clients}
            invalid={!!trimmedUnitName && !!unitNameError}
            onChange={(name) => {
              setUnitName(name);
              setError(null);
            }}
          />
        </Field>
      )}

      {isReady && (
        <>
          <div className="mb-3 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
            Primary {vocabulary.contactLower}
            <span className="ml-1 font-normal normal-case tracking-normal text-u-text3">
              · optional — gets an invite. Add more from the {vocabulary.unitLower} panel later.
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
        </>
      )}
    </Modal>
  );
}
