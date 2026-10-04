import { describe, expect, it } from "vitest";
import type { PersonDocument } from "../api/types";
import { formatBytes, groupDocuments, guessCategory, planUpload, primaryCvOf } from "./documents";

const documentOf = (overrides: Partial<PersonDocument> & { fileName?: string; uploadedAt?: string }): PersonDocument => ({
  id: overrides.id ?? "d1",
  category: overrides.category ?? "cv",
  title: overrides.title ?? "Jane Doe CV",
  primaryCv: overrides.primaryCv ?? false,
  projectId: null,
  projectTitle: null,
  createdBy: "u1",
  createdByName: "Yara Haddad",
  createdAt: "2026-09-01T09:00:00Z",
  updatedAt: "2026-09-01T09:00:00Z",
  removable: true,
  versions: [
    {
      id: `${overrides.id ?? "d1"}-v3`,
      versionNo: 3,
      fileName: overrides.fileName ?? "Jane_Doe_CV.pdf",
      contentType: "application/pdf",
      sizeBytes: 412_000,
      uploadedBy: "u1",
      uploadedByName: "Yara Haddad",
      uploadedAt: overrides.uploadedAt ?? "2026-09-12T09:40:00Z",
      previewable: true,
      removable: true,
    },
  ],
});

describe("guessCategory", () => {
  it("proposes what the server would file a name under", () => {
    expect(guessCategory("Jane_Doe_CV.pdf")).toBe("cv");
    expect(guessCategory("resume-2026.docx")).toBe("cv");
    expect(guessCategory("Cover letter CV.pdf")).toBe("cover_letter");
    expect(guessCategory("Hogan report.pdf")).toBe("assessment");
    expect(guessCategory("ACCA certificate.jpg")).toBe("certificate");
    expect(guessCategory("Board deck.pdf")).toBe("other");
  });
});

describe("planUpload", () => {
  const held = [documentOf({ fileName: "Jane_Doe_CV.pdf" })];

  it("makes a file named like a document's latest file its next version, ignoring case", () => {
    expect(planUpload("jane_doe_cv.PDF", held, false)).toMatchObject({ kind: "version", next: 4 });
  });

  it("keeps a same-named file apart when asked, and says what it shares a name with", () => {
    expect(planUpload("Jane_Doe_CV.pdf", held, true)).toEqual({ kind: "new", sameNameAs: held[0] });
  });

  it("files any other name as a new document", () => {
    expect(planUpload("Reference.pdf", held, false)).toEqual({ kind: "new", sameNameAs: null });
  });
});

describe("groupDocuments", () => {
  it("puts CVs first, the primary CV ahead of a newer one, and the rest newest first", () => {
    const groups = groupDocuments([
      documentOf({ id: "ref", category: "reference", uploadedAt: "2026-09-20T00:00:00Z" }),
      documentOf({ id: "new-cv", uploadedAt: "2026-09-25T00:00:00Z" }),
      documentOf({ id: "primary", primaryCv: true, uploadedAt: "2026-04-01T00:00:00Z" }),
    ]);
    expect(groups.map((group) => group.category.value)).toEqual(["cv", "reference"]);
    expect(groups[0].documents.map((document) => document.id)).toEqual(["primary", "new-cv"]);
  });
});

describe("primaryCvOf", () => {
  it("answers the marked CV, or nothing", () => {
    expect(primaryCvOf([documentOf({ id: "a" })])).toBeNull();
    expect(primaryCvOf([documentOf({ id: "a" }), documentOf({ id: "b", primaryCv: true })])?.id).toBe("b");
  });
});

describe("formatBytes", () => {
  it("reads kilobytes and megabytes", () => {
    expect(formatBytes(54_000)).toBe("53 KB");
    expect(formatBytes(1_258_291)).toBe("1.2 MB");
    expect(formatBytes(20 * 1024 * 1024)).toBe("20 MB");
  });
});
