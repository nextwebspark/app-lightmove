import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui/Toast";
import { ApiRequestError } from "../../../../lib/apiClient";
import * as poolApi from "../../api/poolApi";
import type { PersonErasurePreview } from "../../api/types";
import { ErasePersonDialog } from "./ErasePersonDialog";

vi.mock("../../api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof poolApi>()),
  getErasePreview: vi.fn(),
  erasePerson: vi.fn(),
}));

const PREVIEW: PersonErasurePreview = {
  personId: "person-1",
  fullName: "Noor Haddad",
  positions: 2,
  notes: 1,
  documents: 1,
  files: 3,
  emails: 6,
  outreachRuns: 2,
  meetings: 1,
  recordings: 1,
  tasks: 0,
  blockedBy: null,
};

function renderDialog(onErased = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <ErasePersonDialog personId="person-1" fullName="Noor Haddad" onClose={vi.fn()} onErased={onErased} />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return onErased;
}

describe("ErasePersonDialog", () => {
  beforeEach(() => {
    vi.mocked(poolApi.getErasePreview).mockResolvedValue(PREVIEW);
    vi.mocked(poolApi.erasePerson).mockResolvedValue(undefined);
  });
  afterEach(() => vi.restoreAllMocks());

  it("counts what goes, and deletes only once the name is typed back, suppressing by default", async () => {
    const onErased = renderDialog();

    expect(await screen.findByText(/documents? \(3 files\)/)).toBeInTheDocument();
    expect(screen.getByText(/emails and 2 outreach runs/)).toBeInTheDocument();
    const confirm = screen.getByRole("button", { name: "Delete permanently" });
    expect(confirm).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: /suppression list/ })).toBeChecked();

    await userEvent.type(screen.getByLabelText("Full name"), "  noor   haddad ");
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);

    await waitFor(() => expect(poolApi.erasePerson).toHaveBeenCalledWith("person-1", "  noor   haddad ", true));
    expect(onErased).toHaveBeenCalled();
  });

  it("can leave them off the suppression list", async () => {
    renderDialog();
    await screen.findByText(/call recording/);

    await userEvent.click(screen.getByRole("checkbox", { name: /suppression list/ }));
    await userEvent.type(screen.getByLabelText("Full name"), "Noor Haddad");
    await userEvent.click(screen.getByRole("button", { name: "Delete permanently" }));

    await waitFor(() => expect(poolApi.erasePerson).toHaveBeenCalledWith("person-1", "Noor Haddad", false));
  });

  it("says an email is sending, and deletes nothing, when the server refuses for that", async () => {
    vi.mocked(poolApi.erasePerson).mockRejectedValue(
      new ApiRequestError({ code: "PERSON_ERASE_SEND_IN_FLIGHT", detail: "sending", status: 409, correlationId: "none" }),
    );
    const onErased = renderDialog();
    await screen.findByText(/call recording/);

    await userEvent.type(screen.getByLabelText("Full name"), "Noor Haddad");
    await userEvent.click(screen.getByRole("button", { name: "Delete permanently" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("An email to this person is sending right now");
    expect(onErased).not.toHaveBeenCalled();
  });
});
