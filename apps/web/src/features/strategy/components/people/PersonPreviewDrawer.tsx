import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Icon } from "../../../../components/layout/Icon";
import { Button } from "../../../../components/ui";
import { Avatar } from "../../../../components/ui/Avatar";
import { CollapsibleSection } from "../../../../components/ui/CollapsibleSection";
import { CompanyLinks } from "../../../../components/ui/CompanyLink";
import { CompanyLogo } from "../../../../components/ui/CompanyLogo";
import { DetailGrid, DetailPill, DetailTile } from "../../../../components/ui/DetailList";
import { Drawer } from "../../../../components/ui/Drawer";
import { PanelCloseButton } from "../../../../components/ui/PanelCloseButton";
import { cn } from "../../../../lib/cn";
import * as candidatesApi from "../../../candidates/api/candidatesApi";
import { CareerTimeline } from "../../../candidates/components/CareerTimeline";
import {
  EducationList,
  FoldAllButton,
  PillRow,
  ProfileItemList,
  type ProfileListItem,
} from "../../../candidates/components/ProfileParts";
import { CANDIDATE_SOURCE_STYLES } from "../../../candidates/lib/candidateVocabulary";
import { careerSummary } from "../../../candidates/lib/careerTimeline";
import { useProfileSections, type ProfileSection } from "../../../candidates/lib/useProfileSections";
import * as contactLookupApi from "../../../contactlookup/api/contactLookupApi";
import { ContactPanel } from "../../../contactlookup/components/ContactPanel";
import type { TriageCompanyStatus } from "../../../triage/api/types";
import { TRIAGE_STAGES } from "../../../triage/lib/triageStages";
import type { PersonDetails, PersonResult } from "../../api/types";
import { PersonLinks } from "./PersonLinks";

/**
 * A search hit read in the candidate drawer's own shape, from the page already bought, so opening it
 * costs nothing. Everything ContactOut sent is shown, each part in its own fold, and a fold with
 * nothing behind it is not drawn — a sparse profile, or one kept before the whole record was, is
 * simply shorter. Contacts are the one thing a search does not buy: once the person is in the mandate
 * the Contact fold is the candidate drawer's own, Find email and Find phone included.
 */
export function PersonPreviewDrawer({
  projectId,
  person,
  onClose,
  onAdd,
  adding,
}: {
  projectId: string;
  person: PersonResult | null;
  onClose: () => void;
  onAdd: (person: PersonResult, status: TriageCompanyStatus) => void;
  adding: boolean;
}) {
  return (
    <Drawer open={person !== null} onClose={onClose} wide label={person?.fullName ?? "Person"}>
      {person && (
        <PersonProfile projectId={projectId} person={person} onClose={onClose} onAdd={onAdd} adding={adding} />
      )}
    </Drawer>
  );
}

