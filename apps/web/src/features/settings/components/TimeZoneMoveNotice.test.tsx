import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as authApi from "../../auth/api/authApi";
import type { User } from "../../auth/api/types";
import { clearTimeZoneMove, recordTimeZoneMove, reloadTimeZoneMove } from "../../auth/timeZoneMove";
import { aUser, FOLLOWING_DATE_TIME } from "../../../test/fixtures/user";
import { TimeZoneMoveNotice } from "./TimeZoneMoveNotice";

vi.mock("../../auth/api/authApi", () => ({ updateProfile: vi.fn() }));

let signedIn: User;
const reload = vi.fn();
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: signedIn, reload }) }));

let computer: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  localStorage.clear();
  clearTimeZoneMove();
  vi.mocked(authApi.updateProfile).mockReset().mockResolvedValue(aUser());
  reload.mockReset().mockResolvedValue(undefined);
  computer = vi
    .spyOn(Intl.DateTimeFormat.prototype, "resolvedOptions")
    .mockReturnValue({ timeZone: "Europe/London" } as Intl.ResolvedDateTimeFormatOptions);
  // The server already holds London: the move is remembered here, not re-read off /me.
  signedIn = aUser({ dateTime: { ...FOLLOWING_DATE_TIME, deviceTimeZone: "Europe/London" } });
  recordTimeZoneMove({ from: "Asia/Dubai", to: "Europe/London" });
});

afterEach(() => computer.mockRestore());

const notice = () => (
  <ToastProvider>
    <TimeZoneMoveNotice />
  </ToastProvider>
);

describe("TimeZoneMoveNotice — an Automatic clock that moved with its device", () => {
  it("stays until answered, and Keep London puts it away for good", async () => {
    const user = userEvent.setup();
    const { unmount } = render(notice());

    expect(screen.getByText(/Now on London time/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Keep London" }));
    expect(screen.queryByText(/Now on London time/)).not.toBeInTheDocument();

    unmount();
    reloadTimeZoneMove();
    render(notice());
    expect(screen.queryByText(/Now on London time/)).not.toBeInTheDocument();
  });

  it("survives a reload of the page", () => {
    reloadTimeZoneMove();
    render(notice());

    expect(screen.getByText(/Now on London time/)).toBeInTheDocument();
  });

  it("pins the zone they came from on Stay on Dubai time", async () => {
    const user = userEvent.setup();
    render(notice());

    await user.click(screen.getByRole("button", { name: "Stay on Dubai time" }));

    await waitFor(() =>
      expect(authApi.updateProfile).toHaveBeenCalledWith(
        expect.objectContaining({ timezone: "Asia/Dubai", dateFormat: null, timeFormat: null }),
      ),
    );
    expect(reload).toHaveBeenCalled();
    expect(await screen.findByText("Staying on Dubai time")).toBeInTheDocument();
  });

  it("offers no Stay for a zone a person cannot pick, like a Madrid laptop's", () => {
    act(() => recordTimeZoneMove({ from: "Europe/Madrid", to: "Europe/London" }));
    render(notice());

    expect(screen.getByText(/Now on London time/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Keep London" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Stay on/ })).not.toBeInTheDocument();
  });

  it("says nothing when the zone is pinned, the device has moved on, or there is no move", () => {
    signedIn = aUser({
      dateTime: { ...FOLLOWING_DATE_TIME, own: { ...FOLLOWING_DATE_TIME.own, timeZone: "Asia/Dubai" } },
    });
    const { rerender } = render(notice());
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    signedIn = aUser({ dateTime: { ...FOLLOWING_DATE_TIME, deviceTimeZone: "Europe/London" } });
    computer.mockReturnValue({ timeZone: "Europe/Paris" } as Intl.ResolvedDateTimeFormatOptions);
    rerender(notice());
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    computer.mockReturnValue({ timeZone: "Europe/London" } as Intl.ResolvedDateTimeFormatOptions);
    act(() => clearTimeZoneMove());
    rerender(notice());
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
