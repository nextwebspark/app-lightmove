import type { TriageCompanyStatus } from "../../triage/api/types";

export type ProposedCompany = {
  apolloAccountId: string;
  companyName: string;
  country: string | null;
  employees: number | null;
  logoUrl: string | null;
};

/** The company card an answer carried. */
export type AssistantProposal = {
  title: string;
  companies: ProposedCompany[];
};

/** What was filed from a card. A null status means the default stage, in universe. */
export type ProposalOutcome = {
  status: TriageCompanyStatus | null;
  added: number;
  skipped: number;
};

/** One thing the assistant did while answering, e.g. a search and how many it matched. */
export type AssistantStep = {
  label: string;
  detail: string | null;
};

/** A step as it arrives while the answer is still being worked out. */
export type LiveStep = AssistantStep & {
  index: number;
  done: boolean;
};

export type AssistantTurn = {
  id: string;
  threadId: string;
  question: string;
  answer: string;
  steps: AssistantStep[];
  proposal: AssistantProposal | null;
  proposalAccepted: ProposalOutcome | null;
  createdAt: string;
};

export type AssistantThreadSummary = {
  id: string;
  title: string;
  updatedAt: string;
};

export type AssistantThread = {
  id: string;
  title: string;
  projectId: string | null;
  turns: AssistantTurn[];
};
