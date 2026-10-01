import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as poolApi from "../../candidates/api/poolApi";
import type { CandidateTag } from "../../candidates/api/types";
import { SettingsCandidateTagsPage } from "./SettingsCandidateTagsPage";

vi.mock("../../candidates/api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/poolApi")>()),
  tagCatalog: vi.fn(),
  createTag: vi.fn(),
  updateTag: vi.fn(),
}));

const passive: CandidateTag = { id: "t1", label: "Passive", colour: "neutral", retired: false, holders: 2 };
const boardReady: CandidateTag = { id: "t2", label: "Board-ready", colour: "neutral", retired: true, holders: 0 };

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <SettingsCandidateTagsPage />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("SettingsCandidateTagsPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(poolApi.tagCatalog).mockResolvedValue([passive, boardReady]);
  });

  it("lists each tag with how many people hold it, a retired one marked", async () => {
    renderPage();

    expect(await screen.findByText("2 people")).toBeInTheDocument();
    expect(screen.getByText("0 people · retired")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Restore" })).toBeInTheDocument();
  });

  it("renames, recolours and retires a tag", async () => {
    vi.mocked(poolApi.updateTag).mockResolvedValue(passive);
    renderPage();
    await screen.findByText("2 people");
    const row = screen.getByText("2 people").closest("li") as HTMLElement;

    await userEvent.click(within(row).getByRole("button", { name: "Rename" }));
    await userEvent.clear(within(row).getByLabelText("Rename Passive"));
    await userEvent.type(within(row).getByLabelText("Rename Passive"), "Not looking");
    await userEvent.click(within(row).getByRole("button", { name: "Save" }));
    await waitFor(() => expect(poolApi.updateTag).toHaveBeenCalledWith("t1", { label: "Not looking" }));
    expect(await screen.findByText("Renamed on every person who has it")).toBeInTheDocument();

    await userEvent.click(within(row).getByRole("radio", { name: "Violet" }));
    await waitFor(() => expect(poolApi.updateTag).toHaveBeenCalledWith("t1", { colour: "violet" }));

    await userEvent.click(within(row).getByRole("button", { name: "Retire" }));
    await waitFor(() => expect(poolApi.updateTag).toHaveBeenCalledWith("t1", { retired: true }));
  });

  it("says a tag already exists in the words the mockup uses", async () => {
    vi.mocked(poolApi.createTag).mockRejectedValue(
      new ApiRequestError({ code: "CANDIDATE_TAG_EXISTS", detail: "exists", status: 409, correlationId: "c1" }),
    );
    renderPage();
    await screen.findByText("2 people");

    await userEvent.type(screen.getByLabelText("New tag"), "passive");
    await userEvent.click(screen.getByRole("button", { name: "Add tag" }));

    expect(await screen.findByText("That tag already exists")).toBeInTheDocument();
  });
});
