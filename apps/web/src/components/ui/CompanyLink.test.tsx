import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CompanyLink } from "./CompanyLink";

/**
 * The Links cell renders whatever a company snapshot carries, and a snapshot can now be written by a
 * consultant or by the browser plugin rather than only by the Apollo export. So what reaches this
 * component is no longer guaranteed to be a URL a browser will follow.
 */
describe("CompanyLink", () => {
  const render1 = (url: string | null) =>
    render(<CompanyLink url={url} icon="M0 0" label="website" companyName="ACWA Power" />);

  it("links an absolute http(s) address", () => {
    render1("https://acwapower.com");

    expect(screen.getByRole("link", { name: /ACWA Power on website/i })).toHaveAttribute(
      "href",
      "https://acwapower.com",
    );
  });

  it("promotes a bare host rather than dropping it", () => {
    render1("acwapower.com");

    // Plenty of Apollo rows publish a bare host. Left alone it would be a *relative* href and navigate
    // inside the SPA, so it is promoted — dropping it would lose the icon on a real company site.
    expect(screen.getByRole("link", { name: /ACWA Power on website/i })).toHaveAttribute(
      "href",
      "https://acwapower.com",
    );
  });

  it("renders nothing for a scheme a browser should not follow", () => {
    render1("javascript:alert(1)");

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("renders nothing when the company published no address", () => {
    render1(null);

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
