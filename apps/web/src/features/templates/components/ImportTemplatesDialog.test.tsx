import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as templateApi from "../api/templateAdminApi";
import { ImportTemplatesDialog } from "./ImportTemplatesDialog";

vi.mock("../api/templateAdminApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/templateAdminApi")>()),
  previewImport: vi.fn(),
  commitImport: vi.fn(),
}));

const file = new File(['{"format":"lightmove.position-templates"}'], "templates.json", { type: "application/json" });

const renderDialog = (onImported = vi.fn()) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ToastProvider>
        <ImportTemplatesDialog open scope="workspace" onClose={vi.fn()} onImported={onImported} />
      </ToastProvider>
    </QueryClientProvider>,
  );

describe("ImportTemplatesDialog", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("shows what each template would do, and will not import a file with an invalid one", async () => {
    vi.mocked(templateApi.previewImport).mockResolvedValue({
      committed: false,
      rows: [
        { code: "head-of-treasury", title: "Head of Treasury", action: "CREATE", problems: [] },
        {
          code: "head-of-tax",
          title: "Head of Tax",
          action: "INVALID",
          problems: [{ field: "body.bonusBais", message: "Not a template field" }],
        },
      ],
    });

    renderDialog();
    await userEvent.upload(screen.getByLabelText("Choose a template file"), file);

    expect(await screen.findByText("body.bonusBais — Not a template field")).toBeInTheDocument();
    expect(screen.getByText("1 create · 1 invalid")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Import" })).toBeDisabled();
    expect(templateApi.commitImport).not.toHaveBeenCalled();
  });

  it("imports a clean file, sending the same file again, and counts only what it writes", async () => {
    const result = {
      rows: [
        { code: "chief-financial-officer", title: "Chief Financial Officer", action: "CUSTOMISE" as const, problems: [] },
        { code: "head-of-treasury", title: "Head of Treasury", action: "CREATE" as const, problems: [] },
        { code: "group-treasury-lead", title: "Group Treasury Lead", action: "UNCHANGED" as const, problems: [] },
      ],
    };
    vi.mocked(templateApi.previewImport).mockResolvedValue({ committed: false, ...result });
    vi.mocked(templateApi.commitImport).mockResolvedValue({ committed: true, ...result });
    const onImported = vi.fn();

    renderDialog(onImported);
    await userEvent.upload(screen.getByLabelText("Choose a template file"), file);
    await userEvent.click(await screen.findByRole("button", { name: "Import 2 templates" }));

    expect(templateApi.commitImport).toHaveBeenCalledWith("workspace", file);
    expect(onImported).toHaveBeenCalled();
  });
});
