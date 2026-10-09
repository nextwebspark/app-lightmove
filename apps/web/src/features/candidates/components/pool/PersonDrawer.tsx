import { useMutation, useQuery } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Drawer } from "../../../../components/ui/Drawer";
import { PanelCloseButton } from "../../../../components/ui/PanelCloseButton";
import { Button, DrawerSkeleton, Select, useToast } from "../../../../components/ui";
import { TabList } from "../../../../components/ui/TabList";
import { tabPanelProps } from "../../../../components/ui/tabPanelProps";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { DocumentScope } from "../../api/documentsApi";
import type { PersonDocument, PersonDocumentVersion, PersonRecord } from "../../api/types";
import { usePersonDocuments } from "../../lib/usePersonDocuments";
import { usePersonRecordUpdate } from "../../lib/usePersonRecordUpdate";
import { usePoolLookups } from "../../lib/usePoolLookups";
import { DocumentPreviewSheet, type PreviewTarget } from "../documents/DocumentPreviewSheet";
import { DocumentsPanel } from "../documents/DocumentsPanel";
import { PrimaryCvChip } from "../documents/PrimaryCvChip";
import { PositionsSection } from "../PersonPositions";
import { TabSectionHeading } from "../PersonSections";
import { PersonTagsSection } from "../PersonTagsSection";
import { HeaderProfileLink } from "../ProfileParts";
import { PersonAvatar } from "./PersonAvatar";
import { PersonNotesTab } from "./PersonNotesTab";
import { PersonProfileTab } from "./PersonProfileTab";
import { PersonTimelineTab } from "./PersonTimelineTab";
import { PersonContactTab } from "./PersonContactTab";
import { AddToPositionDialog } from "./AddToPositionDialog";

export type PersonDrawerTab = "profile" | "contact" | "records" | "timeline";

const TABS: { value: PersonDrawerTab; label: string }[] = [
  { value: "profile", label: "Profile" },
  { value: "contact", label: "Contact" },
  { value: "records", label: "Records" },
  { value: "timeline", label: "Timeline" },
];

const DEFAULT_REASON = "Marked from the candidate drawer.";

/**
 * A workspace person on the Candidates page, as `Candidates.dc.html` draws them: who they are and the
 * team's own facts about them — owner and do not contact in the header; tags, notes, documents and
 * positions under Records — laid out as a position's executive drawer is. Staff-only, like the
 * page it opens from.
 */
export function PersonDrawer({
  personId,
  tab,
  onTabChange,
  onClose,
}: {
  personId: string | null;
  tab: PersonDrawerTab;
  onTabChange: (tab: PersonDrawerTab) => void;
  onClose: () => void;
}) {
  const record = useQuery({
    queryKey: poolApi.PERSON_RECORD_KEY(personId ?? ""),
    queryFn: ({ signal }) => poolApi.getPerson(personId ?? "", signal),
    enabled: personId !== null,
  });

  return (
    <Drawer open={personId !== null} onClose={onClose} label={record.data?.fullName ?? "Candidate"} wide>
      <div className="flex h-full flex-col">
        {record.isError ? (
          <div className="relative p-5">
            <PanelCloseButton onClose={onClose} />
            <p className="mt-6 text-[13px] text-u-text3">{messageFor(record.error)}</p>
          </div>
        ) : !record.data ? (
          <div className="relative">
            <PanelCloseButton onClose={onClose} />
            <DrawerSkeleton />
          </div>
        ) : (
          <PersonDrawerBody person={record.data} tab={tab} onTabChange={onTabChange} onClose={onClose} />
        )}
      </div>
    </Drawer>
  );
}

