import { describe, expect, it } from "vitest";
import type { Client } from "../api/types";
import { filterClients } from "./filtering";

/** The chips + search combination is the Clients screen's core list behavior. */

const client = (overrides: Partial<Client>): Client => ({
  id: "c1",
  name: "Meridian Energy",
  type: "RETAINED",
  sector: "Energy",
  hqCountry: "UAE",
  activeMandates: 2,
  deliveredMandates: 1,
  contacts: [{ fullName: "Khalid Al-Otaibi", status: "ACTIVE" }],
  viewers: { active: 1, invited: 0 },
  ...overrides,
});

describe("filterClients", () => {
  const retained = client({ id: "retained" });
  const prospect = client({
    id: "prospect",
    type: "PROSPECT",
    activeMandates: 0,
    deliveredMandates: 0,
    contacts: [],
    viewers: { active: 0, invited: 0 },
  });

  it("the Active-mandates chip hides clients with no live mandate", () => {
    const rows = filterClients([retained, prospect], { chip: "active", query: "" });
    expect(rows.map((c) => c.id)).toEqual(["retained"]);
  });

  it("the No-representative chip keeps only clients with no contacts", () => {
    const rows = filterClients([retained, prospect], { chip: "noreps", query: "" });
    expect(rows.map((c) => c.id)).toEqual(["prospect"]);
  });

  it("All clients keeps everything", () => {
    const rows = filterClients([retained, prospect], { chip: "all", query: "" });
    expect(rows).toHaveLength(2);
  });

  it("search matches name and sector, case-insensitively", () => {
    const other = client({ id: "other", name: "Agthia", sector: "FMCG" });
    expect(filterClients([retained, other], { chip: "all", query: "meridian" }).map((c) => c.id)).toEqual([
      "retained",
    ]);
    expect(filterClients([retained, other], { chip: "all", query: "fmcg" }).map((c) => c.id)).toEqual([
      "other",
    ]);
  });
});
