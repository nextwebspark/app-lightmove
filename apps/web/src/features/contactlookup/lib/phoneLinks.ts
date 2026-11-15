import type { CandidatePhone } from "../../candidates/api/types";

/** `tel:` with the server's E.164 reading where it has one, else the written digits a dialer still takes. */
export function dialHrefOf(phone: CandidatePhone): string | null {
  const dial = phone.e164 ?? writtenDialOf(phone.number);
  return /\d/.test(dial) ? `tel:${dial}` : null;
}

/** False for a landline or a service number, which WhatsApp cannot reach; an unread number may still be a mobile. */
export function offersWhatsApp(phone: CandidatePhone): boolean {
  return phone.lineType !== "landline" && phone.lineType !== "service";
}

export function whatsappHrefOf(phone: CandidatePhone): string | null {
  if (!phone.e164 || !offersWhatsApp(phone)) return null;
  return `https://wa.me/${phone.e164.replace(/\D/g, "")}`;
}

/** The calling code the server added to a locally written number, `+971`; null where it was written or none was. */
export function addedCallingCodeOf(phone: CandidatePhone): string | null {
  if (phone.countrySource !== "location" && phone.countrySource !== "workspace") return null;
  return phone.callingCode ?? null;
}

function writtenDialOf(number: string): string {
  const withoutExtension = number.replace(/(?:ext\.?|x)\s*\d+\s*$/i, "");
  const digits = withoutExtension.replace(/\D/g, "");
  return withoutExtension.trim().startsWith("+") ? `+${digits}` : digits.replace(/^00/, "+");
}
