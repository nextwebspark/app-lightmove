import { useMutation, useQuery } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Drawer, DrawerFooter } from "../../../../components/ui/Drawer";
import { PanelCloseButton } from "../../../../components/ui/PanelCloseButton";
import { Button, DrawerSkeleton, Select, useToast } from "../../../../components/ui";
import { tabPanelProps } from "../../../../components/ui/tabPanelProps";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { DocumentScope } from "../../api/documentsApi";
import type { PersonDocument, PersonDocumentVersion, PersonRecord } from "../../api/types";
import { isContactNote } from "../../lib/noteKinds";
import { useDataProtection } from "../../lib/useDataProtection";
import { usePersonDocuments } from "../../lib/usePersonDocuments";
import { usePersonRecordUpdate } from "../../lib/usePersonRecordUpdate";
import { usePoolLookups } from "../../lib/usePoolLookups";
import { DocumentPreviewSheet, type PreviewTarget } from "../documents/DocumentPreviewSheet";
import { PositionsSection, type SetNextActionOn } from "../PersonPositions";
import { PersonTagsSection } from "../PersonTagsSection";
import { DrawerTabBar } from "../DrawerTabBar";
import { WORKSPACE_TABS, type DrawerTab } from "../../lib/drawerTabs";
import { NextActionModal, type NextActionTarget } from "../../../tasks/components/NextActionModal";
import { PersonHeaderLayout } from "../ProfileParts";
import { PersonAvatar } from "./PersonAvatar";
import { PersonNotesTab } from "./PersonNotesTab";
import { PersonProfileTab } from "./PersonProfileTab";
import { ActivityTimeline } from "../activity/ActivityTimeline";
import { ContactsReadDialog } from "../../../contactlookup/components/ContactsReadDialog";
import { useProfileSections, type ProfileSection } from "../../lib/useProfileSections";
import { scrollToFold } from "../../lib/profileFolds";
import { AddToPositionDialog } from "./AddToPositionDialog";
import { focusNoteComposer } from "../../../notes/lib/focusNoteComposer";
import { ActionMenu, MenuItem } from "../../../../components/ui/ActionMenu";
import { useIsWorkspaceAdmin } from "../../../billing/lib/useBilling";
import { ErasePersonDialog } from "./ErasePersonDialog";
import { RestrictProcessingDialog } from "./RestrictProcessingDialog";
import { SubjectAccessExportDialog } from "./SubjectAccessExportDialog";

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
  const sections = useProfileSections();
  const [showingContacts, setShowingContacts] = useState(false);
  /** An Activity line's way back: its tab, and on Profile the fold that holds the thing, unfolded and in view. */
  const openTab = (next: DrawerTab, fold?: ProfileSection) => {
    onTabChange(next);
    if (!fold) return;
    sections.setAll(true, [fold]);
    scrollToFold(fold);
  };
  const [adding, setAdding] = useState(false);
  const [erasing, setErasing] = useState(false);
  const [exportingData, setExportingData] = useState(false);
  const [restricting, setRestricting] = useState(false);
  const isAdmin = useIsWorkspaceAdmin();
  const { offered: dataProtection } = useDataProtection();
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
    focusNoteComposer(`${TAB_ID_PREFIX}-panel-notes`);
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

  const lifting = useMutation({
    mutationFn: () => poolApi.setProcessingRestriction(person.personId, false),
    onSuccess: (record) => {
      changed(record);
      toast.success("Restriction lifted");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const doNotContact = person.doNotContact;
  const restriction = person.processingRestriction;
  const offersRestriction = dataProtection && (!restriction || restriction.mayLift);
  const offersErasure = dataProtection && isAdmin;
  const offersExport = dataProtection && isAdmin;
  const hasMenu = offersRestriction || offersExport || offersErasure;

  return (
    <>
      <header className="relative flex-none border-b border-u-border px-5 pt-4">
        <PanelCloseButton onClose={onClose} />
        {hasMenu && (
          <div className="absolute end-12 top-3.5">
            <ActionMenu label={`More for ${person.fullName}`} width={256}>
              {(close) => (
                <>
                  {!offersRestriction ? null : restriction ? (
                    <MenuItem
                      icon={ICONS.unlock}
                      onSelect={() => {
                        close();
                        lifting.mutate();
                      }}
                    >
                      Lift restriction
                    </MenuItem>
                  ) : (
                    <MenuItem
                      icon={ICONS.lock}
                      onSelect={() => {
                        close();
                        setRestricting(true);
                      }}
                    >
                      Restrict processing…
                    </MenuItem>
                  )}
                  {offersExport && (
                    <MenuItem
                      icon={ICONS.download}
                      onSelect={() => {
                        close();
                        setExportingData(true);
                      }}
                    >
                      Export personal data…
                    </MenuItem>
                  )}
                  {offersErasure && (
                    <MenuItem
                      icon={ICONS.trash}
                      tone="danger"
                      onSelect={() => {
                        close();
                        setErasing(true);
                      }}
                    >
                      Delete person permanently…
                    </MenuItem>
                  )}
                </>
              )}
            </ActionMenu>
          </div>
        )}
        <PersonHeaderLayout
          avatar={<PersonAvatar person={person} size="xl" className="size-11 rounded-u-card border border-u-border-strong text-sm" />}
          fullName={person.fullName}
          linkedinUrl={person.linkedinUrl}
          title={person.title}
          contacts={person.contacts}
          onShowContacts={() => setShowingContacts(true)}
          employer={{ name: person.companyName, logoUrl: person.companyLogoUrl }}
          location={[person.locationCity, person.locationCountry].filter(Boolean).join(" · ")}
          hasCornerMenu={hasMenu}
        >
          <div className="mt-2 flex flex-wrap items-center gap-2">
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
            {restriction && (
              <span
                title="No AI, research, outreach or new positions until the restriction is lifted"
                className="flex h-7 items-center gap-1.5 rounded-full bg-u-signal-tint px-2.5 font-mono text-meta font-semibold text-u-signal"
              >
                <Icon d={ICONS.lock} size={12} />
                Processing restricted
              </span>
            )}
          </div>
          <PersonTagsSection person={person} className="mt-2.5" />
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
          {restriction && (
            <div
              role="note"
              aria-label="Processing restricted"
              className="mt-3 flex items-start gap-2.5 rounded-u-card border border-u-signal/35 bg-u-signal-tint px-3 py-2"
            >
              <Icon d={ICONS.lock} size={14} className="mt-0.5 flex-none text-u-signal" />
              <div className="min-w-0 flex-1">
                <div className="text-body font-semibold text-u-signal">Processing restricted</div>
                <div className="mt-0.5 text-note text-u-text2">{restriction.reason}</div>
                <div className="mt-0.5 font-mono text-meta text-u-text3">
                  Restricted by {restriction.restrictedByName ?? "someone"} ·{" "}
                  {new Date(restriction.restrictedAt).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })}
                </div>
                {!restriction.mayLift && (
                  <div className="mt-0.5 text-note text-u-text3">
                    Only {restriction.restrictedByName ?? "whoever restricted them"} or an admin can lift it.
                  </div>
                )}
              </div>
              {restriction.mayLift && (
                <Button
                  variant="secondary"
                  size="xs"
                  disabled={lifting.isPending}
                  onClick={() => lifting.mutate()}
                  className="flex-none"
                >
                  Lift restriction
                </Button>
              )}
            </div>
          )}
        </PersonHeaderLayout>

        {showingContacts && (
          <ContactsReadDialog
            fullName={person.fullName}
            contacts={person.contacts}
            note={doNotContact ? "Do not contact is set — lookups are off for this person." : null}
            onClose={() => setShowingContacts(false)}
          />
        )}
        <DrawerTabBar
          idPrefix={TAB_ID_PREFIX}
          tabs={WORKSPACE_TABS}
          value={tab}
          onChange={onTabChange}
          counts={{
            notes: notes.data?.filter((note) => !isContactNote(note.kind)).length,
            positions: person.positions.length,
          }}
        />
      </header>

      <div key={tab} className="min-h-0 flex-1 overflow-y-auto px-5" {...tabPanelProps(TAB_ID_PREFIX, tab)}>
        {tab === "profile" && (
          <div className="py-4">
            <PersonProfileTab
              person={person}
              sections={sections}
              documents={documents}
              documentScope={documentScope}
              onPreview={openPreview}
            />
          </div>
        )}
        {tab === "notes" && (
          <div className="py-4">
            <PersonNotesTab person={person} notes={notes} />
          </div>
        )}
        {tab === "activity" && (
          <div className="pb-4">
            <ActivityTimeline
              scope={{ kind: "person", personId: person.personId }}
              executive={{
                fullName: person.fullName,
                face: (size, className) => <PersonAvatar person={person} size={size} className={className} />,
              }}
              documents={documents.documents.data}
              destinations={{
                onShowNote: () => onTabChange("notes"),
                onOpenTab: openTab,
                onShowContacts: () => setShowingContacts(true),
                onOpenMeetings: () => onTabChange("positions"),
                onOpenTask: () => onTabChange("positions"),
                onPreviewDocument: openPreview,
              }}
            />
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

      <DrawerFooter>
        <Button variant="secondary" className="ms-auto" onClick={startNote}>
          <Icon d={ICONS.plus} size={14} />
          Add note
        </Button>
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </DrawerFooter>

      <NextActionModal target={nextAction} onClose={() => setNextAction(null)} />

      <DocumentPreviewSheet
        scope={documentScope}
        documents={documents}
        target={preview}
        onTargetChange={setPreview}
        onClose={closePreview}
      />

      {restricting && (
        <RestrictProcessingDialog
          person={person}
          onClose={() => setRestricting(false)}
          onRestricted={(record) => {
            changed(record);
            setRestricting(false);
          }}
        />
      )}
      {exportingData && (
        <SubjectAccessExportDialog
          personId={person.personId}
          fullName={person.fullName}
          onClose={() => setExportingData(false)}
        />
      )}
      {erasing && (
        <ErasePersonDialog
          personId={person.personId}
          fullName={person.fullName}
          onClose={() => setErasing(false)}
          onErased={onClose}
        />
      )}

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
