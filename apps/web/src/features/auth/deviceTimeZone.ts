import type { QueryClient } from "@tanstack/react-query";
import { computerTimeZone } from "../../lib/datetime";
import { reportDeviceTimeZone as sendDeviceTimeZone } from "./api/authApi";
import type { UserDateTime } from "./api/types";

/** The mailbox read carries the zone sequences send by, which a report moves. */
const MAILBOX_KEY = ["outreach", "mailbox"] as const;

const reportedThisTab = new Set<string>();

/**
 * Tells the server which zone this browser is in, so an Automatic clock's sending hours and digest follow the person
 * as the screens already do. Once per zone per tab, and only when it differs from what the server last heard; a
 * pinned zone is reported too, so Profile can name "this device" once the person goes back to Automatic.
 */
export function reportDeviceTimeZone(dateTime: UserDateTime, queryClient: QueryClient): void {
  const zone = computerTimeZone();
  if (!zone || zone === dateTime.deviceTimeZone || reportedThisTab.has(zone)) return;
  reportedThisTab.add(zone);
  Promise.resolve(sendDeviceTimeZone(zone))
    .then(() => {
      if (!dateTime.own.timeZone) void queryClient.invalidateQueries({ queryKey: MAILBOX_KEY });
    })
    // A refused or lost report changes nothing on screen, which already reads in this zone; the next tab tries again.
    .catch(() => reportedThisTab.delete(zone));
}

/** For a test that signs in more than once in one module. */
export function forgetReportedTimeZones(): void {
  reportedThisTab.clear();
}
