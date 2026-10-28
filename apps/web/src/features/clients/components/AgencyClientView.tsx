import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Input, useToast } from "../../../components/ui";
import { CompanyDrawerHeader } from "../../../components/ui/CompanyDrawerHeader";
import { CountryField } from "../../../components/ui/CountryField";
import { DrawerSection } from "../../../components/ui/DetailList";
import { messageFor } from "../../../lib/errorCodes";
import { toBrowsableUrl } from "../../../lib/url";
import * as companiesApi from "../../strategy/api/companiesApi";
import { CompanyFactsSections } from "../../triage/components/CompanyFactsSections";
import * as clientsApi from "../api/clientsApi";
import type { ClientDetail, UpdateClientPayload } from "../api/types";
import { ClientArchivedNotice, ClientDrawerActions } from "./ClientArchive";
import { NoteEditor } from "../../notes/components/NoteEditor";
import { CLIENT_NOTES_MAX, CLIENT_NOTES_TOO_LONG } from "../lib/clientNotes";
import {
  ClientPersonaSection,
  ClientPositions,
  DrawerField,
  DrawerFieldGroup,
  Representatives,
} from "./ClientRecordParts";

/**
 * An agency's client, drawn as the company panel Strategy and Companies open: the company's own facts
 * from the universe where it was picked from there, editable basics where it was typed in, then the
 * persona the assistant reads for its positions, its contacts and its positions.
 */
export function AgencyClientView({
  client,
  onClose,
  onOpenMandate,
  onNewMandate,
}: {
  client: ClientDetail;
  onClose: () => void;
  onOpenMandate: (mandateId: string) => void;
  onNewMandate: () => void;
}) {
  const accountId = client.apolloAccountId;
  const universe = useQuery({
    queryKey: companiesApi.COMPANY_KEY(accountId ?? ""),
    queryFn: ({ signal }) => companiesApi.getCompany(accountId!, signal),
    enabled: accountId !== null,
  });
  // The record's own snapshot stands in while the universe row loads, and for good if it has left.
  const company = universe.data;

  return (
    <>
      <CompanyDrawerHeader
        companyName={client.name}
        logoUrl={company?.logoUrl ?? client.logoUrl}
        website={company?.website ?? toBrowsableUrl(client.domain)}
        linkedinUrl={company?.companyLinkedinUrl ?? null}
        context={[
          company?.industry ?? client.sector,
          client.hqCity ?? company?.companyCity ?? null,
          client.hqCountry ?? company?.companyCountry ?? null,
        ]}
        onClose={onClose}
      />

      <div className="min-h-0 flex-1 overflow-y-auto px-5">
        <ClientArchivedNotice client={client} className="mt-4" />
        {company && <CompanyFactsSections company={company} />}
        <ClientDetailsSection client={client} editsCompanyFacts={accountId === null} />
        <ClientPersonaSection
          client={client}
          hint="What this client does — Uncava's assistant tailors research for its positions to it."
          summaryPlaceholder="e.g. Saudi Arabia's largest private hospital operator, expanding into outpatient care"
        />
        <section className="border-b border-u-border py-4">
          <Representatives client={client} />
        </section>
        <section className="py-4">
          <ClientPositions client={client} onOpenMandate={onOpenMandate} />
        </section>
      </div>

      <ClientDrawerActions client={client} onClose={onClose} onNewMandate={onNewMandate} />
    </>
  );
}

/**
 * The agency's own record of the client. A universe pick's company facts are the universe's and read
 * only above; a client typed in by hand has no such source, so its sector, country and website are
 * edited here instead.
 */
function ClientDetailsSection({ client, editsCompanyFacts }: { client: ClientDetail; editsCompanyFacts: boolean }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const saved = detailsOf(client);
  const [draft, setDraft] = useState(saved);

  const dirty = (Object.keys(saved) as (keyof ClientDetails)[]).some((key) => draft[key] !== saved[key]);
  const update = (patch: Partial<ClientDetails>) => setDraft((current) => ({ ...current, ...patch }));

  const save = useMutation({
    mutationFn: () => clientsApi.updateClient(client.id, payloadOf(draft, saved, editsCompanyFacts)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: clientsApi.clientKey(client.id) });
      void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
      toast("Client saved");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <DrawerSection
      title="Details"
      action={
        dirty && (
          <span className="flex gap-2">
            <Button variant="secondary" onClick={() => setDraft(saved)}>
              Discard
            </Button>
            <Button
              loading={save.isPending}
              disabled={!draft.name.trim() || draft.notes.trim().length > CLIENT_NOTES_MAX}
              onClick={() => save.mutate()}
            >
              Save changes
            </Button>
          </span>
        )
      }
    >
      <DrawerField label="Client name">
        <Input value={draft.name} onChange={(event) => update({ name: event.target.value })} />
      </DrawerField>
      {editsCompanyFacts && (
        <>
          <DrawerField label="Sector">
            <Input
              value={draft.sector}
              onChange={(event) => update({ sector: event.target.value })}
              placeholder="e.g. Hospitals & Health Care"
            />
          </DrawerField>
          <DrawerField label="Country">
            <CountryField
              listId={`client-${client.id}-country`}
              value={draft.hqCountry}
              onChange={(hqCountry) => update({ hqCountry })}
            />
          </DrawerField>
          <DrawerField label="Website">
            <Input
              value={draft.domain}
              onChange={(event) => update({ domain: event.target.value })}
              placeholder="e.g. harbourhealth.com"
            />
          </DrawerField>
        </>
      )}
      <DrawerFieldGroup label="Notes">
        <div className="rounded-u-control border border-u-border p-2">
          <NoteEditor
            value={draft.notes}
            format={client.notesFormat}
            onChange={(notes) => update({ notes })}
            teammates={null}
            ariaLabel="Notes"
            placeholder="e.g. retained for the C-suite, prefers GCC nationals for board roles"
            expandTitle="Client notes"
            variant="field"
          />
        </div>
        {draft.notes.trim().length > CLIENT_NOTES_MAX && (
          <p role="alert" className="mt-1.5 text-meta text-u-offlimits">
            {CLIENT_NOTES_TOO_LONG}
          </p>
        )}
      </DrawerFieldGroup>
    </DrawerSection>
  );
}

interface ClientDetails {
  name: string;
  sector: string;
  hqCountry: string;
  domain: string;
  notes: string;
}

function detailsOf(client: ClientDetail): ClientDetails {
  return {
    name: client.name,
    sector: client.sector ?? "",
    hqCountry: client.hqCountry ?? "",
    domain: client.domain ?? "",
    notes: client.notes ?? "",
  };
}

/**
 * The PATCH is partial: a universe pick's company facts are never sent, so they are never overwritten, and untouched
 * notes are left out, since sent back as Markdown a plain note's "- " and "1." would turn into lists.
 */
function payloadOf(draft: ClientDetails, saved: ClientDetails, editsCompanyFacts: boolean): UpdateClientPayload {
  const own: UpdateClientPayload = {
    name: draft.name.trim(),
    ...(draft.notes !== saved.notes && { notes: draft.notes.trim(), notesFormat: "markdown" as const }),
  };
  if (!editsCompanyFacts) return own;
  return { ...own, sector: draft.sector.trim(), hqCountry: draft.hqCountry.trim(), domain: draft.domain.trim() };
}
