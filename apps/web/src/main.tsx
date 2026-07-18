import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { AppRoutes } from "./app/routes";
import { ToastProvider } from "./components/ui";
import { AuthProvider } from "./features/auth/AuthProvider";
import { applyStoredTheme } from "./features/theme/useTheme";
import "./styles/global.css";

// Before the first paint, so a dark-mode user is not flashed a white login screen on the way in.
applyStoredTheme();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // The apiClient already retries a 401 once, after refreshing. A second retry on top of that
      // would only replay genuine failures — a 403, a 409 — slowing the error down without changing it.
      retry: false,
      refetchOnWindowFocus: false,
      staleTime: 30_000,
    },
  },
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {/* Inside the router, because AuthProvider's children navigate. */}
        <AuthProvider>
          <ToastProvider>
            <AppRoutes />
          </ToastProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
