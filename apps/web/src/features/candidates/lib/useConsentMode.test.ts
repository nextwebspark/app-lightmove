import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { beforeEach, expect, it, vi } from "vitest";
import * as candidatePrivacyApi from "../api/candidatePrivacyApi";
import type { CandidatePrivacySettings } from "../api/candidatePrivacyApi";
import * as dataProtectionApi from "../api/dataProtectionApi";
import { useConsentMode } from "./useConsentMode";

vi.mock("../api/dataProtectionApi", async (importOriginal) => ({
  ...(await importOriginal<typeof dataProtectionApi>()),
  getDataProtectionConfig: vi.fn(),
}));

vi.mock("../api/candidatePrivacyApi", async (importOriginal) => ({
  ...(await importOriginal<typeof candidatePrivacyApi>()),
  getCandidatePrivacy: vi.fn(),
}));

const FIRM: CandidatePrivacySettings = {
  controllerName: "Meridian Search Partners FZ-LLC",
  privacyContactEmail: "privacy@meridian.example",
  noticeUrl: "https://meridian.example/privacy",
  defaultLawfulBasis: "legitimate_interest",
  readyForNotices: true,
  privacyMode: "consent",
  keepOnFile: true,
  keepOnFileMonths: 24,
  shareWithClient: true,
  marketing: false,
  renewalDaysBefore: 30,
  readyForConsent: true,
};

function renderConsentMode() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client }, children);
  return renderHook(() => useConsentMode(), { wrapper });
}

beforeEach(() => {
  vi.mocked(dataProtectionApi.getDataProtectionConfig).mockResolvedValue({ enabled: true });
});

it("is on when the workspace asks for consent and has a purpose to ask from the Candidates screens", async () => {
  vi.mocked(candidatePrivacyApi.getCandidatePrivacy).mockResolvedValue(FIRM);
  const { result } = renderConsentMode();

  await waitFor(() => expect(result.current.firm).toBeDefined());
  expect(result.current.on).toBe(true);
});

it("stays off when the only purpose on is a share, which is asked from its position", async () => {
  vi.mocked(candidatePrivacyApi.getCandidatePrivacy).mockResolvedValue({ ...FIRM, keepOnFile: false });
  const { result } = renderConsentMode();

  await waitFor(() => expect(result.current.firm).toBeDefined());
  expect(result.current.on).toBe(false);
});

it("stays off while the server says consent is not ready", async () => {
  vi.mocked(candidatePrivacyApi.getCandidatePrivacy).mockResolvedValue({ ...FIRM, readyForConsent: false });
  const { result } = renderConsentMode();

  await waitFor(() => expect(result.current.firm).toBeDefined());
  expect(result.current.on).toBe(false);
});
