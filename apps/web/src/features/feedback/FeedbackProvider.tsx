import { createContext, useCallback, useContext, useState, type ReactNode } from "react";
import { captureScreen } from "./lib/captureScreen";
import { collectReportContext } from "./lib/reportContext";
import { FeedbackLauncher } from "./components/FeedbackLauncher";
import { FeedbackPanel } from "./components/FeedbackPanel";
import type { FeedbackContext as ReportContext } from "./api/types";

/**
 * The bug reporter, mounted once for the whole app.
 *
 * <p>It lives above the router rather than inside a screen because it has to work on *every* screen,
 * signed in or not — and the pre-login screens are exactly where a UAT tester most needs it and has
 * the least to report with.
 *
 * <p>The trigger is in two places and the panel is in one. Before a workspace exists there is no
 * shell to put a button in, so the launcher floats at the right edge; inside the app the sidebar
 * carries the row and this renders no launcher at all. Both call {@link useFeedback}.
 */

interface FeedbackValue {
  /** Captures the screen behind the form, then opens it. Safe to call while already capturing. */
  open: () => void;
  isCapturing: boolean;
}

const FeedbackControls = createContext<FeedbackValue>({ open: () => {}, isCapturing: false });

export function useFeedback(): FeedbackValue {
  return useContext(FeedbackControls);
}

/** What the widget captured at the moment it was opened, held until the panel closes. */
interface OpenReport {
  screenshot: Blob | null;
  context: ReportContext;
}

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [report, setReport] = useState<OpenReport | null>(null);
  const [isCapturing, setCapturing] = useState(false);

  /**
   * The capture happens *before* the panel renders, and that ordering is the feature: the tester is
   * reporting on the screen they were looking at, and a screenshot with our own form across the
   * middle of it is a screenshot of the wrong thing.
   *
   * The cost is the half-second the button spends spinning. Opening first and capturing after would
   * be snappier and would photograph the overlay, which is worse.
   */
  const open = useCallback(async () => {
    if (isCapturing) return;

    setCapturing(true);
    const context = collectReportContext();
    const screenshot = await captureScreen();
    setCapturing(false);
    setReport({ screenshot, context });
  }, [isCapturing]);

  const close = useCallback(() => setReport(null), []);

  return (
    <FeedbackControls.Provider value={{ open: () => void open(), isCapturing }}>
      {children}
      <FeedbackLauncher />
      {report && (
        <FeedbackPanel
          screenshot={report.screenshot}
          context={report.context}
          onClose={close}
        />
      )}
    </FeedbackControls.Provider>
  );
}
