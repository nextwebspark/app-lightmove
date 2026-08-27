import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, FormError, Input, TextArea, useToast } from "../../../components/ui";
import { CompanyLink } from "../../../components/ui/CompanyLink";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import {
  DetailGrid,
  DetailPill,
  DetailTile,
  DrawerSection,
} from "../../../components/ui/DetailList";
import { Drawer } from "../../../components/ui/Drawer";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate, formatMoney } from "../../../lib/format";
import * as companiesApi from "../../strategy/api/companiesApi";
import type { CompanySuggestion } from "../../strategy/api/types";
import { useDebouncedValue } from "../../strategy/lib/useComboboxList";
import * as triageApi from "../api/triageApi";
import type { TriageCompany, TriageCompanyStatus } from "../api/types";
import { stageByStatus } from "../lib/triageStages";
import { MOVES, SOURCE_STYLES } from "../lib/triageVocabulary";
import { capturePayloadOf, CompanyFactsForm, editPayloadOf } from "./CompanyFactsForm";

/** Below this the market answers half the alphabet; the picker says so rather than asking for it. */
const MIN_SEARCH_LENGTH = 2;

/**
 * One company, in the mandate's own terms — the panel the mockup's `coDrawer` describes, finally
 * built, and the form that adds a new one.
 *
 * <p><b>It opens read-only.</b> A consultant clicking a company name is reading, not typing, and a
 * form that opens over the grid on every click is a form you dismiss without looking at. Edit is a
 * deliberate second step.
 *
 * <p><b>A company taken from Strategy has no Edit button at all.</b> Its fields are the market
 * export's snapshot, refreshed by the export, and a mandate rewriting them would make the Source badge
 * a claim the figures no longer support. The server refuses the write too — the missing button is the
 * courtesy, not the control.
 *
 * <p>The <b>Note</b> is the exception, and stays editable on every company including those. It is the
 * mandate's own remark rather than a fact about the company, which is exactly why the export cannot
 * own it — and it is the reason a consultant opens this panel rather than Strategy's.
 */
