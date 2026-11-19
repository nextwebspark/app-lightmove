import { CollapsibleSection } from "../../../components/ui/CollapsibleSection";
import { DetailGrid, DetailTile } from "../../../components/ui/DetailList";
import type { PersonResearchDetails } from "../api/types";
import type { ProfileSection } from "../lib/useProfileSections";
import { ProfileItemList, type ProfileListItem } from "./ProfileParts";

export interface ProfileFold {
  id: ProfileSection;
  open: boolean;
  onToggle: () => void;
}

/** What research said of a person beyond the profile: their profile facts, then each kind of listed item. */
export function ResearchedProfileSections({
  details,
  fold,
}: {
  details: PersonResearchDetails | null | undefined;
  fold: (id: ProfileSection) => ProfileFold;
}) {
  if (!details) return null;
  const profileFacts = researchedProfileFacts(details);
  return (
    <>
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
      <ResearchedItemsSection fold={fold("certifications")} title="Certifications" items={details.certifications} />
      <ResearchedItemsSection fold={fold("publications")} title="Publications" items={details.publications} />
      <ResearchedItemsSection fold={fold("projects")} title="Projects" items={details.projects} />
      <ResearchedItemsSection fold={fold("volunteering")} title="Volunteering" items={details.volunteering} />
    </>
  );
}

export function researchedProfileFacts(details: PersonResearchDetails): [string, string][] {
  return presentFacts([
    ["Industry", details.industry],
    ["Job function", details.jobFunction],
    ["Seniority", details.seniority],
    ["Work status", details.workStatus],
    ["Followers", details.followers?.toLocaleString() ?? null],
    ["Connections", details.connections?.toLocaleString() ?? null],
  ]);
}

export function ResearchedItemsSection({
  fold,
  title,
  items,
}: {
  fold: ProfileFold;
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

export function FactGrid({ facts }: { facts: [string, string][] }) {
  return (
    <DetailGrid>
      {facts.map(([label, value]) => (
        <DetailTile key={label} label={label} value={value} />
      ))}
    </DetailGrid>
  );
}

/** Only the facts research stated: a grid of em dashes says nothing a missing tile does not. */
export function presentFacts(facts: [string, string | null][]): [string, string][] {
  return facts.filter((fact): fact is [string, string] => fact[1] !== null && fact[1] !== "");
}
