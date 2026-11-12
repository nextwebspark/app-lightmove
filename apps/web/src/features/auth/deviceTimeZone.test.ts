import { QueryClient } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "./api/authApi";
import type { UserDateTime } from "./api/types";
import { forgetReportedTimeZones, reportDeviceTimeZone } from "./deviceTimeZone";

vi.mock("./api/authApi", () => ({ reportDeviceTimeZone: vi.fn() }));

const AUTOMATIC: UserDateTime = {
  effective: { timeZone: "Asia/Dubai", dateFormat: "DD_MMM_YYYY", timeFormat: "H24" },
  own: { timeZone: null, dateFormat: null, timeFormat: null },
  deviceTimeZone: "Asia/Dubai",
  workspace: { timeZone: "Asia/Dubai", dateFormat: "DD_MMM_YYYY", timeFormat: "H24" },
};

let computer: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  forgetReportedTimeZones();
  vi.mocked(authApi.reportDeviceTimeZone).mockReset().mockResolvedValue(undefined);
  computer = vi
    .spyOn(Intl.DateTimeFormat.prototype, "resolvedOptions")
    .mockReturnValue({ timeZone: "Europe/London" } as Intl.ResolvedDateTimeFormatOptions);
});

afterEach(() => computer.mockRestore());

describe("reporting this browser's zone", () => {
  it("tells the server once when the device moved, and refreshes the mailbox an Automatic clock moves", async () => {
    const queryClient = new QueryClient();
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");

    reportDeviceTimeZone(AUTOMATIC, queryClient);
    reportDeviceTimeZone(AUTOMATIC, queryClient);

    expect(authApi.reportDeviceTimeZone).toHaveBeenCalledTimes(1);
    expect(authApi.reportDeviceTimeZone).toHaveBeenCalledWith("Europe/London");
    await vi.waitFor(() => expect(invalidate).toHaveBeenCalledWith({ queryKey: ["outreach", "mailbox"] }));
  });

  it("says nothing when the server already holds this zone", () => {
    reportDeviceTimeZone({ ...AUTOMATIC, deviceTimeZone: "Europe/London" }, new QueryClient());

    expect(authApi.reportDeviceTimeZone).not.toHaveBeenCalled();
  });

  it("reports a pinned person's device too, without touching their mailbox", async () => {
    const queryClient = new QueryClient();
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");

    reportDeviceTimeZone({ ...AUTOMATIC, own: { ...AUTOMATIC.own, timeZone: "Asia/Dubai" } }, queryClient);

    await vi.waitFor(() => expect(authApi.reportDeviceTimeZone).toHaveBeenCalledWith("Europe/London"));
    expect(invalidate).not.toHaveBeenCalled();
  });

  it("tries again after a refused report", async () => {
    vi.mocked(authApi.reportDeviceTimeZone).mockRejectedValueOnce(new Error("offline"));

    reportDeviceTimeZone(AUTOMATIC, new QueryClient());
    await vi.waitFor(() => expect(authApi.reportDeviceTimeZone).toHaveBeenCalledTimes(1));
    await Promise.resolve();
    reportDeviceTimeZone(AUTOMATIC, new QueryClient());

    await vi.waitFor(() => expect(authApi.reportDeviceTimeZone).toHaveBeenCalledTimes(2));
  });
});
