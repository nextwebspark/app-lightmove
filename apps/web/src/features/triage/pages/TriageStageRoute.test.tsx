import { render, screen } from "@testing-library/react";
import { MemoryRouter, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { TriageStagePage } from "./TriageStagePage";

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/projects/:projectId" element={<Outlet context={{ project: { id: "p1" } }} />}>
          <Route path="companies/universe" element={<UniverseStub />} />
          <Route path="companies/:stage" element={<TriageStagePage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

function UniverseStub() {
  const { search } = useLocation();
  return <p>In universe page{search}</p>;
}

describe("a link to a stage that is switched off", () => {
  it.each(["shortlisted", "declined"])("lands /companies/%s on In universe", async (slug) => {
    renderAt(`/projects/p1/companies/${slug}`);

    expect(await screen.findByText("In universe page")).toBeInTheDocument();
  });

  it("keeps the link's query, so an executive it named still opens", async () => {
    renderAt("/projects/p1/companies/shortlisted?candidate=c1");

    expect(await screen.findByText("In universe page?candidate=c1")).toBeInTheDocument();
  });
});
