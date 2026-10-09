import type { GettingStartedStepKey } from "../api/gettingStartedApi";

export interface StepCopy {
  title: string;
  detail: string;
  action: string;
  /** Where the step is done; null opens the New position dialog instead of navigating. */
  pathFor: ((projectId: string) => string) | null;
  /** Steps worked on a position can't be started until one exists. */
  needsPosition: boolean;
}

export function stepCopyOf(step: GettingStartedStepKey, unitLower: string): StepCopy {
  switch (step) {
    case "OPEN_POSITION":
      return {
        title: "Open a position",
        detail: `Name the role and the ${unitLower} you're hiring for.`,
        action: "New position",
        pathFor: null,
        needsPosition: false,
      };
    case "WRITE_BRIEF":
      return {
        title: "Write the brief",
        detail: "Or attach the job description and let Uncava fill it in.",
        action: "Open the brief",
        pathFor: (projectId) => `/projects/${projectId}`,
        needsPosition: true,
      };
    case "FIND_COMPANIES":
      return {
        title: "Find target companies",
        detail: "Filter the market and add ten or more companies to the position.",
        action: "Open Strategy",
        pathFor: (projectId) => `/projects/${projectId}/strategy`,
        needsPosition: true,
      };
    case "MAP_EXECUTIVES":
      return {
        title: "Map executives",
        detail: "Use Find executives, the Chrome extension, or a spreadsheet.",
        action: "Open In universe",
        pathFor: (projectId) => `/projects/${projectId}/companies/universe`,
        needsPosition: true,
      };
    case "CONNECT_MAILBOX":
      return {
        title: "Connect your mailbox",
        detail: "Reach out to executives from your own email address.",
        action: "Open Outreach",
        pathFor: (projectId) => `/projects/${projectId}/outreach`,
        needsPosition: true,
      };
    case "INVITE_COLLEAGUE":
      return {
        title: "Invite a colleague",
        detail: "Work the search together. They get access when they accept.",
        action: "Open Team",
        pathFor: () => "/team",
        needsPosition: false,
      };
  }
}
