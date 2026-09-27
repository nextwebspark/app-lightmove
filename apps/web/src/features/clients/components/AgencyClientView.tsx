import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Input, TextArea, useToast } from "../../../components/ui";
import { CompanyDrawerHeader } from "../../../components/ui/CompanyDrawerHeader";
import { CountryField } from "../../../components/ui/CountryField";
import { DrawerSection } from "../../../components/ui/DetailList";
import { messageFor } from "../../../lib/errorCodes";
import { toBrowsableUrl } from "../../../lib/url";
import * as companiesApi from "../../strategy/api/companiesApi";
import { CompanyFactsSections } from "../../triage/components/CompanyFactsSections";
import type { HiringPersona } from "../../workspace/api/types";
import { PersonaFields } from "../../workspace/components/PersonaFields";
import * as clientsApi from "../api/clientsApi";
import type { ClientDetail, UpdateClientPayload } from "../api/types";
import { ClientDrawerFoot, ClientPositions, DrawerField, Representatives } from "./ClientRecordParts";

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
        {company && <CompanyFactsSections company={company} />}
        <ClientDetailsSection client={client} editsCompanyFacts={accountId === null} />
        <ClientPersonaSection client={client} />
        <section className="border-b border-u-border py-4">
          <Representatives client={client} />
        </section>
        <section className="py-4">
          <ClientPositions client={client} onOpenMandate={onOpenMandate} />
        </section>
      </div>

      <ClientDrawerFoot onClose={onClose} onNewMandate={onNewMandate} />
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
    mutationFn: () => clientsApi.updateClient(client.id, payloadOf(draft, editsCompanyFacts)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: clientsApi.clientKey(client.id) });
      void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
      toast("Client saved");
    },
    onError: (error) => toast(messageFor(error)),
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
            <Button loading={save.isPending} disabled={!draft.name.trim()} onClick={() => save.mutate()}>
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
      <DrawerField label="Notes">
        <TextArea
          rows={3}
          maxLength={2000}
          value={draft.notes}
          onChange={(event) => update({ notes: event.target.value })}
          placeholder="e.g. retained for the C-suite, prefers GCC nationals for board roles"
        />
      </DrawerField>
    </DrawerSection>
  );
}

function ClientPersonaSection({ client }: { client: ClientDetail }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [draft, setDraft] = useState<HiringPersona>(client.persona);

  const save = useMutation({
    mutationFn: () => clientsApi.updateClientPersona(client.id, draft),
    onSuccess: (updated) => {
      queryClient.setQueryData(clientsApi.clientKey(client.id), updated);
      setDraft(updated.persona);
      toast("Client persona saved");
    },
    onError: (error) => toast(messageFor(error)),
  });

  return (
    <DrawerSection title="Client persona">
      <p className="-mt-1 mb-3 font-mono text-[11.5px] text-u-text3">
        What this client does — Uncava&apos;s assistant tailors research for its positions to it.
      </p>
      <PersonaFields
        value={draft}
        onChange={setDraft}
        idPrefix={`client-${client.id}-persona`}
        summaryPlaceholder="e.g. Saudi Arabia's largest private hospital operator, expanding into outpatient care"
      />
      <div className="flex justify-end">
        <Button loading={save.isPending} onClick={() => save.mutate()}>
          Save persona
        </Button>
      </div>
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

/** The PATCH is partial: a universe pick's company facts are never sent, so they are never overwritten. */
function payloadOf(draft: ClientDetails, editsCompanyFacts: boolean): UpdateClientPayload {
  const own = { name: draft.name.trim(), notes: draft.notes.trim() };
  if (!editsCompanyFacts) return own;
  return { ...own, sector: draft.sector.trim(), hqCountry: draft.hqCountry.trim(), domain: draft.domain.trim() };
}
