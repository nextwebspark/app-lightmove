import { describe, expect, it } from "vitest";
import { pillsThatFit } from "./pillsThatFit";

describe("pillsThatFit", () => {
  it("shows every pill, and no chip, when they and the tail fit", () => {
    expect(pillsThatFit([40, 40, 40], 200, { gap: 4, lines: 1, chip: 30, tail: [50] })).toBe(3);
  });

  it("keeps room on a single line for the chip and the tail once a pill is left out", () => {
    // 40 + 40 + 40 + 40 + 50 with gaps passes 200; two pills leave room for the chip and the tail.
    expect(pillsThatFit([40, 40, 40, 40], 200, { gap: 4, lines: 1, chip: 30, tail: [50] })).toBe(2);
  });

  it("puts the chip at the end of the last line allowed, never on a line after it", () => {
    // Two lines of 100 hold two 48s each; the chip takes the second line's last place.
    expect(pillsThatFit([48, 48, 48, 48, 48, 48], 100, { gap: 4, lines: 2, chip: 40 })).toBe(3);
  });

  it("counts a pill wider than the row as a line of its own", () => {
    expect(pillsThatFit([300, 40], 100, { gap: 4, lines: 2, chip: 40 })).toBe(2);
    expect(pillsThatFit([300, 300, 40], 100, { gap: 4, lines: 2, chip: 40 })).toBe(1);
  });

  it("shows only the chip when the row has no room for any pill beside it", () => {
    expect(pillsThatFit([80, 80], 100, { gap: 4, lines: 1, chip: 30, tail: [50] })).toBe(0);
  });
});
