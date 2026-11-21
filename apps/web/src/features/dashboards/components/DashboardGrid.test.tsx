import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { limitsFor } from "../lib/gridLayout";
import { DashboardGrid } from "./DashboardGrid";

const items = [{ key: "a", layout: { x: 0, y: 0, w: 6, h: 7 }, limits: limitsFor("bar"), label: "Pipeline" }];

describe("DashboardGrid on a narrow canvas", () => {
  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(600);
  });
  afterEach(() => vi.restoreAllMocks());

  it("says in edit mode why the charts cannot be moved", () => {
    render(<DashboardGrid items={items} editing onChange={() => {}}>{() => <div>chart</div>}</DashboardGrid>);
    expect(screen.getByText(/There isn't room here to move or resize charts/)).toBeInTheDocument();
  });

  it("says nothing outside edit mode", () => {
    render(<DashboardGrid items={items} editing={false} onChange={() => {}}>{() => <div>chart</div>}</DashboardGrid>);
    expect(screen.queryByText(/There isn't room here/)).not.toBeInTheDocument();
  });
});
