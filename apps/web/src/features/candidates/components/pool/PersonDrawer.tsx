import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Avatar } from "../../../../components/ui/Avatar";
import { Drawer, DrawerCloseButton } from "../../../../components/ui/Drawer";
import { Button, Select, useToast } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { PersonRecord } from "../../api/types";
import { usePoolLookups } from "../../lib/usePoolLookups";
import { HeaderProfileLink } from "../ProfileParts";
import { PersonNotesTab } from "./PersonNotesTab";
import { PersonProfileTab } from "./PersonProfileTab";
import { PersonTimelineTab } from "./PersonTimelineTab";
import { TagPicker } from "./TagPicker";
import { TagPill } from "./TagPill";

export type PersonDrawerTab = "profile" | "notes" | "timeline";

const TABS: { value: PersonDrawerTab; label: string }[] = [
  { value: "profile", label: "Profile" },
  { value: "notes", label: "Notes" },
  { value: "timeline", label: "Timeline" },
];

const DEFAULT_REASON = "Marked from the candidate drawer.";

/**
 * A workspace person on the Candidates page, as `Candidates.dc.html` draws them: who they are and the
 * team's own facts about them — owner, do not contact, tags — over three tabs. Staff-only, like the
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
            <DrawerCloseButton onClose={onClose} />
            <p className="mt-6 text-[13px] text-u-text3">{messageFor(record.error)}</p>
          </div>
        ) : !record.data ? (
          <div className="relative p-5">
            <DrawerCloseButton onClose={onClose} />
            <p className="mt-6 text-[13px] text-u-text3">Loading…</p>
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
  const queryClient = useQueryClient();
  const toast = useToast();
  const lookups = usePoolLookups();
  const notes = useQuery({
    queryKey: poolApi.POOL_NOTES_KEY(person.personId),
    queryFn: ({ signal }) => poolApi.getPoolNotes(person.personId, signal),
  });

  const changed = (updated: PersonRecord) => {
    queryClient.setQueryData(poolApi.PERSON_RECORD_KEY(updated.personId), updated);
    void queryClient.invalidateQueries({ queryKey: poolApi.POOL_KEY });
  };

  const owning = useMutation({
    mutationFn: (ownerUserId: string | null) => poolApi.setOwner(person.personId, ownerUserId),
    onSuccess: changed,
    onError: (error) => toast(messageFor(error)),
  });
  const marking = useMutation({
    mutationFn: (doNotContact: boolean) =>
      poolApi.setDoNotContact(person.personId, doNotContact, doNotContact ? DEFAULT_REASON : undefined),
    onSuccess: changed,
    onError: (error) => toast(messageFor(error)),
  });
  const untagging = useMutation({
    mutationFn: (tagId: string) => poolApi.untagPerson(person.personId, tagId),
    onSuccess: (updated) => {
      changed(updated);
      void queryClient.invalidateQueries({ queryKey: poolApi.TAGS_KEY });
    },
    onError: (error) => toast(messageFor(error)),
  });

  const context = [person.companyName, person.locationCity, person.locationCountry].filter(Boolean).join(" · ");
  const held = person.tagIds.map((id) => lookups.tagsById.get(id)).filter((tag) => tag !== undefined);
  const doNotContact = person.doNotContact;

  return (
    <>
      <header className="relative border-b border-u-border px-5 pb-3 pt-4">
        <div className="flex items-start gap-3">
          <Avatar id={person.personId} name={person.fullName} size="xl" />
          <div className="min-w-0 flex-1 pe-8">
            <div className="flex items-center gap-2">
              <h2 className="truncate text-[18px] font-semibold text-u-text">{person.fullName}</h2>
              <HeaderProfileLink linkedinUrl={person.linkedinUrl} />
            </div>
            <p className="truncate text-[13px] text-u-text2">{person.title ?? "—"}</p>
            <p className="truncate font-mono text-[11.5px] text-u-text3">{context || "No employer or location recorded"}</p>
          </div>
        </div>
        <DrawerCloseButton onClose={onClose} />

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

        <div className="mt-3 flex flex-wrap items-center gap-1.5">
          {held.map((tag) => (
            <span key={tag.id} className="flex items-center">
              <TagPill tag={tag} className="pe-1" />
              <button
                type="button"
                aria-label={`Remove tag ${tag.label}`}
                onClick={() => untagging.mutate(tag.id)}
                className="-ms-1 grid size-4 place-items-center rounded-full text-u-text3 hover:text-u-text"
              >
                <Icon d={ICONS.close} size={10} />
              </button>
            </span>
          ))}
          <TagPicker person={person} tags={lookups.tags} onChanged={changed} />
        </div>

        <div role="tablist" aria-label="Candidate sections" className="-mb-3 mt-3 flex gap-4">
          {TABS.map((option) => {
            const count = option.value === "notes" ? (notes.data?.length ?? 0) : 0;
            return (
              <button
                key={option.value}
                type="button"
                role="tab"
                aria-selected={tab === option.value}
                onClick={() => onTabChange(option.value)}
                className={cn(
                  "flex items-center gap-1.5 border-b-2 pb-2 text-[13px] font-semibold",
                  tab === option.value ? "border-u-accent text-u-text" : "border-transparent text-u-text3 hover:text-u-text2",
                )}
              >
                {option.label}
                {count > 0 && (
                  <span className="rounded-full bg-u-raised px-1.5 font-mono text-[10.5px] text-u-text3">{count}</span>
                )}
              </button>
            );
          })}
        </div>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4" role="tabpanel">
        {tab === "profile" && <PersonProfileTab person={person} />}
        {tab === "notes" && <PersonNotesTab person={person} notes={notes} />}
        {tab === "timeline" && <PersonTimelineTab personId={person.personId} />}
      </div>

      <footer className="flex justify-end border-t border-u-border px-5 py-3">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </footer>
    </>
  );
}
