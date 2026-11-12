import type { QueryClient } from "@tanstack/react-query";
import { deviceTimeZone } from "../../lib/datetime";
import { reportDeviceTimeZone as sendDeviceTimeZone } from "./api/authApi";
import type { UserDateTime } from "./api/types";
import { recordTimeZoneMove } from "./timeZoneMove";

const MAILBOX_KEY = ["outreach", "mailbox"] as const;

const reportedThisTab = new Set<string>();

/**
 * Once per zone per tab, and only when it differs from what the server last heard. A pinned zone is reported too, so
 * Profile can name "this device" once its person goes back to Automatic.
 */
export function reportDeviceTimeZone(dateTime: UserDateTime, queryClient: QueryClient): void {
  const zone = deviceTimeZone();
  if (!zone || zone === dateTime.deviceTimeZone || reportedThisTab.has(zone)) return;
  reportedThisTab.add(zone);
  void send(zone, dateTime, queryClient);
}

async function send(zone: string, dateTime: UserDateTime, queryClient: QueryClient): Promise<void> {
  try {
    await sendDeviceTimeZone(zone);
  } catch {
    // A refused or lost report changes nothing on screen, which already reads in this zone; the next tab tries again.
    reportedThisTab.delete(zone);
    return;
  }
  if (dateTime.own.timeZone) return;
  void queryClient.invalidateQueries({ queryKey: MAILBOX_KEY });
  if (dateTime.deviceTimeZone) recordTimeZoneMove({ from: dateTime.deviceTimeZone, to: zone });
}

/** For a test that signs in more than once in one module. */
export function forgetReportedTimeZones(): void {
  reportedThisTab.clear();
}
