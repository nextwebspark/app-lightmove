import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "../../../lib/apiClient";
import * as platformApi from "../api/platformApi";
import { PlatformStepUp } from "./PlatformStepUp";

const prompts: (() => Promise<void>)[] = [];

vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  onStepUpRequired: (prompt: () => Promise<void>) => {
    prompts.push(prompt);
    return () => undefined;
  },
}));
vi.mock("../api/platformApi", () => ({ confirmStepUp: vi.fn() }));

/** What a refused request does: asks, and waits on the answer. */
const ask = () => {
  let answer!: Promise<string>;
  act(() => {
    answer = prompts[prompts.length - 1]().then(
      () => "confirmed",
      () => "dismissed",
    );
  });
  return answer;
};

describe("PlatformStepUp", () => {
  beforeEach(() => {
    prompts.length = 0;
    vi.resetAllMocks();
  });

  it("draws nothing until a request asks for a code", () => {
    render(<PlatformStepUp />);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("takes the code, and lets the waiting request go on once the server has it", async () => {
    vi.mocked(platformApi.confirmStepUp).mockResolvedValue(undefined);
    render(<PlatformStepUp />);
    const answer = ask();

    await userEvent.type(await screen.findByLabelText(/Code/), "123 456");
    await userEvent.click(screen.getByRole("button", { name: "Confirm" }));

    await expect(answer).resolves.toBe("confirmed");
    expect(platformApi.confirmStepUp).toHaveBeenCalledWith("123 456");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("keeps a wrong code on its field and the dialog open", async () => {
    vi.mocked(platformApi.confirmStepUp).mockRejectedValue(
      new ApiRequestError({
        code: "MFA_CODE_INVALID",
        detail: "bad code",
        status: 400,
        correlationId: "c1",
        fieldErrors: { code: "That code didn't work. Check your authenticator app and try again" },
      }),
    );
    render(<PlatformStepUp />);
    void ask();

    await userEvent.type(await screen.findByLabelText(/Code/), "000000");
    await userEvent.click(screen.getByRole("button", { name: "Confirm" }));

    expect(
      await screen.findByText("That code didn't work. Check your authenticator app and try again"),
    ).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("refuses a value that is neither a code nor a recovery code without asking the server", async () => {
    render(<PlatformStepUp />);
    void ask();

    await userEvent.type(await screen.findByLabelText(/Code/), "12");
    await userEvent.click(screen.getByRole("button", { name: "Confirm" }));

    expect(await screen.findByText("Enter a 6-digit code or a recovery code")).toBeInTheDocument();
    expect(platformApi.confirmStepUp).not.toHaveBeenCalled();
  });

  it("tells the waiting request when the dialog is dismissed", async () => {
    render(<PlatformStepUp />);
    const answer = ask();

    await userEvent.click(await screen.findByRole("button", { name: "Cancel" }));

    await expect(answer).resolves.toBe("dismissed");
  });

  it("shares one dialog between two requests refused together", async () => {
    vi.mocked(platformApi.confirmStepUp).mockResolvedValue(undefined);
    render(<PlatformStepUp />);
    const first = ask();
    const second = ask();

    await userEvent.type(await screen.findByLabelText(/Code/), "123456");
    await userEvent.click(screen.getByRole("button", { name: "Confirm" }));

    await expect(first).resolves.toBe("confirmed");
    await expect(second).resolves.toBe("confirmed");
    expect(platformApi.confirmStepUp).toHaveBeenCalledTimes(1);
  });
});
