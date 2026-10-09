import type { GettingStartedStepKey } from "../api/gettingStartedApi";

/** Where the step is done: the New position dialog, a fixed page, or a page of a position the caller is on. */
export type StepTarget =
  | { kind: "newPosition" }
  | { kind: "page"; path: string }
  | { kind: "position"; pathFor: (projectId: string) => string };

export interface StepCopy {
  title: string;
  detail: string;
  action: string;
  target: StepTarget;
}

export function stepCopyOf(step: GettingStartedStepKey, unitLower: string): StepCopy {
  switch (step) {
    case "OPEN_POSITION":
      return {
        title: "Open a position",
        detail: `Name the role and the ${unitLower} you're hiring for.`,
        action: "New position",
        target: { kind: "newPosition" },
      };
    case "WRITE_BRIEF":
      return {
        title: "Write the brief",
        detail: "Or attach the job description and let Uncava fill it in.",
        action: "Open the brief",
        target: { kind: "position", pathFor: (projectId) => `/projects/${projectId}` },
      };
    case "FIND_COMPANIES":
      return {
        title: "Find target companies",
        detail: "Filter the market and add ten or more companies to the position.",
        action: "Go to Strategy",
        target: { kind: "position", pathFor: (projectId) => `/projects/${projectId}/strategy` },
      };
    case "MAP_EXECUTIVES":
      return {
        title: "Map executives",
        detail: "Use Find executives, the Chrome extension, or a spreadsheet.",
        action: "Go to In universe",
        target: { kind: "position", pathFor: (projectId) => `/projects/${projectId}/companies/universe` },
      };
    case "CONNECT_MAILBOX":
      return {
        title: "Connect your mailbox",
        detail: "Reach out to executives from your own email address.",
        action: "Go to Outreach",
        target: { kind: "position", pathFor: (projectId) => `/projects/${projectId}/outreach` },
      };
    case "INVITE_COLLEAGUE":
      return {
        title: "Invite a colleague",
        detail: "Work the search together. They get access when they accept.",
        action: "Go to Team",
        target: { kind: "page", path: "/team" },
      };
  }
}
