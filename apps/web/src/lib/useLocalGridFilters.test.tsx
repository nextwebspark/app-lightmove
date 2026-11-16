import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { filterSpec, useLocalGridFilters } from "./useLocalGridFilters";

interface Position {
  id: string;
  team: { memberId: string; fullName: string }[];
  health: "OK" | "RISK";
}

const POSITIONS: Position[] = [
  { id: "p1", team: [{ memberId: "m1", fullName: "Sara Khan" }], health: "OK" },
  { id: "p2", team: [{ memberId: "m2", fullName: "Sara Khan" }], health: "OK" },
  { id: "p3", team: [{ memberId: "m3", fullName: "Omar Ali" }], health: "OK" },
];

const NAMES = new Map(POSITIONS.flatMap((position) => position.team.map((seat) => [seat.memberId, seat.fullName] as const)));

const SPECS = [
  filterSpec<Position, string>({
    id: "team",
    label: "Team",
    valuesOf: (position) => position.team.map((seat) => seat.memberId),
    labelOf: (memberId) => NAMES.get(memberId) ?? memberId,
  }),
  filterSpec<Position, Position["health"]>({
    id: "health",
    label: "Health",
    valuesOf: (position) => [position.health],
    order: ["OK", "RISK"],
  }),
];

describe("useLocalGridFilters", () => {
  it("keeps two colleagues who share a name as two options, and ticking one keeps only their rows", () => {
    const { result } = renderHook(() => useLocalGridFilters(POSITIONS, SPECS));
    const team = result.current.filters.team!;

    expect(team.options.map((option) => option.value)).toEqual(["m3", "m1", "m2"]);
    act(() => team.onChange(["m1"]));

    expect(result.current.rows.map((position) => position.id)).toEqual(["p1"]);
    expect(result.current.inForce).toBe(true);
  });

  it("offers no filter on a column whose rows all hold the same value", () => {
    const { result } = renderHook(() => useLocalGridFilters(POSITIONS, SPECS));

    expect(result.current.filters.health).toBeUndefined();
  });
});
