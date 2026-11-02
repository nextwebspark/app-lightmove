import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui/Toast";
import { ApiRequestError } from "../../../../lib/apiClient";
import * as poolApi from "../../api/poolApi";
import type { PersonRecord } from "../../api/types";
import { RestrictProcessingDialog } from "./RestrictProcessingDialog";

vi.mock("../../api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof poolApi>()),
  setProcessingRestriction: vi.fn(),
}));

const PERSON: PersonRecord = {
  personId: "person-1",
  fullName: "Noor Haddad",
  title: "Group CFO",
  companyName: "Aldar Properties",
  seniority: null,
  linkedinUrl: "https://www.linkedin.com/in/noor-haddad/",
  enrichedAt: null,
  locationCity: null,
  locationCountry: null,
  nationality: null,
  gender: null,
  yearsExperience: null,
  summary: null,
  compensation: {
    currency: null,
    baseSalary: null,
    bonus: null,
    allowances: null,
    longTermIncentive: null,
    noticePeriod: null,
    allowanceLines: [],
    longTermIncentiveTypes: [],
  },
  career: [],
  contacts: { emails: [], phones: [], emailsLookedUpAt: null, phonesLookedUpAt: null, source: null },
  positions: [],
  ownerUserId: null,
  doNotContact: null,
  processingRestriction: null,
  tagIds: [],
  source: "manual",
  addedAt: "2026-09-01T10:00:00Z",
  addedByUserId: null,
  addedByName: null,
};

function renderDialog(onRestricted = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <RestrictProcessingDialog person={PERSON} onClose={vi.fn()} onRestricted={onRestricted} />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return onRestricted;
}

describe("RestrictProcessingDialog", () => {
  beforeEach(() => {
    vi.mocked(poolApi.setProcessingRestriction).mockResolvedValue({
      ...PERSON,
      processingRestriction: {
        reason: "Asked what we hold",
        restrictedByUserId: "u1",
        restrictedByName: "Alok Kumar",
        restrictedAt: "2026-11-02T09:00:00Z",
      },
    });
  });
  afterEach(() => vi.restoreAllMocks());

  it("restricts only with a reason, leaving the suppression list unticked by default", async () => {
    const onRestricted = renderDialog();

    const confirm = screen.getByRole("button", { name: "Restrict" });
    expect(confirm).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: /suppression list/ })).not.toBeChecked();
    expect(screen.getByText(/LinkedIn profile is kept as one-way hashes/)).toBeInTheDocument();

    await userEvent.type(screen.getByRole("textbox", { name: /Reason/ }), "  Asked what we hold ");
    expect(screen.getByText("21 / 500")).toBeInTheDocument();
    await userEvent.click(confirm);

    await waitFor(() =>
      expect(poolApi.setProcessingRestriction).toHaveBeenCalledWith("person-1", true, "Asked what we hold", false),
    );
    expect(onRestricted).toHaveBeenCalledWith(expect.objectContaining({ processingRestriction: expect.anything() }));
  });

  it("may also suppress them, and says why when the server refuses", async () => {
    vi.mocked(poolApi.setProcessingRestriction).mockRejectedValue(
      new ApiRequestError({ code: "NOT_FOUND", detail: "gone", status: 404, correlationId: "none" }),
    );
    const onRestricted = renderDialog();

    await userEvent.type(screen.getByRole("textbox", { name: /Reason/ }), "Objected");
    await userEvent.click(screen.getByRole("checkbox", { name: /suppression list/ }));
    await userEvent.click(screen.getByRole("button", { name: "Restrict" }));

    await waitFor(() =>
      expect(poolApi.setProcessingRestriction).toHaveBeenCalledWith("person-1", true, "Objected", true),
    );
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(onRestricted).not.toHaveBeenCalled();
  });
});
