import type { CandidatePhone } from "../../candidates/api/types";

export interface PhoneReach {
  personName: string;
  locationCountry: string | null;
  /** False for a reader who may not reach out on this position. */
  mayReachOut: boolean;
  doNotContact: boolean;
  /** Absent for a reader who may not edit. */
  onSaveInternational?: (phone: CandidatePhone) => void;
  /** Opens Edit contact details from the WhatsApp mark of a number with no calling code; absent for a reader. */
  onAddCountryCode?: () => void;
}

export function isReachable(reach: PhoneReach): boolean {
  return reach.mayReachOut && !reach.doNotContact;
}
