import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as signaturesApi from "../api/signaturesApi";
import type { EmailSignature, EmailSignatures } from "../api/signaturesApi";
import { useSignatureChoice } from "./useSignatureChoice";

vi.mock("../api/signaturesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/signaturesApi")>()),
  listSignatures: vi.fn(),
}));

function aSignature(id: string, isDefault: boolean): EmailSignature {
  return {
    id,
    name: id,
    layout: "MINIMAL",
    fields: { name: null, title: null, phone: null, linkedinUrl: null, company: null, website: null, address: null, mobile: null },
    showPhoto: false,
    photoShape: "CIRCLE",
    showLogo: false,
    showBookingLink: false,
    customBody: null,
    footerPictures: [],
    disclaimer: null,
    isDefault,
    updatedAt: "2026-11-01T10:00:00Z",
    html: `<div>${id}</div>`,
  };
}

function listed(signatures: EmailSignature[]): EmailSignatures {
  return { signatures, sources: {} as EmailSignatures["sources"], limit: 10 };
}

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      {children}
    </QueryClientProvider>
  );
}

/** Which signature an email carries: the default, ticked; none ticked where there is no default until one is picked. */
describe("useSignatureChoice", () => {
  beforeEach(() => vi.resetAllMocks());

  it("ticks the default, listed first", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue(listed([aSignature("short", false), aSignature("formal", true)]));
    const { result } = renderHook(() => useSignatureChoice(), { wrapper });

    await waitFor(() => expect(result.current.isLoaded).toBe(true));
    expect(result.current.signatures.map((signature) => signature.id)).toEqual(["formal", "short"]);
    expect(result.current.included).toBe(true);
    expect(result.current.signatureId).toBe("formal");
  });

  it("starts unticked where signatures are kept but none is the default, and picking one ticks it", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue(listed([aSignature("formal", false), aSignature("short", false)]));
    const { result } = renderHook(() => useSignatureChoice(), { wrapper });

    await waitFor(() => expect(result.current.isLoaded).toBe(true));
    expect(result.current.included).toBe(false);
    expect(result.current.signatureId).toBeNull();

    act(() => result.current.setIncluded(true));
    expect(result.current.signatureId).toBe("formal");

    act(() => result.current.choose("short"));
    expect(result.current.included).toBe(true);
    expect(result.current.signatureId).toBe("short");
  });

  it("sends none where the person keeps none", async () => {
    vi.mocked(signaturesApi.listSignatures).mockResolvedValue(listed([]));
    const { result } = renderHook(() => useSignatureChoice(), { wrapper });

    await waitFor(() => expect(result.current.isLoaded).toBe(true));
    expect(result.current.included).toBe(false);
    expect(result.current.signatureId).toBeNull();
  });
});
