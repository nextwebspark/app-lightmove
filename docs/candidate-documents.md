# Candidate documents

A person's CV, cover letter, references and the like, kept on the workspace **person** (never on one
mandate), versioned, and recorded on the timeline. File handling only: parsing a CV into the profile is
a later phase.

| Phase | State |
|---|---|
| 0 — Mockups: the Documents tab, upload tray, CV chip, preview, timeline chip | **Next** (Claude Design) |
| 1 — V105, `core/storage`, `PersonDocumentService`, routes, bucket | **Built** |
| 2 — SPA, built from the approved mockup | After 0 |
| Later | Virus scanning · DOCX→PDF preview · CV parsing · share a document with a client seat · documents in merge · GDPR erasure · retention · orphan sweep |

## How established systems do it

- **The file belongs to the person.** Ashby, Greenhouse and Invenias keep documents on the candidate, not
  on the application. Ashby shows the latest resume everywhere and adds "See latest resume" where an
  application holds an older one.
- **Every document has a type.** Greenhouse requires resume / cover letter / offer / take-home / other.
  SmartRecruiters and Bullhorn use similar lists.
- **A new CV is added, not overwritten.** Bullhorn keeps every one in a Files tab. Invenias marks one as
  the **Default CV**, which is also the only one search reads. SmartRecruiters refuses an exact
  duplicate.
- **Formats and size.** The usual set is PDF / DOC / DOCX / RTF / TXT / ODT plus images for scans, with a
  cap of 5–100 MB (20 MB is common).
- **Storage.** Files sit in private object storage behind short-lived access. Virus scanning runs
  asynchronously and warns on download (Greenhouse, Ashby).
- **Visibility.** Documents can be private, and clients see only what a recruiter chooses to share
  (Loxo's submit flow, Teamtailor's anonymised CV).

## Decisions

- **Model.** A **document** is the card (category, title, CV mark), and its **versions** are the files.
  There is at most one **primary CV** per person, enforced by a partial unique index. The first CV takes
  the mark, and when the primary CV is removed the next one takes it.
- **Same name means next version.** A file whose name matches (case-insensitively) the latest version
  of a document on the person becomes that document's next version, unless the upload says
  `asNewDocument`. "Upload new version" on a card adds a file under any name.
- **Duplicate bytes are refused.** The same bytes (SHA-256) already on the person answer
  `409 PERSON_DOCUMENT_DUPLICATE` with `duplicateOf: {documentId, versionNo}`.
- **The file is judged by its bytes.** Name and signature must agree on one of PDF, DOCX, ODT, DOC, RTF,
  TXT, PNG or JPEG (`DocumentFormat`). Whatever content type the browser declares decides nothing.
- **Staff only**, like notes (D1).
  - **Routes:** the position's (`WORK_EXECUTE`) and the workspace's (`CANDIDATE_POOL_MANAGE`). A client
    seat reaches neither.
  - **Removing** a document is for whoever filed it, and a version for whoever uploaded it; a holder of
    `WORKSPACE_MANAGE` can remove either (`PERSON_DOCUMENT_NOT_YOURS`).
  - **Editing:** any staff member may rename a document, change its category or move the CV mark.
- **Storage.** The bytes live in a private GCS bucket (`DocumentStore`, `lightmove.storage.*`), never in
  Cloud SQL.
  - **Upload:** the file is checked, hashed and written before any row exists. If the rows then fail, the
    object is deleted.
  - **Removal:** the rows go first and the objects after. A crash leaves an unreferenced object, never a
    row pointing at nothing.
  - **Download:** the API streams every file itself, so there are no signed URLs. The SPA and the API
    stay one origin, and every download is audited (`PERSON_DOCUMENT_DOWNLOADED`).
  - **What a download is served as:** a PDF or image asked for as a preview goes out `inline` as its own
    type. Everything else is an `octet-stream` attachment. All of it is `nosniff` and `no-store`.
