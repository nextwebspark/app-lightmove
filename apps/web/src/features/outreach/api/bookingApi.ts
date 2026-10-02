import { request } from "../../../lib/apiClient";

/** The booking link's page: public, opened from an email by an executive with no session. */

export const BOOKING_PAGE_KEY = (slug: string) => ["booking-page", slug] as const;

export interface BookingPage {
  /** The scheduler page Nylas opens, on the consultant's calendar. */
  configurationId: string;
  /** The Nylas API region the page lives in. */
  schedulerApiUrl: string;
  consultantName: string | null;
}

export function getBookingPage(slug: string, signal?: AbortSignal): Promise<BookingPage> {
  return request<BookingPage>(`/outreach/booking/${encodeURIComponent(slug)}`, { signal });
}
