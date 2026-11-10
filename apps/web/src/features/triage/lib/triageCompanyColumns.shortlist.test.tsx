import { describe, expect, it, vi } from "vitest";
import { createTriageCompanyColumns } from "./triageCompanyColumns";

vi.mock("./triageStages", async (importOriginal) =>
  (await import("../../../test/shortlistAndDecline")).withShortlistAndDecline(await importOriginal()),
);

describe("the Actions column while Shortlisted is offered", () => {
  it("leaves room for two moves and the trash, since it cannot be dragged wider", () => {
    const actions = createTriageCompanyColumns([], { staff: true }).find((column) => column.id === "actions");
    const meta = actions?.meta as { min: number; fixed?: boolean };

    expect(meta.fixed).toBe(true);
    expect(meta.min).toBeGreaterThanOrEqual(90);
  });
});
