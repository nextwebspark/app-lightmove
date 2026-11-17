import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterAll, beforeEach, describe, expect, it, vi } from "vitest";
import { installChromeStub, type ChromeStub } from "../../test/chromeStub";
import { useProjectSelection } from "./useProjectSelection";

function position(id: string, viewerCanWork?: boolean | null) {
  return { id, positionTitle: id, clientName: null, clientLogoUrl: null, team: [], archivedAt: null, viewerCanWork };
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("the positions the panel files into", () => {
  let chrome: ChromeStub;
  let lastUsed: () => Promise<unknown> = async () => ({ ok: true, value: null });

  beforeEach(() => {
    lastUsed = async () => ({ ok: true, value: null });
    chrome = installChromeStub();
    chrome.answer((request) => {
      switch (request.kind) {
        case "listProjects":
          return {
            ok: true,
            value: [position("unseated", false), position("seated", true), position("older-api")],
          };
        case "lastUsedProject":
          return lastUsed();
        case "recentProjects":
          return { ok: true, value: [] };
        case "readSettings":
          return { ok: true, value: { defaultProjectId: null, closesAfterSave: false } };
        default:
          return { ok: false, code: "UNSTUBBED", message: request.kind };
      }
    });
  });

  afterAll(() => vi.unstubAllGlobals());

  // Staff browse every mandate; an unseated member was offered — and defaulted into — ones whose saves were refused.
  it("offers only the positions the consultant can work, and an older API's as before", async () => {
    const { result } = renderHook(() => useProjectSelection(), { wrapper });

    await waitFor(() => expect(result.current.selectedProjectId).not.toBeNull());
    expect(result.current.projects.map((each) => each.id)).toEqual(["seated", "older-api"]);
    expect(result.current.selectedProjectId).toBe("seated");
  });

  it("waits for the last-used position before choosing one", async () => {
    let answerLastUsed: (value: unknown) => void = () => undefined;
    lastUsed = () => new Promise((resolve) => (answerLastUsed = resolve));
    const { result } = renderHook(() => useProjectSelection(), { wrapper });

    await waitFor(() => expect(result.current.projects).toHaveLength(2));
    expect(result.current.selectedProjectId).toBeNull();
    answerLastUsed({ ok: true, value: "older-api" });

    await waitFor(() => expect(result.current.selectedProjectId).toBe("older-api"));
  });
});
