import { fireEvent, screen } from "@testing-library/react";
import { afterAll, beforeEach, describe, expect, it, vi } from "vitest";
import { CAPTURE_PRIVACY_URL } from "../../captureLinks";
import { DEFAULT_CAPTURE_SETTINGS } from "../../domain/captureSettings";
import { installChromeStub } from "../../test/chromeStub";
import { CaptureSettingsScreen } from "./CaptureSettingsScreen";
import { PROJECTS, USER, renderPanel } from "./panelTestKit";

describe("the settings screen's privacy notice", () => {
  const openTab = vi.fn(async () => ({}) as chrome.tabs.Tab);

  beforeEach(() => {
    openTab.mockClear();
    installChromeStub().answer(() => ({ ok: true, value: DEFAULT_CAPTURE_SETTINGS }));
    (chrome.tabs as unknown as { create: typeof openTab }).create = openTab;
  });

  afterAll(() => vi.unstubAllGlobals());

  it("says what a capture reads and opens the website's account of it", () => {
    renderPanel(<CaptureSettingsScreen user={USER} projects={PROJECTS} onBack={vi.fn()} onSignOut={vi.fn()} />);

    expect(screen.getByText(/reads only the name and link of the LinkedIn page you save/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "How we handle it" }));

    expect(openTab).toHaveBeenCalledWith({ url: CAPTURE_PRIVACY_URL });
  });
});