export function CompanyDrawer({
  open,
  projectId,
  company,
  landingStatus,
  canWrite,
  onClose,
  onSaved,
  onMove,
  onDelete,
}: {
  open: boolean;
  projectId: string;
  /** The company being read, or null to add a new one. */
  company: TriageCompany | null;
  /** Where a newly added company lands — the stage the grid was showing. */
  landingStatus: TriageCompanyStatus;
  canWrite: boolean;
  onClose: () => void;
  onSaved: () => void;
  onMove: (company: TriageCompany, status: TriageCompanyStatus) => void;
  onDelete: (company: TriageCompany) => void;
}) {
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const [note, setNote] = useState("");

  const saveNote = useMutation({
    mutationFn: (text: string) =>
      triageApi.updateTriageCompany(projectId, company!.id, { note: text }),
    onSuccess: () => {
      onSaved();
      toast("Note saved");
    },
    onError: (error) => toast(messageFor(error)),
  });

  // Reopening should show the company being read, never the half-typed edit that was abandoned.
  useEffect(() => {
    if (!open) return;
    setNote(company?.note ?? "");
    setEditing(false);
  }, [open, company]);

  // A company the mandate supplied itself is the mandate's to rewrite; one taken from the market is
  // the export's. Keyed on `source` and not on `apolloAccountId`, because that is the test the server
  // applies — and the two can disagree, which would put an Edit button on a row the API then refuses.
  //
  // Named for the invariant rather than for what this screen concludes from it, and named identically
  // to `TriageCompany.isMandateSupplied` on the server: one rule with two names across the wire is how
  // the apolloAccountId/source split above happened in the first place. Editing is that rule plus a
  // permission, which is what `canEdit` says.
  const isMandateSupplied = company !== null && company.source !== "strategy";
  const canEdit = isMandateSupplied && canWrite;

  if (company === null) {
    return (
      <Drawer open={open} onClose={onClose} wide label="Add a company">
        <AddCompanyPanel
          projectId={projectId}
          landingStatus={landingStatus}
          onClose={onClose}
          onSaved={onSaved}
        />
      </Drawer>
    );
  }

  return (
    <Drawer open={open} onClose={onClose} wide label={company.companyName}>
      <div className="relative flex-none border-b border-line-soft px-5 py-4">
        <CloseButton onClose={onClose} />

        <div className="flex items-start gap-3 pe-8">
          <CompanyLogo name={company.companyName} logo={company.logoUrl} size={44} />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="font-sans text-base font-semibold">{company.companyName}</h2>
              <DetailPill
                label={SOURCE_STYLES[company.source].label}
                className={SOURCE_STYLES[company.source].className}
              />
              <DetailPill label={stageByStatus(company.status).label} />
            </div>
            <p className="mt-1 font-mono text-[11.5px] text-text3">
              {[company.industry, company.companyCity, company.companyCountry]
                .filter(Boolean)
                .join(" · ") || "Nothing recorded about where it sits"}
            </p>
          </div>
          {canEdit && !editing && (
            <Button type="button" variant="secondary" onClick={() => setEditing(true)}>
              <Icon d={ICONS.pencil} size={14} />
              Edit
            </Button>
          )}
        </div>
      </div>

      {editing ? (
        <CompanyFactsForm
          company={company}
          isCapture={false}
          save={(parsed) => triageApi.editTriageCompany(projectId, company.id, editPayloadOf(parsed))}
          onSaved={(saved) => {
            onSaved();
            toast(`${saved.companyName} updated`);
            setEditing(false);
          }}
          onCancel={() => setEditing(false)}
        />
      ) : (
        <>
          <div className="min-h-0 flex-1 overflow-y-auto px-5">
            <DrawerSection title="About">
              <p className="text-[13px]/[1.6] text-text2">
                {company.shortDescription ?? "No description was captured for this company."}
              </p>
            </DrawerSection>

            <DrawerSection title="Scale snapshot">
              <DetailGrid>
                <DetailTile label="Revenue" value={formatMoney(company.annualRevenue)} />
                <DetailTile
                  label="Employees"
                  value={company.numEmployees?.toLocaleString() ?? null}
                />
                <DetailTile label="Founded" value={company.foundedYear?.toString() ?? null} />
                <DetailTile label="Sector" value={company.industry} />
                <DetailTile
                  label="Links"
                  full
                  value={
                    company.website || company.companyLinkedinUrl ? (
                      <span className="flex gap-1">
                        <CompanyLink
                          url={company.website}
                          icon={ICONS.globe}
                          label="website"
                          companyName={company.companyName}
                        />
                        <CompanyLink
                          url={company.companyLinkedinUrl}
                          icon={ICONS.linkedin}
                          label="LinkedIn"
                          companyName={company.companyName}
                        />
                      </span>
                    ) : null
                  }
                />
              </DetailGrid>
            </DrawerSection>

            <DrawerSection title="In this mandate">
              <DetailGrid>
                <DetailTile label="Stage" value={stageByStatus(company.status).label} />
                <DetailTile label="Source" value={SOURCE_STYLES[company.source].label} />
                <DetailTile label="Added" value={formatInstantDate(company.addedAt)} />
                <DetailTile label="Country" value={company.companyCountry} />
              </DetailGrid>
              {!isMandateSupplied && (
                <p className="mt-3 font-mono text-[11px]/[1.6] text-text3">
                  These fields come from the market export and are refreshed by it, so they are not
                  editable here. Your note below is yours.
                </p>
              )}
            </DrawerSection>

            <DrawerSection
              title="Note"
              action={
                canWrite &&
                note !== (company.note ?? "") && (
                  <button
                    type="button"
                    onClick={() => saveNote.mutate(note)}
                    disabled={saveNote.isPending}
                    className="font-mono text-[11px] font-semibold uppercase tracking-[0.06em] text-amber transition hover:underline disabled:opacity-50"
                  >
                    Save
                  </button>
                )
              }
            >
              <TextArea
                value={note}
                onChange={(event) => setNote(event.target.value)}
                readOnly={!canWrite}
                rows={3}
                aria-label="Note on this company"
                placeholder="Your own remark on this company, for this mandate…"
              />
            </DrawerSection>
          </div>

          {canWrite && (
            <div className="flex flex-none flex-wrap items-center gap-2 border-t border-line-soft px-5 py-3">
              {MOVES[company.status].map((move) => (
                <Button
                  key={move.status}
                  type="button"
                  variant="secondary"
                  onClick={() => onMove(company, move.status)}
                >
                  <Icon d={move.icon} size={14} />
                  {move.label}
                </Button>
              ))}
              <Button
                type="button"
                variant="secondary"
                className="ms-auto text-red"
                onClick={() => onDelete(company)}
              >
                Remove
              </Button>
            </div>
          )}
        </>
      )}
    </Drawer>
  );
}

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
 */
