import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, FormError, Input, TextArea, useToast } from "../../../components/ui";
import { CompanyLinks } from "../../../components/ui/CompanyLink";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { DrawerCloseButton } from "../../../components/ui/Drawer";
import { messageFor } from "../../../lib/errorCodes";
import * as companiesApi from "../../strategy/api/companiesApi";
import type { CompanySuggestion } from "../../strategy/api/types";
import { useDebouncedValue } from "../../strategy/lib/useComboboxList";
import type { CustomColumn } from "../../customcolumns/api/types";
import * as triageApi from "../api/triageApi";
import type { TriageCompanyStatus } from "../api/types";
import { stageByStatus } from "../lib/triageStages";
import { capturePayloadOf, CompanyFactsForm } from "./CompanyFactsForm";
import { CompanyFactsSections } from "./CompanyFactsSections";

/** Below this the market answers half the alphabet; the picker says so rather than asking for it. */
const MIN_SEARCH_LENGTH = 2;

/**
 * What the Add form is working on. A company reaches a mandate two ways and they are different acts,
 * not two spellings of one: the market's own row is taken by its universe id and the server resolves
 * every field of it, while a company the export does not carry is typed out here and marked as the
 * mandate's own. Searching first is what keeps the second from quietly duplicating the first.
 */
type AddDraft =
  | { step: "search" }
  | { step: "picked"; company: CompanySuggestion }
  | { step: "byHand"; name: string };

/**
 * Adding a company to the mandate: the market first, then the long way round.
 *
 * <p>The name box is a picker over the 71,822-company universe rather than a plain field, because a
 * hand-typed row is the worse of the two outcomes every time the market already holds the company —
 * it carries no universe id, no logo and no refreshable figures, and its Source badge tells every
 * later reader the mandate vouched for facts nobody checked.
 *
 * <p>A picked company is <b>shown, not offered for editing</b>. Its fields belong to the export and
 * are refreshed by it, and the server resolves them from the universe whatever this screen sends —
 * so the panel reads them back rather than pretending they are this form's to fill in.
 */
