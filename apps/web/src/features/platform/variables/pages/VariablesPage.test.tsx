import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../../components/ui";
import { ApiRequestError } from "../../../../lib/apiClient";
import * as variableApi from "../api/variableApi";
import type { ApplicationVariable } from "../api/types";
import VariablesPage from "./VariablesPage";

vi.mock("../api/variableApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/variableApi")>()),
  listVariables: vi.fn(),
  getVariableHistory: vi.fn(),
  changeVariable: vi.fn(),
  resetVariable: vi.fn(),
}));

const variable = (overrides: Partial<ApplicationVariable> = {}): ApplicationVariable => ({
  key: "enrichment.sourcing.max-companies-per-run",
  group: "Executive sourcing",
  groupKeyPrefix: "enrichment.sourcing",
  label: "Companies per run",
  description: "How many companies one Find executives run works through.",
  value: 8,
  defaultValue: 5,
  min: 1,
  max: 25,
  unit: null,
  changed: true,
  lastChangedBy: "Noor Rahman",
  lastChangedAt: "2026-11-14T10:42:00Z",
  ...overrides,
});

const DEADLINE = variable({
  key: "enrichment.sourcing.run-deadline",
  label: "Run deadline",
  description: "How long a run may take before it stops with what it has.",
  value: 180,
  defaultValue: 180,
  min: 30,
  max: 600,
  unit: "seconds",
  changed: false,
  lastChangedBy: null,
  lastChangedAt: null,
});

