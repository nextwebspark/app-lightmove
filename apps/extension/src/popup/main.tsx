import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CapturePopup } from "./CapturePopup";
import "../styles/theme.css";

/**
 * A popup is opened, used and destroyed in seconds, so the cache is tuned for that rather than for a
 * long-lived page: nothing is refetched on window focus (there is no focus to regain), and a failed
 * request is not retried behind the consultant's back — they can see the error and press the button
 * again, which is faster and clearer than a silent retry against an API that just said no.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false, staleTime: 30_000 },
    mutations: { retry: false },
  },
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <CapturePopup />
    </QueryClientProvider>
  </StrictMode>,
);
