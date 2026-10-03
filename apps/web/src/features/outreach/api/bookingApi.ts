import { request } from "../../../lib/apiClient";
import type { SlotDay } from "./meetingApi";

/** The booking link's page: public, opened from an email by an executive with no session. */

export const BOOKING_PAGE_KEY = (slug: string) => ["booking-page", slug] as const;
export const BOOKING_SLOTS_KEY = (slug: string, from: string | null) => ["booking-page", slug, "slots", from] as const;

export interface BookingPage {
  /** NYLAS opens Nylas's scheduler; DIRECT is Uncava's own page, on a mailbox connected without Nylas. */
  kind: "NYLAS" | "DIRECT";
  /** The scheduler page Nylas opens, on the consultant's calendar. Null on a direct page. */
  configurationId: string | null;
  /** The Nylas API region the page lives in. Null on a direct page. */
  schedulerApiUrl: string | null;
  consultantName: string | null;
  minutes: number;
}

export interface BookingSlots {
  timeZone: string;
  minutes: number;
  earliestDate: string;
  latestDate: string;
  previousFrom: string | null;
  days: SlotDay[];
}

export interface BookOnPage {
  startsAt: string;
  name: string;
  email: string;
}

export function getBookingPage(slug: string, signal?: AbortSignal): Promise<BookingPage> {
  return request<BookingPage>(`/outreach/booking/${encodeURIComponent(slug)}`, { signal });
}

export function getBookingSlots(slug: string, from: string | null, signal?: AbortSignal): Promise<BookingSlots> {
  const query = from ? `?from=${encodeURIComponent(from)}` : "";
  return request<BookingSlots>(`/outreach/booking/${encodeURIComponent(slug)}/slots${query}`, { signal });
}

export function bookOnPage(slug: string, booking: BookOnPage): Promise<void> {
  return request<void>(`/outreach/booking/${encodeURIComponent(slug)}`, { method: "POST", body: booking });
}
