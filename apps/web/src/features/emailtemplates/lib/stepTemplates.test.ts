import { describe, expect, it } from "vitest";
import type { EmailTemplate } from "../api/emailTemplatesApi";
import {
  sequenceSigns,
  stepFromTemplate,
  stepHasText,
  stepRefusalOf,
  templateRefusalOf,
  templateWordsOfStep,
} from "./stepTemplates";

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
  it("closes with exactly one signature wherever the sequence signs, a cleared step included", () => {
    const signed = [step("Hi\n\n{{signature}}"), step("")];
    expect(sequenceSigns(signed)).toBe(true);
    expect(stepFromTemplate(aTemplate("Thanks."), signed[1], false, true).body).toBe("Thanks.\n\n{{signature}}");
    expect(stepFromTemplate(aTemplate("Thanks.\n\n{{signature|Best}}"), signed[1], false, true).body).toBe(
      "Thanks.\n\n{{signature}}",
    );
  });

  it("stays unsigned in a sequence that signs no step", () => {
    const unsigned = [step("Hi"), step("Following up")];
    expect(sequenceSigns(unsigned)).toBe(false);
    expect(stepFromTemplate(aTemplate("Thanks."), unsigned[1], false, false).body).toBe("Thanks.");
  });

  it("takes the template's subject into the first email alone", () => {
    const template = aTemplate("Thanks.", "A word");
    expect(stepFromTemplate(template, step("x", "Old"), true, false).subject).toBe("A word");
    expect(stepFromTemplate(template, step("x", null), false, false).subject).toBeNull();
  });

  it("is refused past a step's 5,000 characters, the signature counted", () => {
    expect(stepRefusalOf(aTemplate("x".repeat(4990)), step(""), true)).toMatch(/5,005 characters/);
    expect(stepRefusalOf(aTemplate("x".repeat(4990)), step(""), false)).toBeNull();
  });
});

describe("a step saved as a template", () => {
  it("leaves out the signature it places, however it is written, and counts nothing else as words", () => {
    expect(templateWordsOfStep(step("Hi {{firstName}},\n\nSoon.\n\n{{ signature|Best }}"))).toBe(
      "Hi {{firstName}},\n\nSoon.",
    );
    expect(stepHasText(step("\n{{signature}}"), false)).toBe(false);
    expect(templateRefusalOf(step("\n{{signature}}"))).toMatch(/no words of its own/);
    expect(templateRefusalOf(step("Hi {{opener}}"))).toMatch(/uses \{\{opener\}\}/);
  });
});
