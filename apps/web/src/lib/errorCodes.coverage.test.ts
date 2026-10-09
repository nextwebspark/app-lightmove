import { describe, expect, it } from "vitest";
import errorCodeSource from "../../../api/src/main/java/app/lightmove/api/core/error/constant/ErrorCode.java?raw";
import { ApiRequestError } from "./apiClient";
import { DETAIL_ALLOWED, messageFor } from "./errorCodes";

/** Codes that never reach `messageFor`, each with where it is answered instead. */
const HANDLED_ELSEWHERE: Record<string, string> = {
  OAUTH_FAILED: "a sign-in redirect's ?error=, worded by messageForOAuthError",
  OAUTH_CANCELLED: "a sign-in redirect's ?error=, worded by messageForOAuthError",
  OAUTH_CLIENT_NOT_FOUND: "the consent page's own refusal screen",
  OAUTH_REQUEST_NOT_FOUND: "the consent page's own refusal screen",
  API_KEY_INVALID: "public API only",
  API_KEY_SCOPE_MISSING: "public API only",
  PUBLIC_API_UNIVERSE_TOO_LARGE: "public API only",
  MCP_CREDENTIAL_INVALID: "MCP server only",
  MCP_SCOPE_INSUFFICIENT: "MCP server only",
  MCP_REQUEST_TOO_LARGE: "MCP server only",
  MAILBOX_WEBHOOK_REJECTED: "the mail provider's webhook, never a browser",
  INTERNAL_ERROR: "a bug on our side: the reference-id line is the message",
  METHOD_NOT_ALLOWED: "a bug on our side: the reference-id line is the message",
  UNSUPPORTED_MEDIA_TYPE: "a bug on our side: the reference-id line is the message",
  NOT_ACCEPTABLE: "a bug on our side: the reference-id line is the message",
};

const backendCodes = [...errorCodeSource.matchAll(/^ {4}([A-Z][A-Z0-9_]+)\(/gm)].map((match) => match[1]);

const messageOf = (code: string) =>
  messageFor(new ApiRequestError({ code, detail: "server detail", status: 400, correlationId: "ref-1" }));

describe("error wording coverage", () => {
  it("reads the backend's codes", () => {
    expect(backendCodes.length).toBeGreaterThan(100);
  });

  it.each(backendCodes)("%s has SPA wording, an allowed server sentence, or a stated home", (code) => {
    const worded = messageOf(code) !== messageOf("NOT_A_REAL_CODE");
    const allowed = DETAIL_ALLOWED.has(code as never);
    expect(worded || allowed || code in HANDLED_ELSEWHERE).toBe(true);
  });

  it("lists nothing as handled elsewhere that the backend no longer has", () => {
    expect(Object.keys(HANDLED_ELSEWHERE).filter((code) => !backendCodes.includes(code))).toEqual([]);
  });

  it.each(backendCodes)("%s's wording names no deployment, mandate or vendor", (code) => {
    expect(messageOf(code)).not.toMatch(/deployment|mandate|nylas|contactout|bright ?data|apollo|lightmove/i);
  });
});
