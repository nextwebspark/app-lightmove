import { useMutation } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, TextArea, useToast } from "../../../components/ui";
import { CompanyLinks } from "../../../components/ui/CompanyLink";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { DetailGrid, DetailPill, DetailTile, DrawerSection } from "../../../components/ui/DetailList";
import { Drawer, DrawerCloseButton } from "../../../components/ui/Drawer";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import type { CustomColumn } from "../../customcolumns/api/types";
import * as triageApi from "../api/triageApi";
import type { TriageCompany, TriageCompanyStatus } from "../api/types";
import { stageByStatus } from "../lib/triageStages";
import { MOVES, SOURCE_STYLES } from "../lib/triageVocabulary";
import { AddCompanyPanel } from "./AddCompanyPanel";
import { CompanyFactsForm, editPayloadOf } from "./CompanyFactsForm";
import { CompanyFactsSections } from "./CompanyFactsSections";

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
  customColumns,
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
  /** This mandate's own company columns, edited in the same save as the fields above them. */
  customColumns: readonly CustomColumn[];
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

  // A company the mandate supplied itself is the mandate's to rewrite; one carrying a market snapshot
  // is the export's. Keyed on `apolloAccountId` and not on `source`, because that is the test the
  // server applies — and the two can disagree, which would put an Edit button on a row the API then
  // refuses. They began disagreeing when a plugin capture started resolving against the universe: such
  // a row is badged `extension` but carries the export's snapshot and its id.
  //
  // Named for the invariant rather than for what this screen concludes from it, and named identically
  // to `TriageCompany.isMandateSupplied` on the server: one rule with two names across the wire is how
  // the apolloAccountId/source split above happened in the first place. Editing is that rule plus a
  // permission, which is what `canEdit` says.
  const isMandateSupplied = company !== null && company.apolloAccountId === null;
  const canEdit = isMandateSupplied && canWrite;

  if (company === null) {
    return (
      <Drawer open={open} onClose={onClose} wide label="Add a company">
        <AddCompanyPanel
          projectId={projectId}
          landingStatus={landingStatus}
          customColumns={customColumns}
          onClose={onClose}
          onSaved={onSaved}
        />
      </Drawer>
    );
  }

  return (
    <Drawer open={open} onClose={onClose} wide label={company.companyName}>
      <div className="relative flex-none border-b border-line-soft px-5 py-4">
        <DrawerCloseButton onClose={onClose} />

        <div className="flex items-start gap-3 pe-8">
          <CompanyLogo name={company.companyName} logo={company.logoUrl} size={44} />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              {/* Name and links are one wrapping item: a long name pushed them onto the pill row,
                  where a bare globe reads as another badge rather than as this company's site. */}
              <span className="flex items-center gap-1.5">
                <h2 className="font-sans text-base font-semibold">{company.companyName}</h2>
                <CompanyLinks
                  companyName={company.companyName}
                  website={company.website}
                  linkedinUrl={company.companyLinkedinUrl}
                />
              </span>
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
          customColumns={customColumns}
          save={(parsed, customFields) =>
            triageApi.editTriageCompany(projectId, company.id, {
              ...editPayloadOf(parsed),
              customFields,
            })
          }
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
            <CompanyFactsSections company={company} />

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
