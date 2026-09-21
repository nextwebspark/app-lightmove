/** One company the assistant is offering to file. Rendered by #435; carried here so a turn is whole. */
export type ProposedCompany = {
  ref: string;
  origin: "UNIVERSE" | "RESEARCHED" | "WEB";
  apolloAccountId: string | null;
  companyName: string;
  country: string | null;
  employees: number | null;
};

export type AssistantProposal = {
  projectId: string;
  title: string;
  companies: ProposedCompany[];
  accepted: { status: string; refs: string[]; added: number; skipped: number } | null;
};

export type AssistantTurnStatus = "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";

export type AssistantTurn = {
  id: string;
  threadId: string;
  status: AssistantTurnStatus;
  question: string;
  answer: string | null;
  errorCode: string | null;
  proposal: AssistantProposal | null;
  createdAt: string;
  finishedAt: string | null;
};

export type AssistantThread = {
  id: string;
  title: string;
  projectId: string | null;
  createdAt: string;
  updatedAt: string;
  turns: AssistantTurn[];
};

/**
 * One frame of a turn's stream.
 *
 * <p>`kind` is passed through as the server's wire string and never parsed into a closed set: an
 * instance running older code must drop a kind it does not know rather than the whole frame, because
 * unlike the project stream — where every event means only "refetch" — an assistant event *is* the
 * content.
 */
export type AssistantFrame = {
  seq: number;
  kind: string;
  payload: Record<string, unknown>;
  occurredAt: string;
};
