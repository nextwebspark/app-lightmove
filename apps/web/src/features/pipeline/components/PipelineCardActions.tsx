import { useQuery } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, LinesSkeleton, useToast } from "../../../components/ui";
import { Popover } from "../../../components/ui/Popover";
import { copyText } from "../../../lib/clipboard";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import * as poolApi from "../../candidates/api/poolApi";
import type { PipelineRow } from "../api/types";

export type CardActionKind = "call" | "email" | "note" | "schedule";

/** Why an action a card offers cannot be taken now; null where it can. */
export interface CardActionsUnavailable {
  email: string | null;
  schedule: string | null;
}

const ACTION_BUTTON =
  "grid size-[22px] flex-none place-items-center rounded-u-control text-u-accent hover:bg-u-accent-tint";

/**
 * A card's quick actions (`Position.dc.html`'s board): Log call, which first shows the numbers to call, then Email,
 * Add note and Schedule, each opening its own dialog. Their clicks never open the card.
 */
export function PipelineCardActions({
  row,
  unavailable,
  onAction,
  onOpenContact,
}: {
  row: PipelineRow;
  unavailable: CardActionsUnavailable;
  onAction: (kind: CardActionKind, row: PipelineRow) => void;
  onOpenContact: (row: PipelineRow) => void;
}) {
  return (
    <span className="ms-auto flex items-center gap-0.5" onClick={(event) => event.stopPropagation()}>
      <Popover
        label="Log call"
        title="Log call"
        hasPopup="dialog"
        align="right"
        placement="auto"
        width={260}
        triggerClassName={ACTION_BUTTON}
        trigger={() => <Icon d={ICONS.phone} size={14} />}
      >
        {(close) => (
          <PhonePanel
            row={row}
            onLogCall={() => {
              close();
              onAction("call", row);
            }}
            onOpenContact={() => {
              close();
              onOpenContact(row);
            }}
          />
        )}
      </Popover>
      <CardActionButton
        icon={ICONS.mail}
        label="Email"
        unavailable={unavailable.email}
        onPress={() => onAction("email", row)}
      />
      <CardActionButton
        icon={ICONS.noteBubble}
        label="Add note"
        unavailable={null}
        onPress={() => onAction("note", row)}
      />
      <CardActionButton
        icon={ICONS.calendar}
        label="Schedule"
        unavailable={unavailable.schedule}
        onPress={() => onAction("schedule", row)}
      />
    </span>
  );
}

function CardActionButton({
  icon,
  label,
  unavailable,
  onPress,
}: {
  icon: string;
  label: string;
  unavailable: string | null;
  onPress: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={unavailable ?? label}
      aria-disabled={unavailable !== null || undefined}
      onClick={() => {
        if (unavailable === null) onPress();
      }}
      className={cn(ACTION_BUTTON, unavailable !== null && "cursor-not-allowed opacity-40 hover:bg-transparent")}
    >
      <Icon d={icon} size={14} />
    </button>
  );
}

/** The numbers on the person's record, to call or copy, and the call to log once it is made. */
function PhonePanel({
  row,
  onLogCall,
  onOpenContact,
}: {
  row: PipelineRow;
  onLogCall: () => void;
  onOpenContact: () => void;
}) {
  const toast = useToast();
  const record = useQuery({
    queryKey: poolApi.PERSON_RECORD_KEY(row.personId),
    queryFn: ({ signal }) => poolApi.getPerson(row.personId, signal),
  });
  const phones = record.data?.contacts.phones ?? [];
  const doNotContact = record.data?.doNotContact ?? null;

  const copy = async (number: string) =>
    toast((await copyText(number)) ? "Number copied" : "Couldn't copy — select it and copy it yourself");

  return (
    <div role="group" aria-label={`Call ${row.fullName}`} className="flex flex-col gap-2.5 p-1">
      {record.isError ? (
        <p role="alert" className="text-meta text-u-text3">
          {messageFor(record.error)}
        </p>
      ) : record.isPending ? (
        <LinesSkeleton lines={2} />
      ) : (
        <>
          {doNotContact && (
            <p role="note" className="text-meta text-u-text2">
              <b className="font-semibold text-u-offlimits">Do not contact.</b>
              {doNotContact.reason ? ` ${doNotContact.reason}` : ""}
            </p>
          )}
          {phones.length === 0 ? (
            <p className="text-meta text-u-text3">
              No phone on file ·{" "}
              <button type="button" onClick={onOpenContact} className="font-medium text-u-accent hover:underline">
                Open Contact
              </button>
            </p>
          ) : (
            <ul className="flex flex-col gap-1">
              {phones.map((phone) => (
                <li key={phone.number} className="flex min-w-0 items-center gap-2">
                  <a
                    href={`tel:${phone.number.replace(/[^\d+]/g, "")}`}
                    className="min-w-0 truncate text-note font-medium text-u-text hover:text-u-accent hover:underline"
                  >
                    {phone.number}
                  </a>
                  {phone.kind && <span className="text-meta text-u-text3">{phone.kind === "work" ? "Work" : "Personal"}</span>}
                  <button
                    type="button"
                    aria-label={`Copy ${phone.number}`}
                    title="Copy"
                    onClick={() => void copy(phone.number)}
                    className="ms-auto grid size-[22px] flex-none place-items-center rounded-u-control text-u-text3 hover:bg-u-border hover:text-u-text"
                  >
                    <Icon d={ICONS.copy} size={12} />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      <Button size="xs" onClick={onLogCall}>
        <Icon d={ICONS.phone} size={12} />
        Log call
      </Button>
    </div>
  );
}
