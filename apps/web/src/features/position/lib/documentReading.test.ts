import { describe, expect, it } from "vitest";
import type { MandateContext, OrgNode, PositionDetails, ReportingStructure } from "../api/types";
import type { PositionSnapshot } from "./documentFill";
import { documentReadingsOf } from "./documentReading";

const details: PositionDetails = {
  roleTitle: "Chief Financial Officer",
  department: "Finance",
  locationCity: "Dubai",
  locationCountry: null,
  employmentType: null,
  seniority: null,
  responsibilities: [
    { text: "Own the group's reporting", source: "DOCUMENT" },
    { text: "Lead the audit", source: "MANUAL" },
  ],
  narrative: null,
  fieldSources: { department: "DOCUMENT", locationCity: "MANUAL" },
};

const context: MandateContext = {
  mandateReason: "NEW_ROLE",
  businessDriver: null,
  strategicPriorities: [],
  confidential: false,
  internalContext: null,
  fieldSources: {},
};

const seat = (nodeId: string, source?: OrgNode["source"]): OrgNode => ({
  nodeId,
  parentNodeId: null,
  title: null,
  name: null,
  mandateSeat: nodeId === "role",
  canvasX: null,
  canvasY: null,
  source,
});

const reporting: ReportingStructure = {
  orgChart: [seat("role"), seat("cfo", "DOCUMENT")],
  teamSize: "12",
  targetStart: null,
  noticeValue: null,
  noticeUnit: null,
  fieldSources: { teamSize: "DOCUMENT" },
};

const snapshot: PositionSnapshot = {
  details,
  context,
  reporting,
  criteria: [{ text: "Has led a listing", mode: "REQUIRED", source: "DOCUMENT" }],
  technical: [{ name: "IFRS", description: null, weight: 50, source: "DOCUMENT" }],
  behavioural: [{ name: "Candour", description: null, weight: 50, source: "MANUAL" }],
  technicalShare: 50,
};

describe("documentReadingsOf", () => {
  it("names the scalars and counts the rows the fill left as the document's, per channel", () => {
    const readings = documentReadingsOf(snapshot, { details: 4, reporting: 2, assessment: 7 });

    expect(readings.details).toEqual({ version: 4, fields: ["department"], listItems: { responsibilities: 1 } });
    expect(readings.context).toEqual({ version: null, fields: [], listItems: {} });
    expect(readings.reporting).toEqual({ version: 2, fields: ["teamSize"], listItems: { orgChart: 1 } });
    expect(readings.criteria).toEqual({ version: 7, fields: [], listItems: { criteria: 1 } });
    expect(readings.competencies).toEqual({ version: 7, fields: [], listItems: { technicalCompetencies: 1 } });
  });
});
