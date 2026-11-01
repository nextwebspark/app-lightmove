import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterAll, beforeEach, describe, expect, it, vi } from "vitest";
import { installChromeStub } from "../../test/chromeStub";
import { PersonPanel } from "./PersonPanel";
import { PIPELINE, PROJECTS, USER, candidate, pageRead, renderPanel } from "./panelTestKit";

type Request = { kind: string; [field: string]: unknown };

describe("a LinkedIn profile against the chosen position", () => {
  let requests: Request[] = [];
  let heldAnswer: unknown = null;
  let captureAnswer: { ok: boolean; value?: unknown; code?: string; message?: string } = { ok: true };
  let undoAnswer: unknown = { ok: true, value: null };

  beforeEach(() => {
    requests = [];
    heldAnswer = null;
    undoAnswer = { ok: true, value: null };
    captureAnswer = { ok: true, value: candidate({ status: "identified", enrichedAt: null }) };
    installChromeStub().answer((raw) => {
      const request = raw as Request;
      requests.push(request);
      switch (request.kind) {
        case "heldCandidate":
          return { ok: true, value: heldAnswer };
        case "readPipeline":
          return { ok: true, value: PIPELINE };
        case "listCandidateTags":
          return { ok: true, value: [] };
        case "readPersonTags":
          return { ok: true, value: { personId: "person-1", tagIds: [] } };
        case "listNotes":
          return { ok: true, value: [] };
        case "readSettings":
          return { ok: true, value: { defaultProjectId: null, closesAfterSave: false } };
        case "candidatePhoto":
          return { ok: true, value: null };
        case "captureCandidate":
          return captureAnswer;
        case "removeCandidate":
          return undoAnswer;
        default:
          return { ok: false, code: "UNSTUBBED", message: request.kind };
      }
    });
  });

  afterAll(() => vi.unstubAllGlobals());

  it("opens the record of someone the position already maps, with no form to file them again", async () => {
    heldAnswer = { candidate: candidate(), addedBy: { userId: "u2", name: "Sara Al-Mansour", avatarUrl: null }, companyLogoUrl: null };
    renderPanel(<PersonPanel page={pageRead()} projects={PROJECTS} user={USER} />);

    expect(await screen.findByText("Already on this position")).toBeDefined();
    expect(screen.getByText(/by Sara Al-Mansour · Approach/)).toBeDefined();
    expect(screen.getByText("Chief Financial Officer · ACWA Power")).toBeDefined();
    expect(screen.queryByRole("button", { name: /Add to position/ })).toBeNull();
    expect(requests.find((each) => each.kind === "heldCandidate")).toMatchObject({
      projectId: "p1",
      linkedinUrl: "https://www.linkedin.com/in/faisal-al-nuaimi/",
    });
  });

  it("opens a new note as a LinkedIn note, on the form and on the record alike", async () => {
    const { unmount } = renderPanel(<PersonPanel page={pageRead()} projects={PROJECTS} user={USER} />);
    await screen.findByRole("button", { name: /Add to position/ });
    expect(screen.getByRole("radio", { name: /LinkedIn/ }).getAttribute("aria-checked")).toBe("true");
    unmount();

    heldAnswer = { candidate: candidate(), addedBy: null, companyLogoUrl: null };
    renderPanel(<PersonPanel page={pageRead()} projects={PROJECTS} user={USER} />);
    await screen.findByText("Already on this position");
    expect(screen.getByRole("radio", { name: /LinkedIn/ }).getAttribute("aria-checked")).toBe("true");
  });

  it("offers the form for someone new, and files them at the workspace's own starting status", async () => {
    renderPanel(<PersonPanel page={pageRead()} projects={PROJECTS} user={USER} />);

    const add = await screen.findByRole("button", { name: /Add to position/ });
    await screen.findByText("Identified");
    heldAnswer = { candidate: candidate({ status: "identified", enrichedAt: null }), addedBy: null, companyLogoUrl: null };
    fireEvent.click(add);

    expect(await screen.findByText("Added to the position")).toBeDefined();
    const filed = requests.find((each) => each.kind === "captureCandidate") as unknown as { candidate: Record<string, unknown> };
    expect(filed.candidate).toMatchObject({ fullName: "Faisal Al Nuaimi", source: "extension" });
    expect(filed.candidate.status).toBeUndefined();
    expect(filed.candidate.note).toBeUndefined();
    expect(requests.some((each) => each.kind === "writeNote")).toBe(false);
  });

  it("opens the record a refused save names, when the position holds them under another door", async () => {
    captureAnswer = { ok: false, code: "CANDIDATE_ALREADY_MAPPED", message: "held" };
    renderPanel(<PersonPanel page={pageRead()} projects={PROJECTS} user={USER} />);

    const add = await screen.findByRole("button", { name: /Add to position/ });
    heldAnswer = { candidate: candidate(), addedBy: null, companyLogoUrl: null };
    fireEvent.click(add);

    await waitFor(() => expect(screen.getByText("Already on this position")).toBeDefined());
  });

  async function fileSomeoneNew() {
    renderPanel(<PersonPanel page={pageRead()} projects={PROJECTS} user={USER} />);
    const add = await screen.findByRole("button", { name: /Add to position/ });
    await screen.findByText("Identified");
    heldAnswer = { candidate: candidate({ status: "identified", enrichedAt: null }), addedBy: null, companyLogoUrl: null };
    fireEvent.click(add);
    await screen.findByText("Added to the position");
  }

  it("goes back to the form once an Undo takes the person off the position", async () => {
    await fileSomeoneNew();
    heldAnswer = null;

    fireEvent.click(screen.getByRole("button", { name: "Undo" }));

    expect(await screen.findByRole("button", { name: /Add to position/ })).toBeDefined();
    expect(requests.find((each) => each.kind === "removeCandidate")).toMatchObject({ projectId: "p1", candidateId: "c1" });
  });

  it("says why an Undo left the person on the position", async () => {
    undoAnswer = { ok: false, code: "CONFLICT", message: "Changed." };
    await fileSomeoneNew();

    fireEvent.click(screen.getByRole("button", { name: "Undo" }));

    expect((await screen.findByRole("alert")).textContent).toContain("Try Undo again");
    expect(screen.getByText("Added to the position")).toBeDefined();
  });
});
