import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ProjectSelect } from "./ProjectSelect";

/**
 * Pins the option's text against the server's own shape.
 *
 * This shipped reading `project.name`, which `ProjectResponse` does not have — every mandate in the
 * dropdown rendered "undefined — Acme". Saving still worked, because the value is the id, so nothing
 * failed loudly. A test on the label is what makes that drift a red build.
 */
describe("the project dropdown", () => {
  it("names each mandate by its role and its client", () => {
    render(
      <ProjectSelect
        projects={[{ id: "p1", positionTitle: "Chief Operating Officer", clientName: "Al Rawabi Dairy" }]}
        selectedProjectId="p1"
        onSelect={vi.fn()}
        isLoading={false}
      />,
    );

    expect(screen.getByRole("option", { name: "Chief Operating Officer — Al Rawabi Dairy" })).toBeDefined();
  });

  it("falls back to the role alone when a mandate has no client name", () => {
    render(
      <ProjectSelect
        projects={[{ id: "p1", positionTitle: "Group CFO", clientName: null }]}
        selectedProjectId="p1"
        onSelect={vi.fn()}
        isLoading={false}
      />,
    );

    expect(screen.getByRole("option", { name: "Group CFO" })).toBeDefined();
  });
});
