import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as authApi from "../../auth/api/authApi";
import type { User } from "../../auth/api/types";
import { aUser, FOLLOWING_DATE_TIME } from "../../../test/fixtures/user";
import { TimeZoneMoveNotice } from "./TimeZoneMoveNotice";

vi.mock("../../auth/api/authApi", () => ({ updateProfile: vi.fn() }));

let signedIn: User;
const reload = vi.fn();
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: signedIn, reload }) }));

let computer: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  localStorage.clear();
  vi.mocked(authApi.updateProfile).mockReset().mockResolvedValue(aUser());
  reload.mockReset().mockResolvedValue(undefined);
  computer = vi
    .spyOn(Intl.DateTimeFormat.prototype, "resolvedOptions")
    .mockReturnValue({ timeZone: "Europe/London" } as Intl.ResolvedDateTimeFormatOptions);
  signedIn = aUser({ dateTime: { ...FOLLOWING_DATE_TIME, deviceTimeZone: "Asia/Dubai" } });
});

afterEach(() => computer.mockRestore());

const renderNotice = () =>
  render(
    <ToastProvider>
      <TimeZoneMoveNotice />
    </ToastProvider>,
  );

describe("TimeZoneMoveNotice — an Automatic clock that moved with its device", () => {
  it("says the move aloud, and Keep London puts it away for good", async () => {
    const user = userEvent.setup();
    const { unmount } = renderNotice();

    expect(screen.getByText(/Now on London time/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Keep London" }));
    expect(screen.queryByText(/Now on London time/)).not.toBeInTheDocument();

    unmount();
    renderNotice();
    expect(screen.queryByText(/Now on London time/)).not.toBeInTheDocument();
  });

  it("pins the zone they came from on Stay on Dubai time", async () => {
    const user = userEvent.setup();
    renderNotice();

    await user.click(screen.getByRole("button", { name: "Stay on Dubai time" }));

    await waitFor(() =>
      expect(authApi.updateProfile).toHaveBeenCalledWith(
        expect.objectContaining({ timezone: "Asia/Dubai", dateFormat: null, timeFormat: null }),
      ),
    );
    expect(reload).toHaveBeenCalled();
    expect(await screen.findByText("Staying on Dubai time")).toBeInTheDocument();
  });

  it("says nothing when the zone is pinned, unchanged, or never reported", () => {
    signedIn = aUser({
      dateTime: { ...FOLLOWING_DATE_TIME, own: { ...FOLLOWING_DATE_TIME.own, timeZone: "Asia/Dubai" }, deviceTimeZone: "Asia/Dubai" },
    });
    const { rerender } = renderNotice();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    signedIn = aUser({ dateTime: { ...FOLLOWING_DATE_TIME, deviceTimeZone: "Europe/London" } });
    rerender(<ToastProvider><TimeZoneMoveNotice /></ToastProvider>);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    signedIn = aUser({ dateTime: { ...FOLLOWING_DATE_TIME, deviceTimeZone: null } });
    rerender(<ToastProvider><TimeZoneMoveNotice /></ToastProvider>);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
