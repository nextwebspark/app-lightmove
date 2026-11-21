import { render, screen } from "@testing-library/react";
import { Suspense } from "react";
import { MemoryRouter } from "react-router-dom";
import { expect, it, vi } from "vitest";
import { LazyPlatformRoutes } from "./LazyPlatformRoutes";

vi.mock("./PlatformRoutes", () => {
  throw new Error("Failed to fetch dynamically imported module");
});

it("says the console couldn't load, and leads back to the workspace, when its chunk is gone", async () => {
  render(
    <MemoryRouter initialEntries={["/platform/templates"]}>
      <Suspense fallback={null}>
        <LazyPlatformRoutes />
      </Suspense>
    </MemoryRouter>,
  );

  expect(await screen.findByRole("heading", { name: "Platform couldn't load" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Reload" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Back to workspace" })).toHaveAttribute("href", "/");
});
