import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Button, Field, FormError, Input, Modal, Spinner, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as clientsApi from "../api/clientsApi";
import type { CompanyHit } from "../api/types";

/**
 * The New-client modal — company-database-first, matching Clients.dc.html.
 *
 * Stage one picks the company: search the universe, or add a custom record when it isn't there. Stage
 * two adds an optional primary contact, who gets a portal invite immediately. A DB pick stores the
 * company's rebuild-stable key; the server resolves its canonical name and domain.
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

  const [companyQuery, setCompanyQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [selected, setSelected] = useState<CompanyHit | null>(null);
  const [custom, setCustom] = useState<{ name: string; domain: string } | null>(null);
  const [newCompanyOpen, setNewCompanyOpen] = useState(false);
  const [newCompanyName, setNewCompanyName] = useState("");
  const [newCompanyDomain, setNewCompanyDomain] = useState("");

  const [contactName, setContactName] = useState("");
  const [contactPosition, setContactPosition] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [error, setError] = useState<string | null>(null);

  const hasSelection = selected !== null || custom !== null;

  // Debounce the search so a request does not fire on every keystroke.
  useEffect(() => {
    const handle = setTimeout(() => setDebounced(companyQuery.trim()), 250);
    return () => clearTimeout(handle);
  }, [companyQuery]);

  const { data: hits = [], isFetching } = useQuery({
    queryKey: ["company-search", debounced],
    queryFn: () => clientsApi.searchCompanies(debounced),
    enabled: open && !hasSelection && debounced.length >= 2,
  });

  const create = useMutation({
    mutationFn: () => {
      const primaryContact = contactEmail.trim()
        ? {
            fullName: contactName.trim(),
            position: contactPosition.trim() || undefined,
            email: contactEmail.trim(),
          }
        : null;

      return clientsApi.createClient(
        selected
          ? { company: { source: selected.source, sourceId: selected.sourceId },
              sector: selected.primaryIndustry ?? undefined, primaryContact }
          : { customName: custom!.name, customDomain: custom!.domain || undefined, primaryContact },
      );
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

  const pickHit = (hit: CompanyHit) => {
    if (existingNames.has(hit.name.toLowerCase())) {
      toast(`${hit.name} is already a client`);
      return;
    }
    setSelected(hit);
  };

  const openNewCompany = () => {
    setNewCompanyName(companyQuery.trim());
    setNewCompanyDomain("");
    setNewCompanyOpen(true);
  };

  const confirmNewCompany = () => {
    if (!newCompanyName.trim()) {
      setError("Enter the company name");
      return;
    }
    setError(null);
    setCustom({ name: newCompanyName.trim(), domain: newCompanyDomain.trim() });
    setNewCompanyOpen(false);
  };

  const changeCompany = () => {
    setSelected(null);
    setCustom(null);
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
    if (contactEmail.trim() && !contactEmail.includes("@")) {
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

      {!hasSelection ? (
        <>
          <Field label="Company">
            <Input
              value={companyQuery}
              onChange={(event) => setCompanyQuery(event.target.value)}
              placeholder="Search company database…"
              autoFocus
            />
          </Field>

          {companyQuery.trim().length < 2 ? (
            <p className="font-mono text-[11.5px] text-text3">
              Type at least 2 characters to search the company database.
            </p>
          ) : (
            <div className="max-h-[280px] overflow-y-auto rounded-lg border border-line-soft">
              {isFetching && (
                <div className="flex items-center gap-2 px-3 py-3 font-mono text-[11.5px] text-text3">
                  <Spinner /> Searching…
                </div>
              )}
              {!isFetching &&
                hits.map((hit) => {
                  const alreadyClient = existingNames.has(hit.name.toLowerCase());
                  return (
                    <button
                      key={`${hit.source}:${hit.sourceId}`}
                      type="button"
                      onClick={() => pickHit(hit)}
                      className="flex w-full items-center gap-2.5 border-b border-line-soft px-3 py-2.5 text-left last:border-0 hover:bg-panel2"
                    >
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-[13px] font-medium text-text">
                          {hit.name}
                        </span>
                        <span className="block truncate font-mono text-[11px] text-text3">
                          {[hit.primaryIndustry, hit.hqCountry].filter(Boolean).join(" · ") || "—"}
                        </span>
                      </span>
                      {alreadyClient ? (
                        <span className="rounded-md bg-green-dim px-1.5 py-0.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-green">
                          Client
                        </span>
                      ) : (
                        <span className="font-mono text-[11px] text-sky">Select →</span>
                      )}
                    </button>
                  );
                })}
              <button
                type="button"
                onClick={openNewCompany}
                className="flex w-full items-center gap-1.5 px-3 py-2.5 text-left font-mono text-[11.5px] text-amber hover:bg-panel2"
              >
                ＋ None of these — add “{companyQuery.trim()}” as a new company
              </button>
            </div>
          )}

          {newCompanyOpen && (
            <div className="mt-4 rounded-lg border border-line-soft bg-panel2 p-3.5">
              <div className="mb-3 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
                New company record
              </div>
              <Field label="Company name">
                <Input
                  value={newCompanyName}
                  onChange={(event) => setNewCompanyName(event.target.value)}
                  placeholder="e.g. Meridian Energy Group"
                  autoFocus
                />
              </Field>
              <Field label="Domain · optional, helps us match the client">
                <Input
                  value={newCompanyDomain}
                  onChange={(event) => setNewCompanyDomain(event.target.value)}
                  placeholder="e.g. meridian.ae"
                />
              </Field>
              <div className="flex justify-end gap-2">
                <Button variant="secondary" onClick={() => setNewCompanyOpen(false)}>
                  Cancel
                </Button>
                <Button onClick={confirmNewCompany}>Use this company</Button>
              </div>
            </div>
          )}
        </>
      ) : (
        <>
          <div className="mb-4 flex items-center gap-2.5 rounded-lg border border-line-soft bg-panel2 px-3 py-2.5">
            <span className="min-w-0 flex-1">
              <span className="block truncate text-[13px] font-semibold text-text">
                {selected ? selected.name : custom!.name}
              </span>
              <span className="block truncate font-mono text-[11px] text-text3">
                {selected
                  ? [selected.primaryIndustry, selected.hqCountry].filter(Boolean).join(" · ") +
                    " · from company DB"
                  : "new company record"}
              </span>
            </span>
            <button
              type="button"
              onClick={changeCompany}
              className="font-mono text-[11px] text-sky hover:underline"
            >
              Change
            </button>
          </div>

          <div className="mb-3 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
            Primary contact
            <span className="ml-1 font-normal normal-case tracking-normal text-text3">
              · optional — gets a portal invite. Add more from the client panel later.
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
