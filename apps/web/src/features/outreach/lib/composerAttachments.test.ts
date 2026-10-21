import { describe, expect, it } from "vitest";
import { attachmentProblemOf, documentOf, uploadOf, withAttachment } from "./composerAttachments";

const LIMITS = { maxFiles: 2, maxFileBytes: 3_000_000, maxTotalBytes: 5_000_000 };

function fileOf(name: string, size: number): File {
  const file = new File(["x"], name, { type: "application/pdf", lastModified: 1 });
  Object.defineProperty(file, "size", { value: size });
  return file;
}

describe("composer attachments", () => {
  it("adds a file or a document once, however often it is picked", () => {
    const cv = documentOf("v1", "CV.pdf", 1_000);
    const brief = uploadOf(fileOf("Brief.pdf", 2_000));
    const held = withAttachment(withAttachment(withAttachment([], cv), brief), documentOf("v1", "CV.pdf", 1_000));
    expect(held.map((attachment) => attachment.fileName)).toEqual(["CV.pdf", "Brief.pdf"]);
  });

  it("says what the server would refuse, in its order", () => {
    expect(attachmentProblemOf([documentOf("v1", "CV.pdf", 1_000)], LIMITS)).toBeNull();
    expect(
      attachmentProblemOf([documentOf("v1", "a.pdf", 1), documentOf("v2", "b.pdf", 1), documentOf("v3", "c.pdf", 1)], LIMITS),
    ).toBe("One email can carry up to 2 files.");
    expect(attachmentProblemOf([uploadOf(fileOf("Big.pdf", 3_000_001))], LIMITS)).toBe(
      "Big.pdf is larger than 2.9 MB, the most one file can be.",
    );
    expect(attachmentProblemOf([documentOf("v1", "a.pdf", 2_900_000), documentOf("v2", "b.pdf", 2_900_000)], LIMITS)).toBe(
      "These files come to 5.5 MB. Keep them to 4.8 MB together.",
    );
  });
});
