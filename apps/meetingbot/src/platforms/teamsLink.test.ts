import { describe, expect, it } from "vitest";
import { isAllowedTeamsNavigation, isTeamsLink } from "./teamsLink.js";

describe("isTeamsLink", () => {
  it.each([
    "https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc%40thread.v2/0?context=%7b%7d",
    "https://teams.microsoft.com/meet/123456789?p=abcdef",
    "https://teams.live.com/meet/9876543210?p=xyz",
  ])("opens %s", (link) => {
    expect(isTeamsLink(link)).toBe(true);
  });

  it.each([
    "http://teams.microsoft.com/l/meetup-join/abc",
    "https://teams.microsoft.com.attacker.example/l/meetup-join/abc",
    "https://attacker.example/teams.microsoft.com",
    "https://teams.microsoft.com:8443/l/meetup-join/abc",
    "https://user:pass@teams.live.com/meet/1",
    "https://teams.cloud.microsoft/l/meetup-join/abc",
    "https://meet.google.com/abc-defg-hij",
    "not a link",
  ])("refuses %s", (link) => {
    expect(isTeamsLink(link)).toBe(false);
  });
});

describe("isAllowedTeamsNavigation", () => {
  it.each([
    "https://teams.microsoft.com/v2/?meetingjoin=true",
    "https://teams.live.com/v2/",
    "https://teams.cloud.microsoft/v2/",
    "about:blank",
  ])("lets the tab go to %s", (url) => {
    expect(isAllowedTeamsNavigation(url)).toBe(true);
  });

  it.each([
    "https://login.microsoftonline.com/common/oauth2/authorize",
    "https://login.live.com/",
    "https://attacker.example/",
    "http://teams.microsoft.com/",
  ])("holds the tab back from %s", (url) => {
    expect(isAllowedTeamsNavigation(url)).toBe(false);
  });
});