function PersonProfile({
  projectId,
  person,
  onClose,
  onAdd,
  adding,
}: {
  projectId: string;
  person: PersonResult;
  onClose: () => void;
  onAdd: (person: PersonResult, status: TriageCompanyStatus) => void;
  adding: boolean;
}) {
  const sections = useProfileSections();
  const name = person.fullName ?? person.linkedinSlug;
  const source = CANDIDATE_SOURCE_STYLES.people_search;
  const details = person.details;
  const company = details?.company ?? null;
  const headline = details?.headline && details.headline !== person.title ? details.headline : null;
  const fold = (id: ProfileSection): Fold => ({ id, open: sections.isOpen(id), onToggle: () => sections.toggle(id) });

  const companyFacts = company
    ? presentFacts([
        ["Industry", company.industry],
        ["Size", company.size],
        ["Headquarters", company.headquarter],
        ["Country", company.country],
        ["Founded", company.foundedYear?.toString() ?? null],
        ["Revenue", company.revenue],
      ])
    : [];
  const profileFacts = details
    ? presentFacts([
        ["Industry", details.industry],
        ["Job function", details.jobFunction],
        ["Seniority", details.seniority],
        ["Work status", details.workStatus],
        ["Followers", details.followers?.toLocaleString() ?? null],
      ])
    : [];
  const hasCompanyFold =
    person.companyName !== null &&
    (companyFacts.length > 0 || Boolean(company?.overview) || (company?.specialties.length ?? 0) > 0);

  return (
    <>
      <div className="relative flex-none border-b border-u-border px-5 py-4">
        <PanelCloseButton onClose={onClose} />
        <div className="flex items-start gap-3 pe-8">
          <Avatar
            id={person.linkedinSlug}
            name={name}
            src={person.photoUrl}
            className="size-[44px] rounded-[10px] border border-u-border-strong text-sm"
          />
          <div className="min-w-0 flex-1">
            <span className="flex items-center gap-1.5">
              <h2 className="font-sans text-base font-semibold">{name}</h2>
              <PersonLinks person={person} />
            </span>
            <p className="mt-0.5 font-mono text-[12.5px] text-u-text2">{person.title ?? "—"}</p>
            {headline && <p className="mt-0.5 text-[12.5px] text-u-text2">{headline}</p>}
            <p className="mt-1 font-mono text-[11.5px] text-u-text3">
              {[person.companyName, person.location].filter(Boolean).join(" · ") || "No employer or location recorded"}
            </p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <DetailPill label={source.label} className={source.className} />
              {person.held && <DetailPill label="In mandate" className="bg-u-accent-tint text-u-accent" />}
              {details?.updatedAt && (
                <span className="font-mono text-[11px] text-u-text3">
                  Profile updated {details.updatedAt.slice(0, 10)}
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-5">
        <div className="-mb-1 flex justify-end gap-3 pt-2.5">
          <FoldAllButton label="Expand all" onClick={() => sections.setAll(true)} />
          <FoldAllButton label="Collapse all" onClick={() => sections.setAll(false)} />
        </div>

        {person.companyName && (
          <div className="flex items-center gap-2.5 border-b border-u-border py-3.5">
            <CompanyLogo name={person.companyName} logo={person.companyLogoUrl} size={28} />
            <span className="min-w-0 flex-1 truncate font-sans text-[13px] font-medium text-u-text">
              {person.companyName}
            </span>
            <CompanyLinks
              website={company?.website ?? null}
              linkedinUrl={person.companyLinkedinUrl}
              companyName={person.companyName}
            />
          </div>
        )}

        {hasCompanyFold && (
          <CollapsibleSection {...fold("company")} title="Company" summary={company?.industry ?? null}>
            {companyFacts.length > 0 && <FactGrid facts={companyFacts} />}
            {company?.overview && (
              <p className="mt-3 whitespace-pre-line text-[13px]/[1.6] text-u-text2">{company.overview}</p>
            )}
            {company && company.specialties.length > 0 && <PillRow label="Specialties" values={company.specialties} />}
          </CollapsibleSection>
        )}

        {person.about && (
          <CollapsibleSection {...fold("summary")} title="Summary" summary={person.about.slice(0, 100)}>
            <p className="whitespace-pre-line text-[13px]/[1.6] text-u-text2">{person.about}</p>
          </CollapsibleSection>
        )}

        {person.career.length > 0 && (
          <CollapsibleSection
            {...fold("experience")}
            title="Experience"
            count={person.career.length}
            summary={careerSummary(person.career)}
          >
            <CareerTimeline career={person.career} />
          </CollapsibleSection>
        )}

        {person.education.length > 0 && (
          <CollapsibleSection
            {...fold("education")}
            title="Education"
            count={person.education.length}
            summary={person.education[0].school ?? person.education[0].degree}
          >
            <EducationList education={person.education} />
          </CollapsibleSection>
        )}

        {profileFacts.length > 0 && (
          <CollapsibleSection
            {...fold("profile")}
            title="Profile"
            summary={profileFacts
              .map(([, value]) => value)
              .slice(0, 3)
              .join(", ")}
          >
            <FactGrid facts={profileFacts} />
          </CollapsibleSection>
        )}

        {(person.languages.length > 0 || person.skills.length > 0) && (
          <CollapsibleSection
            {...fold("background")}
            title="Skills & languages"
            summary={[...person.languages, ...person.skills].slice(0, 3).join(", ")}
          >
            {person.languages.length > 0 && <PillRow label="Languages" values={person.languages} />}
            {person.skills.length > 0 && <PillRow label="Skills" values={person.skills} />}
          </CollapsibleSection>
        )}

        <ItemsSection fold={fold("certifications")} title="Certifications" items={details?.certifications} />
        <ItemsSection fold={fold("publications")} title="Publications" items={details?.publications} />
        <ItemsSection fold={fold("projects")} title="Projects" items={details?.projects} />
        <ItemsSection fold={fold("volunteering")} title="Volunteering" items={details?.volunteering} />

        <ContactSection projectId={projectId} person={person} fold={fold("contact")} />
      </div>

      <div className="flex flex-none flex-wrap items-center gap-2 border-t border-u-border px-5 py-3">
        {person.held ? (
          <p className="font-mono text-[12px] text-u-text3">Already mapped in this mandate.</p>
        ) : (
          TRIAGE_STAGES.map((stage) => (
            <Button
              key={stage.status}
              type="button"
              variant="secondary"
              className={cn(stage.status === "declined" && "text-u-offlimits")}
              disabled={adding}
              onClick={() => onAdd(person, stage.status)}
            >
              <Icon d={stage.icon} size={14} />
              {stage.label}
            </Button>
          ))
        )}
      </div>
    </>
  );
}

interface Fold {
  id: ProfileSection;
  open: boolean;
  onToggle: () => void;
}

function ItemsSection({
  fold,
  title,
  items,
}: {
  fold: Fold;
  title: string;
  items: readonly ProfileListItem[] | undefined;
}) {
  if (!items || items.length === 0) return null;
  return (
    <CollapsibleSection {...fold} title={title} count={items.length} summary={items[0].title}>
      <ProfileItemList items={items} />
    </CollapsibleSection>
  );
}

/**
 * The candidate drawer's own Contact fold once the person is in the mandate, so a found email lands on
 * their row and is never bought twice. Before that, only what ContactOut says it holds — free flags.
 */
function ContactSection({ projectId, person, fold }: { projectId: string; person: PersonResult; fold: Fold }) {
  const queryClient = useQueryClient();
  const candidateId = person.candidateId;
  const candidate = useQuery({
    queryKey: candidatesApi.CANDIDATE_KEY(projectId, candidateId ?? ""),
    queryFn: ({ signal }) => candidatesApi.getCandidate(projectId, candidateId ?? "", signal),
    enabled: candidateId !== null,
  });
  const lookupConfig = useQuery({
    queryKey: contactLookupApi.CONTACT_LOOKUP_CONFIG_KEY,
    queryFn: ({ signal }) => contactLookupApi.getContactLookupConfig(signal),
    staleTime: Infinity,
    enabled: candidateId !== null,
  });

  const availability = availabilityLine(person.details);
  if (candidateId === null && !availability) return null;

  let body: ReactNode;
  if (candidateId === null) {
    body = <p className="text-[13px]/[1.6] text-u-text2">{availability} Add them to the mandate to find it.</p>;
  } else if (candidate.data) {
    body = (
      <ContactPanel
        projectId={projectId}
        candidate={candidate.data}
        canWrite
        lookupOffered={lookupConfig.data?.enabled === true}
        onSaved={(saved) => queryClient.setQueryData(candidatesApi.CANDIDATE_KEY(projectId, saved.id), saved)}
      />
    );
  } else {
    body = (
      <p className="font-mono text-[12px] text-u-text3">
        {candidate.isError ? "Their contacts could not be loaded." : "Loading contacts…"}
      </p>
    );
  }

  return (
    <CollapsibleSection {...fold} title="Contact" summary={candidateId === null ? availability : null}>
      {body}
    </CollapsibleSection>
  );
}

function availabilityLine(details: PersonDetails | null): string | null {
  const flags = details?.contactAvailability;
  if (!flags) return null;
  const held = [
    flags.workEmail && "a work email",
    flags.personalEmail && "a personal email",
    flags.phone && "a phone number",
  ].filter((kind): kind is string => Boolean(kind));
  if (held.length === 0) return null;
  const listed = held.length === 1 ? held[0] : `${held.slice(0, -1).join(", ")} and ${held.at(-1)}`;
  return `ContactOut has ${listed} for this person.`;
}

function FactGrid({ facts }: { facts: [string, string][] }) {
  return (
    <DetailGrid>
      {facts.map(([label, value]) => (
        <DetailTile key={label} label={label} value={value} />
      ))}
    </DetailGrid>
  );
}

/** Only the facts ContactOut stated: a grid of em dashes says nothing a missing tile does not. */
function presentFacts(facts: [string, string | null][]): [string, string][] {
  return facts.filter((fact): fact is [string, string] => fact[1] !== null && fact[1] !== "");
}
