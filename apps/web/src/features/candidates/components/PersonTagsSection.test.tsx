import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import { isFittingPill, SHOWN, stubLayout } from "../../../test/stubLayout";
import * as poolApi from "../api/poolApi";
import type { CandidateTag, PersonRecord } from "../api/types";
import { PersonTagsSection } from "./PersonTagsSection";

vi.mock("../api/poolApi", async (importOriginal) => ({
  ...(await importOriginal<typeof poolApi>()),
  tagCatalog: vi.fn(),
  untagPerson: vi.fn(),
}));

const tagOf = (id: string, label: string): CandidateTag => ({ id, label, colour: "green", retired: false, holders: 1 });
const TAGS = [tagOf("t1", "Open to work"), tagOf("t2", "Passive"), tagOf("t3", "Referral")];
const person = { personId: "person-1", tagIds: ["t1", "t2", "t3"] } as unknown as PersonRecord;

function renderTags() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter>
          <PersonTagsSection person={person} />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("PersonTagsSection", () => {
  beforeEach(() => {
    vi.mocked(poolApi.tagCatalog).mockResolvedValue(TAGS);
    vi.mocked(poolApi.untagPerson).mockResolvedValue({ ...person, tagIds: ["t1", "t2"] });
  });
  afterEach(() => vi.restoreAllMocks());

  it("keeps the tags to one line, and every tag past it in reach of the +N chip", async () => {
    // A row of 200 holds one 80 tag beside the chip and "+ Tag"; the other two fold into "+2".
    stubLayout({ rowWidth: 200, widthOf: (element) => (isFittingPill(element) ? 80 : 40) });
    renderTags();

    expect(await screen.findByRole("button", { name: "Remove tag Open to work" })).toBeInTheDocument();
    expect(screen.queryByText("Referral", SHOWN)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Show all 3 tags" })).toHaveTextContent("+2");

    await userEvent.click(screen.getByRole("button", { name: "Show all 3 tags" }));
    const all = screen.getByRole("dialog", { name: "All tags" });
    for (const tag of TAGS) {
      expect(within(all).getByRole("button", { name: `Remove tag ${tag.label}` })).toBeInTheDocument();
    }

    await userEvent.click(within(all).getByRole("button", { name: "Remove tag Referral" }));
    await waitFor(() => expect(poolApi.untagPerson).toHaveBeenCalledWith("person-1", "t3"));
  });
});
