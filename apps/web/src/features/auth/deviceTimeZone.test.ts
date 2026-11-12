import { QueryClient } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as authApi from "./api/authApi";
import type { UserDateTime } from "./api/types";
import { forgetReportedTimeZones, reportDeviceTimeZone } from "./deviceTimeZone";
import { clearTimeZoneMove, useTimeZoneMove } from "./timeZoneMove";

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
  clearTimeZoneMove();
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
    await vi.waitFor(() => {
      reportDeviceTimeZone(AUTOMATIC, new QueryClient());
      expect(authApi.reportDeviceTimeZone).toHaveBeenCalledTimes(2);
    });
  });

  it("holds the move an Automatic clock made until its person answers it", async () => {
    const move = renderHook(() => useTimeZoneMove());

    reportDeviceTimeZone(AUTOMATIC, new QueryClient());

    await vi.waitFor(() => expect(move.result.current).toEqual({ from: "Asia/Dubai", to: "Europe/London" }));
  });

  it("records no move for a pinned zone or a first report", async () => {
    const move = renderHook(() => useTimeZoneMove());

    reportDeviceTimeZone({ ...AUTOMATIC, own: { ...AUTOMATIC.own, timeZone: "Asia/Dubai" } }, new QueryClient());
    forgetReportedTimeZones();
    reportDeviceTimeZone({ ...AUTOMATIC, deviceTimeZone: null }, new QueryClient());

    await vi.waitFor(() => expect(authApi.reportDeviceTimeZone).toHaveBeenCalledTimes(2));
    await Promise.resolve();
    expect(move.result.current).toBeNull();
  });
});