const refusal = (code: string, status: number, fieldErrors: Record<string, string> = {}) =>
  new ApiRequestError({ code, detail: "", status, correlationId: "c", fieldErrors });

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={["/platform/variables"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <VariablesPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

const rowOf = async (label: string) => (await screen.findByRole("listitem", { name: label })) as HTMLElement;

describe("VariablesPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(variableApi.listVariables).mockResolvedValue([variable(), DEADLINE]);
  });

  it("lists each variable under its group with its value, default and who changed it last", async () => {
    renderPage();

    const companies = await rowOf("Companies per run");
    expect(screen.getByRole("heading", { name: "Executive sourcing" })).toBeInTheDocument();
    expect(screen.getByText("enrichment.sourcing.*")).toBeInTheDocument();
    expect(within(companies).getByText("Changed")).toBeInTheDocument();
    expect(within(companies).getByText("8")).toBeInTheDocument();
    expect(within(companies).getByText("5")).toBeInTheDocument();
    expect(within(companies).getByText("Noor Rahman")).toBeInTheDocument();
    expect(within(companies).getByRole("button", { name: "History of Companies per run" })).toBeInTheDocument();
    expect(within(companies).getByRole("button", { name: "Reset Companies per run" })).toBeInTheDocument();

    const deadline = await rowOf("Run deadline");
    expect(within(deadline).getAllByText("180 s")).toHaveLength(2);
    expect(within(deadline).getByText("Never changed")).toBeInTheDocument();
    expect(within(deadline).queryByText("Changed")).not.toBeInTheDocument();
    expect(within(deadline).queryByRole("button", { name: /History/ })).not.toBeInTheDocument();
    expect(within(deadline).queryByRole("button", { name: /Reset/ })).not.toBeInTheDocument();
  });

  it("says a refused read was refused, never that there are no variables", async () => {
    vi.mocked(variableApi.listVariables).mockRejectedValue(refusal("INTERNAL_ERROR", 500));
    renderPage();

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.queryByRole("listitem")).not.toBeInTheDocument();
  });

  it("asks a super admin with no second factor to set one up", async () => {
    vi.mocked(variableApi.listVariables).mockRejectedValue(refusal("PLATFORM_TWO_FACTOR_REQUIRED", 403));
    renderPage();

    expect(await screen.findByText(/Set up two-factor/)).toBeInTheDocument();
  });

  it("saves a new value with its note, shows it in the row and says it is live", async () => {
    const user = userEvent.setup();
    vi.mocked(variableApi.changeVariable).mockResolvedValue(variable({ value: 12 }));
    renderPage();

    const companies = await rowOf("Companies per run");
    await user.click(within(companies).getByRole("button", { name: "Edit Companies per run" }));
    expect(within(companies).getByText("Whole number, 1 to 25 · default 5")).toBeInTheDocument();
    const field = within(companies).getByRole("textbox", { name: /New value/ });
    await user.clear(field);
    await user.type(field, "12");
    await user.type(within(companies).getByRole("textbox", { name: /Change note/ }), "Larger groups this month");
    await user.click(within(companies).getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(variableApi.changeVariable).toHaveBeenCalledWith(
        "enrichment.sourcing.max-companies-per-run",
        "12",
        "Larger groups this month",
      ),
    );
    expect(await screen.findByText("Companies per run is now 12 on every instance")).toBeInTheDocument();
    expect(within(await rowOf("Companies per run")).getByText("12")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save" })).not.toBeInTheDocument();
  });

  it("names the unit in the toast of a variable that has one", async () => {
    const user = userEvent.setup();
    vi.mocked(variableApi.changeVariable).mockResolvedValue({ ...DEADLINE, value: 240, changed: true });
    renderPage();

    const deadline = await rowOf("Run deadline");
    await user.click(within(deadline).getByRole("button", { name: "Edit Run deadline" }));
    const field = within(deadline).getByRole("textbox", { name: /New value/ });
    await user.clear(field);
    await user.type(field, "240");
    await user.type(within(deadline).getByRole("textbox", { name: /Change note/ }), "Slow provider this week");
    await user.click(within(deadline).getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Run deadline is now 240 seconds on every instance")).toBeInTheDocument();
  });

  it("holds a value outside the range or a missing note on its own field, and sends nothing", async () => {
    const user = userEvent.setup();
    renderPage();

    const companies = await rowOf("Companies per run");
    await user.click(within(companies).getByRole("button", { name: "Edit Companies per run" }));
    const field = within(companies).getByRole("textbox", { name: /New value/ });
    await user.clear(field);
    await user.type(field, "40");
    await user.click(within(companies).getByRole("button", { name: "Save" }));

    expect(await within(companies).findByText("Enter a whole number from 1 to 25.")).toBeInTheDocument();
    expect(within(companies).getByText("Say why, for whoever reads the history later.")).toBeInTheDocument();
    expect(variableApi.changeVariable).not.toHaveBeenCalled();
  });

  it("shows the server's refusal of a value on the value's field", async () => {
    const user = userEvent.setup();
    vi.mocked(variableApi.changeVariable).mockRejectedValue(
      refusal("VALIDATION_FAILED", 400, { value: "That is its value now." }),
    );
    renderPage();

    const companies = await rowOf("Companies per run");
    await user.click(within(companies).getByRole("button", { name: "Edit Companies per run" }));
    await user.type(within(companies).getByRole("textbox", { name: /Change note/ }), "Tested");
    await user.click(within(companies).getByRole("button", { name: "Save" }));

    expect(await within(companies).findByText("That is its value now.")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(within(companies).getByRole("button", { name: "Save" })).toBeInTheDocument();
  });

  it("keeps the edit open with no banner when the code dialog was closed without a code", async () => {
    const user = userEvent.setup();
    vi.mocked(variableApi.changeVariable).mockRejectedValue(refusal("PLATFORM_STEP_UP_REQUIRED", 403));
    renderPage();

    const companies = await rowOf("Companies per run");
    await user.click(within(companies).getByRole("button", { name: "Edit Companies per run" }));
    await user.type(within(companies).getByRole("textbox", { name: /Change note/ }), "Tested");
    const field = within(companies).getByRole("textbox", { name: /New value/ });
    await user.clear(field);
    await user.type(field, "9");
    await user.click(within(companies).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(variableApi.changeVariable).toHaveBeenCalled());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(within(companies).getByRole("button", { name: "Save" })).toBeInTheDocument();
  });

  it("resets a variable only once a note is given, and says it is back on its default", async () => {
    const user = userEvent.setup();
    vi.mocked(variableApi.resetVariable).mockResolvedValue(variable({ value: 5, changed: false }));
    renderPage();

    await user.click(within(await rowOf("Companies per run")).getByRole("button", { name: "Reset Companies per run" }));
    const dialog = await screen.findByRole("dialog", { name: "Reset Companies per run to its default?" });
    expect(within(dialog).getByText(/It goes back from 8 to 5 on every instance/)).toBeInTheDocument();
    const confirm = within(dialog).getByRole("button", { name: "Reset to default" });
    expect(confirm).toBeDisabled();

    await user.type(within(dialog).getByRole("textbox", { name: /Change note/ }), "Eight ran past the deadline");
    await waitFor(() => expect(confirm).toBeEnabled());
    await user.click(confirm);

    await waitFor(() =>
      expect(variableApi.resetVariable).toHaveBeenCalledWith(
        "enrichment.sourcing.max-companies-per-run",
        "Eight ran past the deadline",
      ),
    );
    expect(await screen.findByText("Companies per run is back on its default")).toBeInTheDocument();
    expect(within(await rowOf("Companies per run")).queryByText("Changed")).not.toBeInTheDocument();
  });

  it("says why a reset was refused, in the dialog", async () => {
    const user = userEvent.setup();
    vi.mocked(variableApi.resetVariable).mockRejectedValue(refusal("PLATFORM_SETTING_NOT_CHANGED", 409));
    renderPage();

    await user.click(within(await rowOf("Companies per run")).getByRole("button", { name: "Reset Companies per run" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByRole("textbox", { name: /Change note/ }), "Back");
    await waitFor(() => expect(within(dialog).getByRole("button", { name: "Reset to default" })).toBeEnabled());
    await user.click(within(dialog).getByRole("button", { name: "Reset to default" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("That value is already on its default.");
  });

  it("opens a variable's history, newest first, over its shipped default", async () => {
    const user = userEvent.setup();
    vi.mocked(variableApi.getVariableHistory).mockResolvedValue([
      { oldValue: "10", newValue: "8", note: "Ten ran past the deadline on large groups.", changedBy: "Noor Rahman", changedAt: "2026-11-14T10:42:00Z" },
      { oldValue: "5", newValue: "10", note: "Trial for the Gulf energy positions.", changedBy: null, changedAt: "2026-11-02T09:15:00Z" },
    ]);
    renderPage();

    await user.click(within(await rowOf("Companies per run")).getByRole("button", { name: "History of Companies per run" }));

    const drawer = await screen.findByRole("dialog", { name: "History of Companies per run" });
    const notes = await within(drawer).findAllByText(/deadline|Trial/);
    expect(notes.map((note) => note.textContent)).toEqual([
      "Ten ran past the deadline on large groups.",
      "Trial for the Gulf energy positions.",
    ]);
    expect(within(drawer).getByText(/Noor Rahman/)).toBeInTheDocument();
    expect(within(drawer).getByText(/Former member/)).toBeInTheDocument();
    expect(within(drawer).getByText("Shipped default: 5")).toBeInTheDocument();
    expect(variableApi.getVariableHistory).toHaveBeenCalledWith(
      "enrichment.sourcing.max-companies-per-run",
      expect.anything(),
    );
  });
});
