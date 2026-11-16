import type { CandidatePhone, RecentApproach } from "../../candidates/api/types";

/** Who a phone on screen reaches, and on which position a log of a press is filed. */
export interface DialTarget {
  projectId: string;
  candidateId: string;
  personName: string;
  positionTitle: string | null;
  /** A teammate's recent approach: at `CAUTION` a press asks first, as Enrol and Reply do. */
  approach?: RecentApproach | null;
}

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
  /** With it, a press of Call, WhatsApp or Copy leaves a card in the log-it tray; absent where no log is filed. */
  target?: DialTarget;
}

export function isReachable(reach: PhoneReach): boolean {
  return reach.mayReachOut && !reach.doNotContact;
}
