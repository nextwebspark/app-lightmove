import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import type { CustomColumn } from "../../customcolumns/api/types";
import * as importApi from "../api/importApi";
import type { ImportPreview, ImportSummary } from "../api/importTypes";
import { ImportSpreadsheetDialog } from "./ImportSpreadsheetDialog";

vi.mock("../api/importApi", async (importOriginal) => ({
  ...(await importOriginal<typeof importApi>()),
  previewImport: vi.fn(),
  commitImport: vi.fn(),
}));

const ethnicity: CustomColumn = {
  id: "cc1",
  target: "candidate",
  fieldKey: "ethnicity",
  label: "Ethnicity",
  dataType: "text",
  displayOrder: 0,
  hidden: false,
};

/** Company mapped to a field, an unknown header proposed as a new column. The ordinary case. */
const preview: ImportPreview = {
  fileName: "longlist.csv",
  rowCount: 2,
  mappedByModel: true,
  availableFields: [
    { value: "companyName", label: "Company", target: "company" },
    { value: "candidateName", label: "Name", target: "candidate" },
    { value: "candidateEmail", label: "Email", target: "candidate" },
  ],
  columns: [
    {
      index: 0,
      header: "Organisation",
      valueShape: "short_text",
      sampleValues: ["ACWA Power"],
      mapping: {
        index: 0,
        header: "Organisation",
        targetField: "companyName",
        customFieldKey: null,
        customLabel: null,
        customTarget: null,
        customType: null,
      },
    },
    {
      index: 1,
      header: "Ethnicity",
      valueShape: "short_text",
      sampleValues: ["Lebanese"],
      mapping: {
        index: 1,
        header: "Ethnicity",
        targetField: null,
        customFieldKey: null,
        customLabel: "Ethnicity",
        customTarget: "candidate",
        customType: "text",
      },
    },
  ],
};

const summary: ImportSummary = {
  rowsRead: 2,
  companiesCreated: 2,
  companiesUpdated: 0,
  companiesSkipped: 0,
  candidatesCreated: 2,
  candidatesUpdated: 0,
  customColumnsCreated: ["Ethnicity"],
  rowErrors: [],
};

const onImported = vi.fn();

function renderDialog(customColumns: CustomColumn[] = []) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <ImportSpreadsheetDialog
          open
          projectId="p1"
          customColumns={customColumns}
          onClose={vi.fn()}
          onImported={onImported}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );
}

/** Uploading through the hidden input the dropzone drives — the same path a click-and-pick takes. */
async function choose(name = "longlist.csv") {
  const file = new File(["Organisation,Ethnicity\nACWA Power,Lebanese\n"], name, { type: "text/csv" });
  await userEvent.upload(screen.getByLabelText(/Spreadsheet to import/i), file);
  return file;
}

/**
 * The mapping step is the reason this dialog has three steps rather than one. A file's headers will
 * not match ours, and a mapping applied without being shown writes a consultant's data into the
 * wrong fields silently — so what it shows, and what a correction actually sends, is what these test.
 */
