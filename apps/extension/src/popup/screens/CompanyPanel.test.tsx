import { fireEvent, screen } from "@testing-library/react";
import { afterAll, beforeEach, describe, expect, it, vi } from "vitest";
import { installChromeStub } from "../../test/chromeStub";
import { CompanyPanel } from "./CompanyPanel";
import { PROJECTS, USER, pageRead, renderPanel } from "./panelTestKit";

type Request = { kind: string; [field: string]: unknown };

const COMPANY_PAGE = pageRead({
  subject: "company",
  sourceUrl: "https://www.linkedin.com/company/acwa-power/",
  pageKey: "company:acwa-power",
});

function triaged(overrides: Record<string, unknown> = {}) {
  return {
    id: "t1", source: "extension", status: "inUniverse", companyName: "ACWA Power", industry: "Renewable energy",
    companyCity: "Riyadh", companyCountry: "Saudi Arabia", companyLinkedinUrl: "https://www.linkedin.com/company/acwa-power/",
    logoUrl: "https://logos.example/acwa.png", addedAt: "2026-10-03T10:00:00Z", addedByName: "Omar Khalid",
    ...overrides,
  };
}

describe("a LinkedIn company page against the chosen position", () => {
  let requests: Request[] = [];
  let heldAnswer: unknown = null;
  let undoAnswer: unknown = { ok: true, value: null };
  let captureAnswer: unknown = null;
  let heldThroughMarket: unknown = null;

  beforeEach(() => {
    requests = [];
    heldAnswer = null;
    undoAnswer = { ok: true, value: null };
    captureAnswer = null;
    heldThroughMarket = null;
    installChromeStub().answer((raw) => {
      const request = raw as Request;
      requests.push(request);
      switch (request.kind) {
        case "heldCompany":
          if (request.throughMarket && heldThroughMarket === "fails") {
            return { ok: false, code: "UNEXPECTED_RESPONSE", message: "The workspace answered 503." };
          }
          return { ok: true, value: request.throughMarket ? heldThroughMarket : heldAnswer };
        case "listNotes":
          return { ok: true, value: [] };
        case "readSettings":
          return { ok: true, value: { defaultProjectId: null, closesAfterSave: false } };
        case "captureCompany":
          return captureAnswer ?? { ok: true, value: triaged({ addedByName: null }) };
        case "removeTriageCompany":
          return undoAnswer;
        default:
          return { ok: false, code: "UNSTUBBED", message: request.kind };
      }
    });
  });

  afterAll(() => vi.unstubAllGlobals());

  it("files a new company into the universe, and nowhere else", async () => {
    renderPanel(<CompanyPanel page={COMPANY_PAGE} projects={PROJECTS} user={USER} />);

    fireEvent.click(await screen.findByRole("button", { name: /Add to universe/ }));

    expect(screen.queryByRole("button", { name: /shortlist/i })).toBeNull();
    expect(await screen.findByText("Added to the universe")).toBeDefined();
    const filed = requests.find((each) => each.kind === "captureCompany") as unknown as { capture: Record<string, unknown> };
    expect(filed.capture).toMatchObject({ status: "inUniverse", companyName: "ACWA Power", source: "extension" });
  });

  // Capture matched the page to a market row the position held under the market's name; the page's own slug and
  // name found nothing, so the refusal had no record to open.
  it("opens the market row a refused save names, asking through the market only then", async () => {
    captureAnswer = { ok: false, code: "TRIAGE_COMPANY_ALREADY_HELD", message: "held" };
    heldThroughMarket = triaged({ companyName: "ACWA Power Company" });
    renderPanel(<CompanyPanel page={COMPANY_PAGE} projects={PROJECTS} user={USER} />);

    const add = await screen.findByRole("button", { name: /Add to universe/ });
    expect(requests.some((each) => each.kind === "heldCompany" && each.throughMarket)).toBe(false);
    fireEvent.click(add);

    expect(await screen.findByText("Already in this position's universe")).toBeDefined();
    expect(requests.some((each) => each.kind === "heldCompany" && each.throughMarket)).toBe(true);
  });

  it("falls back to the plain re-check when asking through the market fails", async () => {
    captureAnswer = { ok: false, code: "TRIAGE_COMPANY_ALREADY_HELD", message: "held" };
    heldThroughMarket = "fails";
    renderPanel(<CompanyPanel page={COMPANY_PAGE} projects={PROJECTS} user={USER} />);

    const add = await screen.findByRole("button", { name: /Add to universe/ });
    heldAnswer = triaged();
    fireEvent.click(add);

    expect(await screen.findByText("Already in this position's universe")).toBeDefined();
  });

  it("opens a company the position holds on its record, its logo and who filed it", async () => {
    heldAnswer = triaged();
    const { container } = renderPanel(<CompanyPanel page={COMPANY_PAGE} projects={PROJECTS} user={USER} />);

    expect(await screen.findByText("Already in this position's universe")).toBeDefined();
    expect(screen.getByText(/by Omar Khalid · In universe/)).toBeDefined();
    expect(screen.getByText("Renewable energy · Riyadh, Saudi Arabia")).toBeDefined();
    expect(container.querySelector('img[src="https://logos.example/acwa.png"]')).not.toBeNull();
    expect(screen.queryByRole("button", { name: /Add to universe/ })).toBeNull();
  });

  it("says why an Undo left the company, rather than looking as if nothing happened", async () => {
    undoAnswer = { ok: false, code: "TRIAGE_COMPANY_HAS_EXECUTIVES", message: "Conflict" };
    renderPanel(<CompanyPanel page={COMPANY_PAGE} projects={PROJECTS} user={USER} />);
    fireEvent.click(await screen.findByRole("button", { name: /Add to universe/ }));

    fireEvent.click(await screen.findByRole("button", { name: "Undo" }));

    expect((await screen.findByRole("alert")).textContent).toContain("Someone has been added at this company since");
    expect(screen.getByText("Added to the universe")).toBeDefined();
  });

  it("goes back to the form once the Undo lands", async () => {
    renderPanel(<CompanyPanel page={COMPANY_PAGE} projects={PROJECTS} user={USER} />);
    fireEvent.click(await screen.findByRole("button", { name: /Add to universe/ }));

    fireEvent.click(await screen.findByRole("button", { name: "Undo" }));

    expect(await screen.findByRole("button", { name: /Add to universe/ })).toBeDefined();
    expect(requests.find((each) => each.kind === "removeTriageCompany")).toMatchObject({ projectId: "p1", triageCompanyId: "t1" });
  });
});
