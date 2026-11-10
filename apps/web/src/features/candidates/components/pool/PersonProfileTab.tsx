import { CollapsibleSection } from "../../../../components/ui/CollapsibleSection";
import { DetailGrid, DetailTile } from "../../../../components/ui/DetailList";
import { formatInstantDate } from "../../../../lib/format";
import type { DocumentScope } from "../../api/documentsApi";
import type { PersonDocument, PersonDocumentVersion, PersonRecord } from "../../api/types";
import { CANDIDATE_SOURCE_STYLES, candidateGenderLabel } from "../../lib/candidateVocabulary";
import { compensationFoldSummary, documentsFoldSummary } from "../../lib/profileFolds";
import type { PersonDocuments } from "../../lib/usePersonDocuments";
import type { ProfileSection, useProfileSections } from "../../lib/useProfileSections";
import { CareerTimeline } from "../CareerTimeline";
import { CompensationSummary } from "../CompensationSummary";
import { DocumentsPanel } from "../documents/DocumentsPanel";
import { ClampedText } from "../ProfileParts";

/**
 * The Profile tab: the profile every position shares, read here and edited from a position's own drawer — the summary
 * open, the rest folded, as the executive drawer's, remembered per viewer in the same place.
 */
export function PersonProfileTab({
  person,
  sections,
  documents,
  documentScope,
  onPreview,
}: {
  person: PersonRecord;
  sections: ReturnType<typeof useProfileSections>;
  documents: PersonDocuments;
  documentScope: DocumentScope;
  onPreview: (document: PersonDocument, version: PersonDocumentVersion) => void;
}) {
  const fold = (id: ProfileSection) => ({ id, open: sections.isOpen(id), onToggle: () => sections.toggle(id) });
  const background = [person.seniority, person.nationality, candidateGenderLabel(person.gender),
    person.yearsExperience != null ? `${person.yearsExperience} years` : null].filter(Boolean);
  const current = person.career[0];
  const documentList = documents.documents.data;

  return (
    <div className="flex flex-col gap-1">
      <CollapsibleSection
        {...fold("summary")}
        title="Summary"
        summary={person.summary ? truncate(person.summary, 90) : "Not written yet"}
      >
        <div className="pb-3">
          <ClampedText
            text={person.summary ?? "No summary written yet."}
            className="text-[13px]/[1.6] text-u-text2"
          />
        </div>
      </CollapsibleSection>
      <CollapsibleSection
        {...fold("experience")}
        title="Experience"
        count={person.career.length > 0 ? person.career.length : undefined}
        summary={current ? [current.title, current.company].filter(Boolean).join(" at ") : null}
      >
        <div className="pb-3">
          <CareerTimeline career={person.career} />
        </div>
      </CollapsibleSection>
      <CollapsibleSection
        {...fold("background")}
        title="Background"
        summary={background.length > 0 ? background.join(" · ") : "Nothing on file"}
      >
        <div className="pb-3">
          <DetailGrid>
            <DetailTile label="Seniority" value={person.seniority} />
            <DetailTile label="Nationality" value={person.nationality} />
            <DetailTile label="Gender" value={candidateGenderLabel(person.gender)} />
            <DetailTile
              label="Experience"
              value={person.yearsExperience != null ? `${person.yearsExperience} years` : null}
            />
          </DetailGrid>
        </div>
      </CollapsibleSection>
      <CollapsibleSection
        {...fold("compensation")}
        title="Compensation"
        summary={compensationFoldSummary(person.compensation) ?? "Nothing on file"}
      >
        <div className="pb-3">
          <CompensationSummary compensation={person.compensation} />
          <p className="mt-2 text-note text-u-text3">
            One package on file for this person, read by every position they are in.
          </p>
        </div>
      </CollapsibleSection>
      <CollapsibleSection
        {...fold("documents")}
        title="Documents"
        count={documentList?.length || undefined}
        summary={documentsFoldSummary(documentList)}
      >
        <div className="pb-3">
          <DocumentsPanel
            scope={documentScope}
            documents={documents}
            personName={person.fullName.split(" ")[0]}
            compact
            onPreview={onPreview}
          />
        </div>
      </CollapsibleSection>

      <p className="mt-3 font-mono text-[11px] text-u-text3">
        Added to the candidates {formatInstantDate(person.addedAt)} by {person.addedByName ?? "someone"} ·{" "}
        {CANDIDATE_SOURCE_STYLES[person.source]?.label ?? person.source}
      </p>
    </div>
  );
}

function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max - 1).trimEnd()}…` : text;
}
