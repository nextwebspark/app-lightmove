import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterAll, beforeEach, describe, expect, it, vi } from "vitest";
import { installChromeStub } from "../../test/chromeStub";
import type { ActivePage } from "../hooks/useActivePage";
import type { ProjectSelection } from "../hooks/useProjectSelection";
import { CaptureCompanyScreen } from "./CaptureCompanyScreen";

const PROJECTS: ProjectSelection = {
  projects: [{ id: "p1", positionTitle: "Group CFO", clientName: "Acme" }],
  selectedProjectId: "p1",
  selectedProjectName: "Group CFO",
  selectProject: () => undefined,
  isLoading: false,
};

function pageRead(overrides: Partial<ActivePage> = {}): ActivePage {
  return {
    subject: "company",
    person: null,
    company: { companyName: "Al Rawabi", linkedinUrl: "https://www.linkedin.com/company/al-rawabi/" },
    sourceUrl: "https://www.linkedin.com/company/al-rawabi/",
    pageKey: "company:al-rawabi",
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
  const view = render(<CaptureCompanyScreen page={page} projects={PROJECTS} />, { wrapper });
  return {
    companyName: () => screen.getByLabelText("Company name") as HTMLInputElement,
    rerenderWith: (next: ActivePage) => view.rerender(<CaptureCompanyScreen page={next} projects={PROJECTS} />),
  };
}

describe("the company the panel offers to capture", () => {
  beforeEach(() => {
    const chrome = installChromeStub();
    chrome.answer(() => ({ ok: true, value: { closesAfterSave: false, isPageTypeDetected: true } }));
  });

  afterAll(() => vi.unstubAllGlobals());

  it("shows the name LinkedIn gave, and does not offer it for editing", () => {
    const view = renderScreen(pageRead());

    expect(view.companyName().value).toBe("Al Rawabi");
    expect(view.companyName().readOnly).toBe(true);
  });

  it("leaves the name typeable when the page named nobody, so the row can still be filed", async () => {
    const view = renderScreen(pageRead({ company: { companyName: null, linkedinUrl: null } }));
    expect(view.companyName().readOnly).toBe(false);

    fireEvent.change(view.companyName(), { target: { value: "Al Rawabi Dairy" } });
    view.rerenderWith(pageRead());

    await waitFor(() => expect(view.companyName().value).toBe("Al Rawabi Dairy"));
    expect(view.companyName().readOnly).toBe(false);
  });

  it("clears a typed name when the panel moves to another company", async () => {
    const view = renderScreen(pageRead({ company: { companyName: null, linkedinUrl: null } }));
    fireEvent.change(view.companyName(), { target: { value: "Typed By Hand" } });

    view.rerenderWith(
      pageRead({ pageKey: "company:zenith", company: { companyName: "Zenith", linkedinUrl: null } }),
    );

    await waitFor(() => expect(view.companyName().value).toBe("Zenith"));
    expect(view.companyName().readOnly).toBe(true);
  });
});
