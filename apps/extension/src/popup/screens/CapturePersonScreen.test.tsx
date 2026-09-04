import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterAll, beforeEach, describe, expect, it, vi } from "vitest";
import { installChromeStub } from "../../test/chromeStub";
import type { ActivePage } from "../hooks/useActivePage";
import type { ProjectSelection } from "../hooks/useProjectSelection";
import { CapturePersonScreen } from "./CapturePersonScreen";

const PROJECTS: ProjectSelection = {
  projects: [{ id: "p1", positionTitle: "Group CFO", clientName: "Acme" }],
  selectedProjectId: "p1",
  selectedProjectName: "Group CFO",
  selectProject: () => undefined,
  isLoading: false,
};

function pageRead(overrides: Partial<ActivePage> = {}): ActivePage {
  return {
    subject: "person",
    person: { fullName: "Amira Haddad", linkedinUrl: "https://www.linkedin.com/in/amira-haddad/" },
    company: null,
    sourceUrl: "https://www.linkedin.com/in/amira-haddad/",
    pageKey: "person:amira-haddad",
    isReading: false,
    hasReadOnce: true,
    readError: null,
    rescan: async () => undefined,
    ...overrides,
  } as ActivePage;
}

function renderScreen(page: ActivePage) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const view = render(<CapturePersonScreen page={page} projects={PROJECTS} />, { wrapper });
  return {
    fullName: () => screen.getByLabelText("Full name") as HTMLInputElement,
    rerenderWith: (next: ActivePage) => view.rerender(<CapturePersonScreen page={next} projects={PROJECTS} />),
  };
}

describe("the person the panel offers to capture", () => {
  beforeEach(() => {
    const chrome = installChromeStub();
    chrome.answer(() => ({ ok: true, value: { closesAfterSave: false, isPageTypeDetected: true } }));
  });

  afterAll(() => vi.unstubAllGlobals());

  it("shows the name LinkedIn gave, and does not offer it for editing", () => {
    const view = renderScreen(pageRead());

    expect(view.fullName().value).toBe("Amira Haddad");
    expect(view.fullName().readOnly).toBe(true);
  });

  it("leaves the name typeable when the page named nobody, so the row can still be filed", () => {
    // The deliberate blank: a page the extractor missed, or a read the panel gave up on past its
    // deadline. `canSave` gates on the name, so locking this would be a row nobody can save.
    const view = renderScreen(pageRead({ person: { fullName: null, linkedinUrl: null } }));

    expect(view.fullName().readOnly).toBe(false);
  });

  it("takes a corrected name from a later read of the same page", async () => {
    const view = renderScreen(pageRead({ person: { fullName: null, linkedinUrl: null } }));
    expect(view.fullName().value).toBe("");

    view.rerenderWith(pageRead());

    await waitFor(() => expect(view.fullName().value).toBe("Amira Haddad"));
    expect(view.fullName().readOnly).toBe(true);
  });

  it("keeps a name the consultant typed into a blank field, and keeps it theirs", () => {
    const view = renderScreen(pageRead({ person: { fullName: null, linkedinUrl: null } }));
    fireEvent.change(view.fullName(), { target: { value: "Amira H" } });

    view.rerenderWith(pageRead());

    expect(view.fullName().value).toBe("Amira H");
    // Still theirs: a read landing late must not lock them out of the correction they just made.
    expect(view.fullName().readOnly).toBe(false);
  });

  it("clears a typed name when the panel moves to another profile", async () => {
    const view = renderScreen(pageRead({ person: { fullName: null, linkedinUrl: null } }));
    fireEvent.change(view.fullName(), { target: { value: "Typed By Hand" } });

    view.rerenderWith(
      pageRead({ pageKey: "person:bilal-nasser", person: { fullName: "Bilal Nasser", linkedinUrl: null } }),
    );

    await waitFor(() => expect(view.fullName().value).toBe("Bilal Nasser"));
    expect(view.fullName().readOnly).toBe(true);
  });

  it("keeps the same person when only the address gains a tracking parameter", () => {
    const view = renderScreen(pageRead({ person: { fullName: null, linkedinUrl: null } }));
    fireEvent.change(view.fullName(), { target: { value: "Amira Haddad-Nasser" } });

    view.rerenderWith(
      pageRead({
        sourceUrl: "https://www.linkedin.com/in/amira-haddad/?trk=nav",
        person: { fullName: null, linkedinUrl: null },
      }),
    );

    expect(view.fullName().value).toBe("Amira Haddad-Nasser");
  });

  it("keeps the note typeable whatever the read said — it is the one field that is written", () => {
    const view = renderScreen(pageRead());
    const note = screen.getByLabelText("Notes") as HTMLTextAreaElement;

    fireEvent.change(note, { target: { value: "Met at the summit" } });

    expect(note.readOnly).toBe(false);
    expect(note.value).toBe("Met at the summit");
    expect(view.fullName().readOnly).toBe(true);
  });

  it("keeps the form and a note being typed on screen while it re-reads", () => {
    const view = renderScreen(pageRead());
    fireEvent.change(screen.getByLabelText("Notes"), { target: { value: "Met at the summit" } });

    view.rerenderWith(pageRead({ isReading: true }));

    expect(view.fullName()).toBeTruthy();
    expect((screen.getByLabelText("Notes") as HTMLTextAreaElement).value).toBe("Met at the summit");
  });
});