function AddCompanyPanel({
  projectId,
  landingStatus,
  onClose,
  onSaved,
}: {
  projectId: string;
  landingStatus: TriageCompanyStatus;
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

  const take = useMutation({
    mutationFn: (picked: CompanySuggestion) =>
      triageApi.addMarketCompany(projectId, picked.apolloAccountId, {
        status: landingStatus,
        note: note.trim() || undefined,
      }),
    onSuccess: (saved) => {
      onSaved();
      // The endpoint answers with the row the mandate already had when it had one, at whatever stage
      // it had reached. Saying "added" over a company that was declined months ago would be a lie the
      // grid then confirms by not showing it.
      toast(
        saved.status === landingStatus
          ? `${saved.companyName} added`
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
        <CloseButton onClose={onClose} />
        <h2 className="font-sans text-base font-semibold">Add a company</h2>
        <p className="mt-1 pe-8 font-mono text-[11.5px] text-text3">{heading}</p>
      </div>

      {draft.step === "byHand" && (
        <CompanyFactsForm
          company={null}
          seedName={draft.name}
          isCapture
          save={(parsed) =>
            triageApi.captureCompany(projectId, capturePayloadOf(parsed, landingStatus))
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
                Type at least {MIN_SEARCH_LENGTH} characters to search the company universe, or add
                a company it does not carry.
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
                {/* A read that failed is not a market with nothing in it — offering "add it by hand"
                    over a 500 would file a company the universe holds as one it does not. */}
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
                {/* Seeded from what is typed rather than from what was last searched: the two differ
                    for a quarter of a second, and the name the consultant sees is the one they typed. */}
                {!matches.isError && (
                  <li>
                    <button
                      type="button"
                      onClick={() => setDraft({ step: "byHand", name: query.trim() })}
                      className="flex w-full items-center gap-1.5 px-3 py-2.5 text-left font-mono text-[11.5px] text-amber transition hover:bg-panel2"
                    >
                      <Icon d={ICONS.plus} size={12} className="flex-none" />
                      Not here — add “{query.trim()}” as a new company
                    </button>
                  </li>
                )}
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
              <span className="min-w-0 flex-1">
                <span className="block truncate font-sans text-[13px] font-semibold text-text">
                  {draft.company.companyName}
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

            <p className="mt-3 font-mono text-[11px]/[1.6] text-text3">
              Its figures come from the market export and are refreshed by it, so there is nothing to
              fill in. It lands in {stageByStatus(landingStatus).label}.
            </p>

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
            <Button
              type="button"
              variant="secondary"
              onClick={onClose}
              disabled={take.isPending}
            >
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

function CloseButton({ onClose }: { onClose: () => void }) {
  return (
    <button
      type="button"
      onClick={onClose}
      aria-label="Close"
      className="absolute end-3.5 top-3.5 rounded-md p-1.5 text-text3 transition hover:bg-panel2 hover:text-text"
    >
      <Icon d={ICONS.close} size={16} />
    </button>
  );
}

/** The muted context line under a market company: whichever of sector and location it has. */
function marketMetaOf(company: CompanySuggestion): string {
  const location = [company.companyCity, company.companyCountry].filter(Boolean).join(", ");
  return [company.industry, location].filter(Boolean).join(" · ");
}
