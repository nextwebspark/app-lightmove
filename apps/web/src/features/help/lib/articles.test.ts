import { describe, expect, it } from "vitest";
import { articlesForPage, HELP_ARTICLES, searchArticles } from "./articles";

describe("help articles", () => {
  it("finds every article's text in docs/help", () => {
    for (const article of HELP_ARTICLES) {
      if (article.href) continue;
      expect(article.body, article.slug).toBeTruthy();
    }
  });

  it("never names a vendor, the deployment or the internal word triage", () => {
    for (const article of HELP_ARTICLES) {
      expect(`${article.title} ${article.summary} ${article.body ?? ""}`).not.toMatch(
        /\bNylas\b|\bthis deployment\b|\btriage\b|\bApollo\b|\bBright Data\b|\bContactOut\b/i,
      );
    }
  });

  it("offers the page's own help first", () => {
    expect(articlesForPage("/projects/p1/strategy").map((article) => article.slug)).toContain("filter-the-market");
    expect(articlesForPage("/projects/p1/companies/universe").map((article) => article.slug)).toContain(
      "find-executives",
    );
    expect(articlesForPage("/team")).toEqual([]);
  });

  it("searches titles, summaries and text, every word", () => {
    expect(searchArticles("spreadsheet").map((article) => article.slug)).toContain("import-spreadsheet");
    expect(searchArticles("reply stops")).toEqual(
      expect.arrayContaining([expect.objectContaining({ slug: "outreach-mailbox" })]),
    );
    expect(searchArticles("   ")).toEqual([]);
  });
});
