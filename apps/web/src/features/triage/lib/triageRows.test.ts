import { describe, expect, it } from "vitest";
import type { Candidate } from "../../candidates/api/types";
import type { TriageCompany } from "../api/types";
import { toTriageRows, triageRowId } from "./triageRows";

const company = (id: string, companyName: string): TriageCompany =>
  ({ id, companyName, status: "inUniverse", source: "strategy" }) as TriageCompany;

const person = (id: string, fullName: string, triageCompanyId: string | null): Candidate =>
  ({ id, fullName, triageCompanyId, companyName: null }) as Candidate;

/**
 * The rule the Companies grid is built on: a line is a person at a company, not a company. It lives
 * apart from the columns because it is the one piece of this screen with an answer that can be wrong
 * rather than merely ugly — a company silently dropped from the page is a company nobody triages.
 */
describe("toTriageRows", () => {
  it("keeps a company with nobody mapped as one line, so its empty slot is visible", () => {
    const rows = toTriageRows([company("a", "Almarai")], []);

    expect(rows).toHaveLength(1);
    expect(rows[0].candidate).toBeNull();
    expect(rows[0].company?.companyName).toBe("Almarai");
  });

  it("gives each executive at a company its own line, numbered within that company", () => {
    const rows = toTriageRows(
      [company("a", "Almarai")],
      [person("c1", "Omar Haddad", "a"), person("c2", "Yasmin El-Sayed", "a")],
    );

    expect(rows.map((row) => row.candidate?.fullName)).toEqual(["Omar Haddad", "Yasmin El-Sayed"]);
    expect(rows.map((row) => row.position)).toEqual([1, 2]);
    expect(rows.every((row) => row.siblings === 2)).toBe(true);
  });

  it("keeps a company's lines contiguous and in the server's company order", () => {
    const rows = toTriageRows(
      [company("a", "Almarai"), company("b", "NADEC")],
      [person("c1", "Omar Haddad", "a"), person("c9", "Wei Ling Tan", "b"), person("c2", "Yasmin El-Sayed", "a")],
    );

    // Paging and sorting are the server's, and they are over companies — so the companies keep their
    // order and each one's people follow it, however the candidate read happened to come back.
    expect(rows.map((row) => row.company?.companyName)).toEqual([
      "Almarai",
      "Almarai",
      "NADEC",
    ]);
  });

  it("ignores people mapped to a company that is not on this page", () => {
    const rows = toTriageRows([company("a", "Almarai")], [person("c1", "Omar Haddad", "elsewhere")]);

    // The grid is paged by company: a person at a company on page 3 belongs on page 3.
    expect(rows).toHaveLength(1);
    expect(rows[0].candidate).toBeNull();
  });

  it("appends the executives who belong to no company after the companies", () => {
    const rows = toTriageRows(
      [company("a", "Almarai")],
      [person("c1", "Omar Haddad", "a")],
      [person("c9", "Wei Ling Tan", null)],
    );

    expect(rows.map((row) => row.candidate?.fullName)).toEqual(["Omar Haddad", "Wei Ling Tan"]);
    expect(rows[1].company).toBeNull();
  });

  it("gives the empty slot an id of its own, so it cannot collide with a person's row", () => {
    const slot = triageRowId({ company: company("a", "Almarai"), candidate: null, position: 1, siblings: 1 });
    const mapped = triageRowId({
      company: company("a", "Almarai"),
      candidate: person("c1", "Omar Haddad", "a"),
      position: 1,
      siblings: 1,
    });

    expect(slot).not.toEqual(mapped);
  });
});