- **Timeline.** Four kinds: `DOCUMENT_ADDED`, `DOCUMENT_VERSION_ADDED`, `DOCUMENT_VERSION_REMOVED` and
  `DOCUMENT_REMOVED`, under a `documents` group.
  - A line carries only the document id, version and category.
  - The read adds the document's current title as `details.document` while the document exists, so a
    removed one leaves no name behind (the note excerpt's rule).
- **Limits** (`lightmove.person-documents.*`): 20 MB a file, 50 documents a person, 20 versions a
  document (`PERSON_DOCUMENT_LIMIT`).

## API

Under `/api/v1/projects/{projectId}/candidates/{candidateId}/documents` (`WORK_EXECUTE`) and
`/api/v1/candidates/{personId}/documents` (`CANDIDATE_POOL_MANAGE`):

| Method | Path | What |
|---|---|---|
| `GET` | (base) | Cards, primary CV first, then by category, then latest upload. Each carries its versions, newest first, with `previewable` and `removable` |
| `POST` | (base) | Multipart `file`, optional `category` (`cv`, `cover_letter`, `reference`, `certificate`, `assessment`, `other`; guessed from the name when absent), `asNewDocument`. Answers `{outcome: created \| new_version, document}` |
| `POST` | `/{documentId}/versions` | Multipart `file` |
| `PATCH` | `/{documentId}` | `{title?, category?, primaryCv?}` |
| `DELETE` | `/{documentId}` | The whole document |
| `DELETE` | `/{documentId}/versions/{versionId}` | One file; removing the last one removes the document |
| `GET` | `/{documentId}/versions/{versionId}/content?preview=` | The file |

## UI brief for the mockup (Phase 0)

The pattern Bullhorn and Ashby use, laid onto the drawers we already have:

1. **Workspace person drawer** (`Candidates.dc.html`). The tabs become **Profile · Notes · Documents
   (count) · Timeline**.
2. **A position's executive drawer.** A **Documents** section after Notes, staff only.
3. **CV one click away.** The drawer header carries a **CV · v3 · 12 Mar** chip for the primary CV,
   with Preview and Download. Opening the CV is the most common act, so it is not buried in a tab.
4. **The Documents tab:**
   - **Drop and upload.** The whole tab is a drop target, and there is an **Upload** button. Several
     files can go at once.
   - **Upload tray.** One row per file, showing:
     - a category picker, pre-filled with the guess from the file name;
     - what the file will become: *"New version of Jane_CV.pdf (v2 → v3)"* with a **Keep as a separate
       document** switch, *"New document"*, or *"Already uploaded as Cover letter v1"* (greyed out; the
       server's 409 confirms it).

     One **Upload n files** button sends them all.
   - **Cards, grouped by category, CV first.** Each card shows a type icon, the title, a category chip,
     a ★ on the primary CV, `v3`, the size, and who uploaded it and when.
   - **Card actions:** Preview (PDF and images), Download, **Upload new version**, and ⋯ (Rename, Change
     category, Make primary CV, Delete).
   - **Version history** expands inline: each version with Download, and Delete for its uploader or an
     admin.
5. **Preview** opens in a wide side sheet. The file is fetched as a blob and shown in a sandboxed
   `<iframe>` (PDF) or an `<img>`. DOC and DOCX download for now.
6. **Timeline.** A **Documents** chip, with lines that read *uploaded a CV — Jane Doe CV*, *uploaded
   version 3 of the CV*, *deleted a reference*. `candidateActivity.ts` already phrases these.

The mockup states to draw are: the tab empty, loaded, and with version history open; the tray in all
three outcomes; the CV chip; the preview sheet; and the timeline chip. All in the UNCAVA palette.

## Operations

- **Bucket.** `ops/gcp/bootstrap.sh` creates `${GCP_PROJECT}-lightmove-documents` and enables the
  Storage API.
  - Uniform access, public access prevention enforced, no object versioning.
  - The runtime service account gets `roles/storage.objectUser` on that bucket only.
- **Deploys.** Both `deploy.yml` and `ops/gcp/deploy.sh` set `STORAGE_PROVIDER=gcs` and
  `STORAGE_BUCKET`. `vars.DOCUMENTS_BUCKET` overrides the name.
- **Local and tests.** `npm run dev` and the tests use the filesystem store: `.data/documents`
  (gitignored) and `target/test-documents` respectively.
