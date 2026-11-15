import { NetworkMark } from "../../../components/ui/NetworkMark";
import { cn } from "../../../lib/cn";
import type { CandidatePhone } from "../../candidates/api/types";
import { addedCallingCodeOf, dialHrefOf, offersWhatsApp, whatsappHrefOf } from "../lib/phoneLinks";
import { isReachable, type PhoneReach } from "../lib/phoneReach";

export type { PhoneReach };

const ADD_COUNTRY_CODE = "Add the country code to open WhatsApp";

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
  if (!isReachable(reach) || !href) {
    return (
      <span title={textTitleOf(reach)} className={cn("min-w-0 truncate", className)}>
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
 * calling code the mark stays, faded, and opens Edit contact details for whoever may edit.
 */
export function WhatsAppLink({ phone, reach, size = 14 }: { phone: CandidatePhone; reach: PhoneReach; size?: number }) {
  if (!isReachable(reach) || !offersWhatsApp(phone)) return null;
  const href = whatsappHrefOf(phone);
  if (!href && reach.onAddCountryCode) {
    return (
      <button
        type="button"
        onClick={reach.onAddCountryCode}
        aria-label={ADD_COUNTRY_CODE}
        title={ADD_COUNTRY_CODE}
        className="flex flex-none rounded-md p-1 opacity-35 transition hover:bg-u-raised hover:opacity-100"
      >
        <NetworkMark network="whatsapp" size={size} />
      </button>
    );
  }
  if (!href) {
    return (
      <span role="img" aria-label={ADD_COUNTRY_CODE} title={ADD_COUNTRY_CODE} className="flex flex-none p-1 opacity-35">
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

/** Says a calling code was added from the person's or the firm's country; the written number is unchanged. */
export function AddedCodePill({ phone, reach }: { phone: CandidatePhone; reach: PhoneReach }) {
  const code = addedCallingCodeOf(phone);
  if (!isReachable(reach) || !code) return null;
  return (
    <span
      title={`${addedFromOf(phone, code, reach)}. The saved number is unchanged.`}
      className="inline-flex flex-none items-center rounded bg-u-raised px-1.5 py-0.5 font-mono text-meta text-u-text3"
    >
      {code} from {phone.countrySource === "location" ? "location" : "your firm"}
    </span>
  );
}

export function SaveInternationalButton({ phone, reach }: { phone: CandidatePhone; reach: PhoneReach }) {
  const onSave = reach.onSaveInternational;
  if (!isReachable(reach) || !onSave || !addedCallingCodeOf(phone) || !phone.international) return null;
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

function textTitleOf(reach: PhoneReach): string | undefined {
  if (reach.doNotContact) return "Do not contact is set";
  return reach.mayReachOut ? undefined : "You can read this number but not reach out on this position";
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