function PersonDrawerBody({
  person,
  tab,
  onTabChange,
  onClose,
}: {
  person: PersonRecord;
  tab: PersonDrawerTab;
  onTabChange: (tab: PersonDrawerTab) => void;
  onClose: () => void;
}) {
  const toast = useToast();
  const lookups = usePoolLookups();
  const notes = useQuery({
    queryKey: poolApi.POOL_NOTES_KEY(person.personId),
    queryFn: ({ signal }) => poolApi.getPoolNotes(person.personId, signal),
  });
  const documentScope: DocumentScope = { kind: "person", personId: person.personId };
  const documents = usePersonDocuments(documentScope);
  const [preview, setPreview] = useState<PreviewTarget | null>(null);
  const openPreview = (document: PersonDocument, version: PersonDocumentVersion) =>
    setPreview({ documentId: document.id, versionId: version.id });
  const closePreview = useCallback(() => setPreview(null), []);
  const changed = usePersonRecordUpdate();
  const [adding, setAdding] = useState(false);

  const owning = useMutation({
    mutationFn: (ownerUserId: string | null) => poolApi.setOwner(person.personId, ownerUserId),
    onSuccess: changed,
    onError: (error) => toast.error(messageFor(error)),
  });
  const marking = useMutation({
    mutationFn: (doNotContact: boolean) =>
      poolApi.setDoNotContact(person.personId, doNotContact, doNotContact ? DEFAULT_REASON : undefined),
    onSuccess: changed,
    onError: (error) => toast.error(messageFor(error)),
  });

  const context = [person.companyName, person.locationCity, person.locationCountry].filter(Boolean).join(" · ");
  const doNotContact = person.doNotContact;

  return (
    <>
      <header className="relative border-b border-u-border px-5 pb-3 pt-4">
        <div className="flex items-start gap-3">
          <PersonAvatar person={person} size="xl" />
          <div className="min-w-0 flex-1 pe-8">
            <div className="flex items-center gap-2">
              <h2 className="truncate text-[18px] font-semibold text-u-text">{person.fullName}</h2>
              <HeaderProfileLink linkedinUrl={person.linkedinUrl} />
            </div>
            <p className="truncate text-[13px] text-u-text2">{person.title ?? "—"}</p>
            <p className="truncate font-mono text-[11.5px] text-u-text3">{context || "No employer or location recorded"}</p>
          </div>
        </div>
        <PanelCloseButton onClose={onClose} />

        <div className="mt-3 flex flex-wrap items-center gap-2">
          {person.seniority && (
            <span className="rounded-[4px] bg-u-raised px-1.5 py-px font-mono text-[10.5px] font-semibold text-u-text2">
              {person.seniority}
            </span>
          )}
          <label className="flex items-center gap-1.5">
            <span className="type-label text-u-text3">Owner</span>
            <Select
              aria-label="Owner"
              value={person.ownerUserId ?? ""}
              disabled={owning.isPending}
              onChange={(event) => owning.mutate(event.target.value || null)}
              className="w-auto py-1 text-[12.5px]"
            >
              <option value="">Nobody</option>
              {lookups.staff.map((member) => (
                <option key={member.userId} value={member.userId}>
                  {member.fullName}
                </option>
              ))}
            </Select>
          </label>
          <button
            type="button"
            aria-pressed={doNotContact !== null}
            disabled={marking.isPending}
            onClick={() => marking.mutate(doNotContact === null)}
            title={
              doNotContact
                ? "Clear do not contact"
                : "Mark do not contact — shown on every position and turns off contact lookups"
            }
            className={cn(
              "flex items-center gap-1.5 rounded-full border px-2.5 py-1 font-mono text-[11px] font-semibold",
              doNotContact
                ? "border-transparent bg-u-offlimits-tint text-u-offlimits"
                : "border-u-border-strong text-u-text3 hover:text-u-text2",
            )}
          >
            <Icon d={ICONS.ban} size={12} />
            Do not contact
          </button>
          <PrimaryCvChip documents={documents} onPreview={openPreview} />
        </div>

        {doNotContact && (
          <div role="note" className="mt-3 rounded-[8px] bg-u-offlimits-tint px-3 py-2 text-[12.5px] text-u-offlimits">
            <p className="font-semibold">Do not contact</p>
            {doNotContact.reason && <p className="mt-0.5">{doNotContact.reason}</p>}
            <p className="mt-0.5 font-mono text-[11px] opacity-80">
              Set by {doNotContact.setByName ?? "someone"}
              {doNotContact.setAt && ` · ${new Date(doNotContact.setAt).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })}`}
            </p>
          </div>
        )}


        <TabList
          label="Candidate sections"
          idPrefix="person-drawer"
          className="-mb-3 mt-3"
          value={tab}
          onChange={onTabChange}
          tabs={TABS}
        />
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4" {...tabPanelProps("person-drawer", tab)}>
        {tab === "profile" && <PersonProfileTab person={person} />}
        {tab === "contact" && <PersonContactTab person={person} />}
        {tab === "records" && (
          <>
            <PersonTagsSection person={person} />
            <section aria-label="Notes" className="border-t border-u-border py-4">
              <TabSectionHeading title="Notes" />
              <PersonNotesTab person={person} notes={notes} />
            </section>
            <section aria-label="Documents" className="border-t border-u-border py-4">
              <TabSectionHeading title="Documents" />
              <DocumentsPanel
                scope={documentScope}
                documents={documents}
                personName={person.fullName.split(" ")[0]}
                onPreview={openPreview}
              />
            </section>
            <PositionsSection
              title="Positions"
              positions={person.positions}
              action={
                <button
                  type="button"
                  onClick={() => setAdding(true)}
                  className="flex items-center gap-1 font-mono text-[11.5px] font-semibold text-u-accent hover:underline"
                >
                  <Icon d={ICONS.plus} size={11} />
                  Add to position
                </button>
              }
              empty={
                <p className="text-[13px] text-u-text3">
                  Not in any position right now. Everything on file stays for the next search.
                </p>
              }
            />
          </>
        )}
        {tab === "timeline" && <PersonTimelineTab personId={person.personId} />}
      </div>

      <footer className="flex justify-end border-t border-u-border px-5 py-3">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </footer>

      <DocumentPreviewSheet
        scope={documentScope}
        documents={documents}
        target={preview}
        onTargetChange={setPreview}
        onClose={closePreview}
      />

      {adding && (
        <AddToPositionDialog
          open
          onClose={() => setAdding(false)}
          personIds={[person.personId]}
          targetName={person.fullName}
          alreadyInByPosition={new Map(person.positions.map((position) => [position.projectId, 1]))}
          positions={lookups.workablePositions}
        />
      )}
    </>
  );
}
