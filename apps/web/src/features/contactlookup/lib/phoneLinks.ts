import type { CandidatePhone } from "../../candidates/api/types";

/** `tel:` with the international number where the server could read one, else the written digits a dialer still takes. */
export function dialHrefOf(phone: CandidatePhone): string | null {
  const dial = phone.international ? digitsOf(phone.international, true) : writtenDialOf(phone.number);
  return /\d/.test(dial) ? `tel:${dial}` : null;
}

/** The chat for a number with a known country code; none for a landline, which WhatsApp cannot reach. */
export function whatsappHrefOf(phone: CandidatePhone): string | null {
  if (!phone.international || phone.lineType === "landline") return null;
  return `https://wa.me/${digitsOf(phone.international, false)}`;
}

/** The calling code the server added to a locally written number, `+971`; null where it was written or none was. */
export function addedCallingCodeOf(phone: CandidatePhone): string | null {
  if (!phone.international || (phone.countrySource !== "location" && phone.countrySource !== "workspace")) return null;
  return phone.international.split(/[\s-]/, 1)[0];
}

function writtenDialOf(number: string): string {
  const withoutExtension = number.replace(/(?:ext\.?|x)\s*\d+\s*$/i, "");
  return digitsOf(withoutExtension, true).replace(/^00/, "+");
}

function digitsOf(text: string, keepPlus: boolean): string {
  const digits = text.replace(/\D/g, "");
  return keepPlus && text.trim().startsWith("+") ? `+${digits}` : digits;
}
