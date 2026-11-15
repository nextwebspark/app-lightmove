import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { NetworkMark } from "../../../components/ui/NetworkMark";
import { cn } from "../../../lib/cn";
import { copyText } from "../../../lib/clipboard";
import type { CandidatePhone } from "../../candidates/api/types";
import { addedCallingCodeOf, dialHrefOf, whatsappHrefOf } from "../lib/phoneLinks";

/**
 * Who a number reaches and what the reader may do with it. `actionable` is false for a reader who may not reach out
 * and for someone on do not contact; `refusal` says why to a reader who sees the number as text.
 */
export interface PhoneReach {
  personName: string;
  locationCountry: string | null;
  actionable: boolean;
  refusal?: string;
  /** Saves a locally written number with the calling code the server added; absent for a reader who may not edit. */
  onSaveInternational?: (phone: CandidatePhone) => void;
}

/** The number as the button: it dials through the device's own phone app, or reads as text where nobody may call. */
export function PhoneNumberLink({
  phone,
  reach,
  className,
}: {
  phone: CandidatePhone;
  reach: PhoneReach;
  className?: string;
}) {
  const href = dialHrefOf(phone);
  if (!reach.actionable || !href) {
    return (
      <span title={reach.refusal} className={cn("min-w-0 truncate", className)}>
        {phone.number}
      </span>
    );
  }
  return (
    <a
      href={href}
      aria-label={`Call ${reach.personName} on ${phone.number}`}
      title={callTitleOf(phone, reach)}
      className={cn("min-w-0 truncate hover:underline", className)}
    >
      {phone.number}
    </a>
  );
}

/**
 * WhatsApp's own mark, opening the chat in a new tab (wa.me hands it to the app where it is installed). Without a
 * calling code there is no chat to open, so the mark stays, disabled, saying what is missing; a landline has none.
 */
export function WhatsAppLink({ phone, reach, size = 14 }: { phone: CandidatePhone; reach: PhoneReach; size?: number }) {
  if (!reach.actionable || phone.lineType === "landline") return null;
  const href = whatsappHrefOf(phone);
  if (!href) {
    return (
      <span
        role="img"
        aria-label="Add the country code to open WhatsApp"
        title="Add the country code to open WhatsApp"
        className="flex flex-none p-1 opacity-35"
      >
        <NetworkMark network="whatsapp" size={size} />
      </span>
    );
  }
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={`WhatsApp ${reach.personName}`}
      title="WhatsApp"
      className="flex flex-none rounded-md p-1 transition hover:bg-u-raised"
    >
      <NetworkMark network="whatsapp" size={size} />
    </a>
  );
}

export function CopyPhoneButton({ phone, size = 13 }: { phone: CandidatePhone; size?: number }) {
  const toast = useToast();
  return (
    <button
      type="button"
      onClick={async () => toast((await copyText(phone.number)) ? "Phone copied" : "Couldn't copy")}
      aria-label="Copy phone"
      title="Copy"
      className="flex flex-none rounded-md p-1 text-u-text3 transition hover:bg-u-raised hover:text-u-text"
    >
      <Icon d={ICONS.copy} size={size} />
    </button>
  );
}

/** Says a calling code was added from the person's or the firm's country; the written number is unchanged. */
export function AddedCodePill({ phone, reach }: { phone: CandidatePhone; reach: PhoneReach }) {
  const code = addedCallingCodeOf(phone);
  if (!reach.actionable || !code) return null;
  return (
    <span
      title={`${addedFromOf(phone, code, reach)}. The saved number is unchanged.`}
      className="inline-flex flex-none items-center rounded bg-u-raised px-1.5 py-0.5 font-mono text-meta text-u-text3"
    >
      {code} from {phone.countrySource === "location" ? "location" : "your firm"}
    </span>
  );
}

/** Offers to keep the calling code the server added, to whoever may edit; the server spells it the international way. */
export function SaveInternationalButton({ phone, reach }: { phone: CandidatePhone; reach: PhoneReach }) {
  if (!reach.actionable || !reach.onSaveInternational || !addedCallingCodeOf(phone) || !phone.international) return null;
  const onSave = reach.onSaveInternational;
  return (
    <button
      type="button"
      onClick={() => onSave(phone)}
      className="basis-full text-start font-mono text-meta font-medium text-u-accent hover:underline"
    >
      Save as {phone.international}
    </button>
  );
}

function callTitleOf(phone: CandidatePhone, reach: PhoneReach): string {
  const code = addedCallingCodeOf(phone);
  if (code && phone.international) return `Call ${phone.international} · ${addedFromOf(phone, code, reach)}`;
  return phone.lineType === "landline" ? `Call ${phone.number} · landline` : `Call ${phone.number}`;
}

function addedFromOf(phone: CandidatePhone, code: string, reach: PhoneReach): string {
  const firstName = reach.personName.split(" ")[0];
  return phone.countrySource === "location" && reach.locationCountry
    ? `${code} added from ${firstName}'s location (${reach.locationCountry})`
    : `${code} added from your firm's country`;
}