describe("ImportSpreadsheetDialog", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(importApi.previewImport).mockResolvedValue(preview);
    vi.mocked(importApi.commitImport).mockResolvedValue(summary);
  });

  it("shows every column of the file with what it will import as", async () => {
    renderDialog();
    await choose();

    expect(await screen.findByText("Organisation")).toBeInTheDocument();
    expect(screen.getByText("Ethnicity")).toBeInTheDocument();
    // The sample value comes from the browser's own file and never went to the model.
    expect(screen.getByText("ACWA Power")).toBeInTheDocument();
    expect(screen.getByLabelText(/What "Organisation" imports as/i)).toHaveValue("field:companyName");
  });

  it("commits the mapping the user did not touch", async () => {
    renderDialog();
    const file = await choose();

    await userEvent.click(await screen.findByRole("button", { name: /Import 2 rows/i }));

    await waitFor(() => expect(importApi.commitImport).toHaveBeenCalled());
    const [projectId, sent, columns] = vi.mocked(importApi.commitImport).mock.calls[0];
    expect(projectId).toBe("p1");
    // The same File object both calls carry — there is no import held open between the two.
    expect(sent).toBe(file);
    expect(columns[0].targetField).toBe("companyName");
    expect(columns[1].customLabel).toBe("Ethnicity");
  });

  it("sends a correction rather than the proposal", async () => {
    renderDialog();
    await choose();

    await userEvent.selectOptions(
      await screen.findByLabelText(/What "Ethnicity" imports as/i),
      "field:candidateName",
    );
    await userEvent.click(screen.getByRole("button", { name: /Import 2 rows/i }));

    await waitFor(() => expect(importApi.commitImport).toHaveBeenCalled());
    const columns = vi.mocked(importApi.commitImport).mock.calls[0][2];
    expect(columns[1].targetField).toBe("candidateName");
    // Cleared, not merely overridden: a stale label would have the server define a column nobody asked for.
    expect(columns[1].customLabel).toBeNull();
  });

  it("drops a column the user chose not to import", async () => {
    renderDialog();
    await choose();

    await userEvent.selectOptions(await screen.findByLabelText(/What "Ethnicity" imports as/i), "ignore");
    await userEvent.click(screen.getByRole("button", { name: /Import 2 rows/i }));

    await waitFor(() => expect(importApi.commitImport).toHaveBeenCalled());
    const columns = vi.mocked(importApi.commitImport).mock.calls[0][2];
    expect(columns[1].targetField).toBeNull();
    expect(columns[1].customLabel).toBeNull();
    expect(columns[1].customFieldKey).toBeNull();
  });

  it("offers a column the mandate already has, so a second import fills it", async () => {
    renderDialog([ethnicity]);
    await choose();

    const select = await screen.findByLabelText(/What "Ethnicity" imports as/i);
    await userEvent.selectOptions(select, "custom:ethnicity");
    await userEvent.click(screen.getByRole("button", { name: /Import 2 rows/i }));

    await waitFor(() => expect(importApi.commitImport).toHaveBeenCalled());
    const columns = vi.mocked(importApi.commitImport).mock.calls[0][2];
    expect(columns[1].customFieldKey).toBe("ethnicity");
    expect(columns[1].customLabel).toBeNull();
  });

  it("lets a new column be renamed and retyped before it is created", async () => {
    renderDialog();
    await choose();

    const name = await screen.findByLabelText(/Name of the new column for "Ethnicity"/i);
    await userEvent.clear(name);
    await userEvent.type(name, "Heritage");
    await userEvent.selectOptions(
      screen.getByLabelText(/What the new column for "Ethnicity" describes/i),
      "company",
    );
    await userEvent.click(screen.getByRole("button", { name: /Import 2 rows/i }));

    await waitFor(() => expect(importApi.commitImport).toHaveBeenCalled());
    const columns = vi.mocked(importApi.commitImport).mock.calls[0][2];
    expect(columns[1].customLabel).toBe("Heritage");
    expect(columns[1].customTarget).toBe("company");
  });

  it("says when the header matcher answered rather than the assistant", async () => {
    // Worth saying plainly: the fallback is confident about far less, and on a machine with no
    // Application Default Credentials it is what every user gets.
    vi.mocked(importApi.previewImport).mockResolvedValue({ ...preview, mappedByModel: false });
    renderDialog();
    await choose();

    expect(await screen.findByText(/matched by header name/i)).toBeInTheDocument();
  });

  it("reports what the import did, and what it skipped", async () => {
    vi.mocked(importApi.commitImport).mockResolvedValue({
      ...summary,
      companiesCreated: 1,
      candidatesCreated: 1,
      rowErrors: [{ rowNumber: 2, message: "row carries neither a company name nor a person's name" }],
    });
    renderDialog();
    await choose();
    await userEvent.click(await screen.findByRole("button", { name: /Import 2 rows/i }));

    expect(await screen.findByText(/1 row skipped/i)).toBeInTheDocument();
    expect(screen.getByText("Row 2")).toBeInTheDocument();
    expect(screen.getByText(/New column on this mandate: Ethnicity/i)).toBeInTheDocument();
    expect(onImported).toHaveBeenCalled();
  });

  it("keeps the user on the file step when the file cannot be read", async () => {
    vi.mocked(importApi.previewImport).mockRejectedValue(new Error("nope"));
    renderDialog();
    await choose("broken.csv");

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByLabelText(/Spreadsheet to import/i)).toBeInTheDocument();
    expect(importApi.commitImport).not.toHaveBeenCalled();
  });
});
