import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { PersonResearchDetails } from "../api/types";
import type { ProfileSection } from "../lib/useProfileSections";
import { ResearchedProfileSections } from "./ResearchedProfileSections";

const open = (id: ProfileSection) => ({ id, open: true, onToggle: () => {} });

const DETAILS: PersonResearchDetails = {
  headline: null,
  industry: "Retail",
  jobFunction: null,
  seniority: "CXO",
  workStatus: null,
  followers: 12345,
  connections: 500,
  updatedAt: null,
  links: [],
  certifications: [{ title: "Chartered Director", subtitle: "IoD", period: "2020", url: null, description: null }],
  publications: [],
  projects: [],
  volunteering: [],
  contactAvailability: null,
  company: null,
};

describe("ResearchedProfileSections", () => {
  it("draws the profile facts research stated and each kind of item it listed", () => {
    render(<ResearchedProfileSections details={DETAILS} fold={open} />);

    expect(screen.getByText("Profile")).toBeInTheDocument();
    expect(screen.getByText("12,345")).toBeInTheDocument();
    expect(screen.getByText("Certifications")).toBeInTheDocument();
    expect(screen.getByText("Chartered Director")).toBeInTheDocument();
    expect(screen.queryByText("Publications")).not.toBeInTheDocument();
  });

  it("draws nothing for a person nobody researched", () => {
    const { container } = render(<ResearchedProfileSections details={null} fold={open} />);

    expect(container).toBeEmptyDOMElement();
  });
});
