import { useMutation, useQuery } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Drawer } from "../../../../components/ui/Drawer";
import { PanelCloseButton } from "../../../../components/ui/PanelCloseButton";
import { Button, DrawerSkeleton, Select, useToast } from "../../../../components/ui";
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
import { PositionsSection, type SetNextActionOn } from "../PersonPositions";
import { PersonTagsSection } from "../PersonTagsSection";
import { DrawerTabBar } from "../DrawerTabBar";
import { DetailPill } from "../../../../components/ui/DetailList";
import { WORKSPACE_TABS, type DrawerTab } from "../../lib/drawerTabs";
import { NextActionModal, type NextActionTarget } from "../../../tasks/components/NextActionModal";
import { HeaderProfileLink } from "../ProfileParts";
import { PersonAvatar } from "./PersonAvatar";
import { PersonNotesTab } from "./PersonNotesTab";
import { PersonProfileTab } from "./PersonProfileTab";
import { PersonTimelineTab } from "./PersonTimelineTab";
import { PersonContactTab } from "./PersonContactTab";
import { AddToPositionDialog } from "./AddToPositionDialog";

const TAB_ID_PREFIX = "person-drawer";

const DEFAULT_REASON = "Marked from the candidate drawer.";

/**
 * A workspace person on the Candidates page, laid out as a position's executive drawer is — the same
 * header, the same tabs in the same order — less what only a position holds (status, assessment,
 * outreach). The team's own facts sit in the header: owner, do not contact and tags. A next action
 * belongs to one position, so it is on that position's card under Positions. Staff-only, like the
 * page it opens from.
 */
export function PersonDrawer({
  personId,
  tab,
  onTabChange,
  onClose,
}: {
  personId: string | null;
  tab: DrawerTab;
  onTabChange: (tab: DrawerTab) => void;
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
  tab: DrawerTab;
  onTabChange: (tab: DrawerTab) => void;
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
  const [nextAction, setNextAction] = useState<NextActionTarget | null>(null);
  const setNextActionOn: SetNextActionOn = (position, taskId) =>
    setNextAction({
      projectId: position.projectId,
      candidateId: position.candidateId,
      personId: person.personId,
      executiveName: person.fullName,
      taskId,
    });
  const startNote = () => {
    onTabChange("notes");
    window.requestAnimationFrame(() =>
      document.getElementById(`${TAB_ID_PREFIX}-panel-notes`)?.querySelector("textarea")?.focus(),
    );
  };

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
      <header className="relative flex-none border-b border-u-border px-5 pt-4">
        <PanelCloseButton onClose={onClose} />
        <div className="flex items-start gap-3 pe-8">
          <PersonAvatar person={person} size="xl" className="size-11 rounded-u-card border border-u-border-strong text-sm" />
          <div className="min-w-0 flex-1">
            <span className="flex items-center gap-1.5">
              <h2 className="truncate font-sans text-base font-semibold text-u-text">{person.fullName}</h2>
              <HeaderProfileLink linkedinUrl={person.linkedinUrl} />
            </span>
            <p className="mt-0.5 truncate font-mono text-[12.5px] text-u-text2">{person.title ?? "—"}</p>
            <p className="mt-1 truncate font-mono text-[11.5px] text-u-text3">{context || "No employer or location recorded"}</p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {person.seniority && <DetailPill label={person.seniority} />}
              <label className="flex items-center gap-1.5">
                <span className="type-label text-u-text3">Owner</span>
                <Select
                  aria-label="Owner"
                  value={person.ownerUserId ?? ""}
                  disabled={owning.isPending}
                  onChange={(event) => owning.mutate(event.target.value || null)}
                  className="h-7 w-auto py-0 text-[12.5px]"
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
                  "flex h-7 items-center gap-1.5 rounded-full border px-2.5 font-mono text-[11px] font-semibold",
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
          </div>
        </div>
        <PersonTagsSection person={person} className="mt-2.5 ps-14" />

        {doNotContact && (
          <div role="note" className="mt-3 rounded-u-card border border-u-offlimits/30 bg-u-offlimits-tint px-3 py-2 text-[12.5px] text-u-text2">
            <b className="font-semibold text-u-offlimits">Do not contact.</b>
            {doNotContact.reason && ` ${doNotContact.reason}`}
            <span className="mt-0.5 block font-mono text-[11px] text-u-text3">
              Set by {doNotContact.setByName ?? "someone"}
              {doNotContact.setAt && ` · ${new Date(doNotContact.setAt).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })}`}
            </span>
          </div>
        )}

        <DrawerTabBar
          idPrefix={TAB_ID_PREFIX}
          tabs={WORKSPACE_TABS}
          value={tab}
          onChange={onTabChange}
          counts={{
            notes: notes.data?.length,
            documents: documents.documents.data?.length,
            positions: person.positions.length,
          }}
        />
      </header>

      <div key={tab} className="min-h-0 flex-1 overflow-y-auto px-5" {...tabPanelProps(TAB_ID_PREFIX, tab)}>
        {tab === "career" && (
          <div className="py-4">
            <PersonProfileTab person={person} />
          </div>
        )}
        {tab === "notes" && (
          <div className="py-4">
            <PersonNotesTab person={person} notes={notes} />
          </div>
        )}
        {tab === "contact" && (
          <div className="py-4">
            <PersonContactTab person={person} />
          </div>
        )}
        {tab === "documents" && (
          <div className="py-4">
            <DocumentsPanel
              scope={documentScope}
              documents={documents}
              personName={person.fullName.split(" ")[0]}
              onPreview={openPreview}
            />
          </div>
        )}
        {tab === "activity" && (
          <div className="py-4">
            <PersonTimelineTab personId={person.personId} />
          </div>
        )}
        {tab === "positions" && (
          <PositionsSection
            title="Positions"
            positions={person.positions}
            onSetNextAction={setNextActionOn}
            action={
              <button
                type="button"
                onClick={() => setAdding(true)}
                className="flex items-center gap-1 text-note font-semibold text-u-accent hover:underline"
              >
                <Icon d={ICONS.plus} size={11} />
                Add to position
              </button>
            }
            empty={
              <p className="text-note text-u-text3">
                Not in any position right now. Everything on file stays for the next search.
              </p>
            }
          />
        )}
      </div>

      <footer className="flex flex-none items-center gap-2 border-t border-u-border px-5 py-3">
        <Button variant="secondary" className="ms-auto" onClick={startNote}>
          <Icon d={ICONS.plus} size={14} />
          Add note
        </Button>
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </footer>

      <NextActionModal target={nextAction} onClose={() => setNextAction(null)} />

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
