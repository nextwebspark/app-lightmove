import { describe, expect, it } from "vitest";
import type { EmailTemplate } from "../api/emailTemplatesApi";
import { stepFromTemplate, stepHasText, stepRefusalOf, templateRefusalOf } from "./stepTemplates";

function aTemplate(plainBody: string, subject: string | null = null): EmailTemplate {
  return {
    id: "t1",
    name: "Intro",
    subject,
    body: plainBody,
    plainBody,
    shared: false,
    mine: true,
    canEdit: true,
    owner: { userId: "u1", fullName: "Yara Haddad", avatarUrl: null },
    updatedAt: "2026-11-01T10:00:00Z",
    version: 0,
  };
}

const step = (body: string, subject: string | null = null) => ({ delayWorkingDays: 0, subject, body });

describe("a template copied into a step", () => {
  it("is its words alone: no step holds a signature, Include signature adds it at Start", () => {
    expect(stepFromTemplate(aTemplate("Thanks.\n"), step("Old words"), false).body).toBe("Thanks.");
  });

  it("takes the template's subject into the first email alone", () => {
    const template = aTemplate("Thanks.", "A word");
    expect(stepFromTemplate(template, step("x", "Old"), true).subject).toBe("A word");
    expect(stepFromTemplate(template, step("x", null), false).subject).toBeNull();
  });

  it("is refused past a step's 5,000 characters", () => {
    expect(stepRefusalOf(aTemplate("x".repeat(5001)))).toMatch(/5,001 characters/);
    expect(stepRefusalOf(aTemplate("x".repeat(5000)))).toBeNull();
  });
});

describe("a step saved as a template", () => {
  it("needs words of its own, and no {{opener}}", () => {
    expect(stepHasText(step("\n "), false)).toBe(false);
    expect(stepHasText(step("", "A subject"), true)).toBe(true);
    expect(templateRefusalOf(step("\n "))).toMatch(/no words of its own/);
    expect(templateRefusalOf(step("Hi {{opener}}"))).toMatch(/uses \{\{opener\}\}/);
  });
});
