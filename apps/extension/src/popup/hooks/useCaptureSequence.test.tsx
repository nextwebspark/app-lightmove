import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterAll, describe, expect, it, vi } from "vitest";
import { installChromeStub } from "../../test/chromeStub";
import { useCapturePerson, useUndoCapture } from "./useCaptureSequence";

describe("filing a person with a note and tags", () => {
  afterAll(() => vi.unstubAllGlobals());

  it("files the row, then the note as Markdown with its kind and scope, then each tag — and keeps what failed", async () => {
    const kinds: { kind: string; [field: string]: unknown }[] = [];
    installChromeStub().answer((raw) => {
      const request = raw as { kind: string; [field: string]: unknown };
      kinds.push(request);
      if (request.kind === "captureCandidate") return { ok: true, value: { id: "c1", personId: "person-1" } };
      if (request.kind === "writeNote") return { ok: false, code: "VALIDATION_FAILED", message: "Too long." };
      if (request.kind === "tagPerson") {
        return request.tagId === "t2"
          ? { ok: false, code: "NOT_FOUND", message: "gone" }
          : { ok: true, value: { personId: "person-1", tagIds: ["t1"] } };
      }
      return { ok: false, code: "UNSTUBBED", message: request.kind };
    });
    const client = new QueryClient();
    const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
    const { result } = renderHook(() => useCapturePerson(), { wrapper });

    act(() =>
      result.current.mutate({
        projectId: "p1",
        candidate: { fullName: "Faisal Al Nuaimi", source: "extension" },
        note: { kind: "call", body: "## First call", format: "markdown", allPositions: true },
        tagIds: ["t1", "t2"],
      }),
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(kinds.map((each) => each.kind)).toEqual(["captureCandidate", "writeNote", "tagPerson", "tagPerson"]);
    expect(kinds[1]).toMatchObject({
      target: { subject: "person", projectId: "p1", candidateId: "c1" },
      note: { kind: "call", body: "## First call", format: "markdown", allPositions: true },
    });
    expect(result.current.data?.unsentNote).toMatchObject({ reason: "Too long." });
    expect(result.current.data?.untaggedTagIds).toEqual(["t2"]);
  });
});

describe("undoing a capture", () => {
  afterAll(() => vi.unstubAllGlobals());

  function undoWith(removeCandidateReply: unknown) {
    const kinds: { kind: string; [field: string]: unknown }[] = [];
    installChromeStub().answer((raw) => {
      const request = raw as { kind: string; [field: string]: unknown };
      kinds.push(request);
      if (request.kind === "removeNote") return { ok: true, value: null };
      if (request.kind === "restoreNote") return { ok: true, value: { id: "n1" } };
      if (request.kind === "removeCandidate") return removeCandidateReply;
      return { ok: false, code: "UNSTUBBED", message: request.kind };
    });
    const client = new QueryClient();
    const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
    const { result } = renderHook(() => useUndoCapture(), { wrapper });
    act(() => result.current.mutate({ projectId: "p1", candidateId: "c1", noteId: "n1" }));
    return { result, kinds };
  }

  it("puts the note back when the person stays, and says why", async () => {
    const { result, kinds } = undoWith({ ok: false, code: "CONFLICT", message: "Changed." });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(kinds.map((each) => each.kind)).toEqual(["removeNote", "removeCandidate", "restoreNote"]);
    expect(result.current.error?.code).toBe("CONFLICT");
  });

  it("counts a person already gone as undone", async () => {
    const { result, kinds } = undoWith({ ok: false, code: "NOT_FOUND", message: "Gone." });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(kinds.map((each) => each.kind)).toEqual(["removeNote", "removeCandidate"]);
  });
});