export function AddCompanyPanel({
  projectId,
  landingStatus,
  customColumns,
  onClose,
  onSaved,
}: {
  projectId: string;
  /** Where a newly added company lands — the stage the grid was showing. */
  landingStatus: TriageCompanyStatus;
  /** This mandate's own company columns, edited in the same save as the fields above them. */
  customColumns: readonly CustomColumn[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [draft, setDraft] = useState<AddDraft>({ step: "search" });
  const [query, setQuery] = useState("");
  const [note, setNote] = useState("");
  const [pickError, setPickError] = useState<string | null>(null);
  const settled = useDebouncedValue(query.trim());

  const matches = useQuery({
    queryKey: companiesApi.COMPANY_SEARCH_KEY(settled),
    queryFn: ({ signal }) => companiesApi.searchCompanies(settled, undefined, signal),
    enabled: draft.step === "search" && settled.length >= MIN_SEARCH_LENGTH,
    // Retyping should narrow the list that is there, not blank it back to "Searching…".
    placeholderData: keepPreviousData,
  });

  const pickedId = draft.step === "picked" ? draft.company.apolloAccountId : null;

  // The suggestion carries a name and a line of context; the record carries the figures a consultant
  // decides on. Read here rather than fattening the typeahead, which answers six rows a keystroke.
  const picked = useQuery({
    queryKey: companiesApi.COMPANY_KEY(pickedId ?? ""),
    queryFn: ({ signal }) => companiesApi.getCompany(pickedId!, signal),
    enabled: pickedId !== null,
  });

  // The record once it lands, and until then whatever the suggestion already answered. A function of
  // the chosen company rather than a value, because "chosen" is a state this component can be out of.
  const factsOf = (chosen: CompanySuggestion) => picked.data ?? factsPendingFor(chosen);

  const take = useMutation({
    mutationFn: (chosen: CompanySuggestion) =>
      triageApi.addMarketCompany(projectId, chosen.apolloAccountId, {
        status: landingStatus,
        note: note.trim() || undefined,
      }),
    onSuccess: (saved) => {
      onSaved();
      // The endpoint is idempotent and its answer cannot say whether this call inserted anything: a
      // company the mandate already held comes back unchanged, at its own stage. So this states where
      // the company now stands rather than claiming an act — true either way, including the likeliest
      // duplicate on this screen, which is re-adding at the very stage being viewed. A stage other
      // than that one is called out, because it is why the grid will not show what was just added.
      toast(
        saved.status === landingStatus
          ? `${saved.companyName} is in this mandate — ${stageByStatus(saved.status).label}`
          : `${saved.companyName} is already in this mandate — ${stageByStatus(saved.status).label}`,
      );
      onClose();
    },
    onError: (error) => setPickError(messageFor(error)),
  });

  const heading =
    draft.step === "byHand"
      ? "For a company the market export does not carry. Only the name is required."
      : "Search the market first — a company it carries brings its own figures with it.";

  return (
    <>
      <div className="relative flex-none border-b border-line-soft px-5 py-4">
        <DrawerCloseButton onClose={onClose} />
        <h2 className="font-sans text-base font-semibold">Add a company</h2>
        <p className="mt-1 pe-8 font-mono text-[11.5px] text-text3">{heading}</p>
      </div>

      {draft.step === "byHand" && (
        <CompanyFactsForm
          company={null}
          seedName={draft.name}
          isCapture
          customColumns={customColumns}
          save={(parsed, customFields) =>
            triageApi.captureCompany(projectId, {
              ...capturePayloadOf(parsed, landingStatus),
              customFields,
            })
          }
          onSaved={(saved) => {
            onSaved();
            toast(`${saved.companyName} added`);
            onClose();
          }}
          onCancel={() => setDraft({ step: "search" })}
        />
      )}

      {draft.step === "search" && (
        <>
          <div className="min-h-0 flex-1 overflow-y-auto px-5 pt-4">
            <Field label="Company name">
              <Input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                autoFocus
                placeholder="Search the market…"
              />
            </Field>

            {settled.length < MIN_SEARCH_LENGTH ? (
              <p className="font-mono text-[11.5px] text-text3">
                Type at least {MIN_SEARCH_LENGTH} characters to search the company universe.
              </p>
            ) : (
              <ul
                aria-label="Companies in the market"
                className="divide-y divide-line-soft overflow-hidden rounded-lg border border-line-soft"
              >
                {(matches.data?.companies ?? []).map((match) => (
                  <li key={match.apolloAccountId}>
                    <button
                      type="button"
                      onClick={() => {
                        setPickError(null);
                        setDraft({ step: "picked", company: match });
                      }}
                      className="flex w-full items-center gap-2.5 px-3 py-2.5 text-left transition hover:bg-panel2"
                    >
                      <CompanyLogo name={match.companyName} logo={match.logoUrl} size={20} />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-sans text-[13px] font-medium text-text">
                          {match.companyName}
                        </span>
                        <span className="block truncate font-mono text-[11px] text-text3">
                          {marketMetaOf(match) || "—"}
                        </span>
                      </span>
                      <span className="flex-none font-mono text-[11px] text-sky">Select →</span>
                    </button>
                  </li>
                ))}
                {matches.isError && (
                  <li className="px-3 py-3 font-mono text-[11.5px] text-red">
                    The company universe could not be searched. Try again in a moment.
                  </li>
                )}
                {matches.isFetching && matches.data === undefined && (
                  <li className="px-3 py-3 font-mono text-[11.5px] text-text3">Searching…</li>
                )}
                {matches.isSuccess && matches.data.companies.length === 0 && (
                  <li className="px-3 py-3 font-mono text-[11.5px] text-text3">
                    Nothing in the market matches “{settled}”.
                  </li>
                )}
                {/* Offered whatever the market answered, a failed read included. A company the
                    export does not carry is the case this door exists for, so closing it when the
                    universe cannot be reached refuses the very company that needs it — the risk of
                    filing a company the market does have is stated in the label instead, to the
                    person who can judge it.

                    Seeded from what is typed rather than from what was last searched: the two differ
                    for a quarter of a second, and the name shown is the one that was typed. */}
                <li>
                  <button
                    type="button"
                    onClick={() => setDraft({ step: "byHand", name: query.trim() })}
                    className="flex w-full items-center gap-1.5 px-3 py-2.5 text-left font-mono text-[11.5px] text-amber transition hover:bg-panel2"
                  >
                    <Icon d={ICONS.plus} size={12} className="flex-none" />
                    {matches.isError
                      ? `The market could not be searched — add “${query.trim()}” as a new company anyway`
                      : `Not here — add “${query.trim()}” as a new company`}
                  </button>
                </li>
              </ul>
            )}
          </div>

          <div className="flex flex-none justify-end gap-2 border-t border-line-soft px-5 py-3">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
          </div>
        </>
      )}

      {draft.step === "picked" && (
        <>
          <div className="min-h-0 flex-1 overflow-y-auto px-5 pt-4">
            <FormError message={pickError} />

            <div className="flex items-center gap-2.5 rounded-lg border border-line-soft bg-panel2 px-3 py-2.5">
              <CompanyLogo
                name={draft.company.companyName}
                logo={draft.company.logoUrl}
                size={28}
              />
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="flex min-w-0 items-center gap-1">
                  <span className="truncate font-sans text-[13px] font-semibold text-text">
                    {draft.company.companyName}
                  </span>
                  {/* Off the record once it arrives, not off the suggestion: the typeahead carries a
                      website and no LinkedIn, so reading the pair from it would show one icon and
                      then quietly grow a second. */}
                  <CompanyLinks
                    companyName={draft.company.companyName}
                    website={factsOf(draft.company).website}
                    linkedinUrl={factsOf(draft.company).companyLinkedinUrl}
                  />
                </span>
                <span className="block truncate font-mono text-[11px] text-text3">
                  {[marketMetaOf(draft.company), "from the market export"]
                    .filter(Boolean)
                    .join(" · ")}
                </span>
              </span>
              <button
                type="button"
                onClick={() => setDraft({ step: "search" })}
                className="flex-none font-mono text-[11px] text-sky transition hover:underline"
              >
                Change
              </button>
            </div>

            {/* A record that would not load never blocks the add: the server resolves the company
                from its id either way, so this is a preview failing, not the company. */}
            {picked.isError ? (
              <p className="mt-4 font-mono text-[11.5px] text-text3">
                The rest of its record could not be read. Adding it still takes the market's own
                figures.
              </p>
            ) : (
              <div aria-busy={picked.isPending}>
                <CompanyFactsSections
                  company={factsOf(draft.company)}
                  emptyDescription={
                    picked.isPending
                      ? "Reading the rest of its record…"
                      : "The export carries no description for this company."
                  }
                />
              </div>
            )}

            <div className="mt-4">
              <Field label="Note" hint="Your own remark on this company, for this mandate.">
                <TextArea
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  rows={3}
                  placeholder="Why this one is worth a look…"
                />
              </Field>
            </div>
          </div>

          <div className="flex flex-none justify-end gap-2 border-t border-line-soft px-5 py-3">
            <Button type="button" variant="secondary" onClick={onClose} disabled={take.isPending}>
              Cancel
            </Button>
            <Button
              type="button"
              variant="primary"
              loading={take.isPending}
              onClick={() => {
                setPickError(null);
                take.mutate(draft.company);
              }}
            >
              Add company
            </Button>
          </div>
        </>
      )}
    </>
  );
}

/**
 * What the suggestion already answers, while the full record is still being read. The tiles it
 * cannot fill show an em dash, which is what they show for a figure the export does not carry
 * either — so the panel never states a blank as a fact before it knows.
 */
function factsPendingFor(suggestion: CompanySuggestion) {
  return {
    companyName: suggestion.companyName,
    shortDescription: null,
    annualRevenue: null,
    numEmployees: suggestion.numEmployees,
    foundedYear: null,
    industry: suggestion.industry,
    website: suggestion.website,
    companyLinkedinUrl: null,
  };
}

/** The muted context line under a market company: whichever of sector and location it has. */
function marketMetaOf(company: CompanySuggestion): string {
  const location = [company.companyCity, company.companyCountry].filter(Boolean).join(", ");
  return [company.industry, location].filter(Boolean).join(" · ");
}
