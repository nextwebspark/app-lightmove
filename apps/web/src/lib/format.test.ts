import { afterEach, describe, expect, it, vi } from "vitest";
import {
  abbreviateAmount,
  formatDate,
  formatInstantDate,
  formatMoney,
  formatMonthYear,
  formatNumber,
  formatRelativeTime,
  initials,
  joined,
  titleCase,
} from "./format";

describe("titleCase", () => {
  it("turns a screaming enum into the label a row shows", () => {
    expect(titleCase("ADMIN")).toBe("Admin");
    expect(titleCase("RESEARCHER")).toBe("Researcher");
  });
});

describe("formatDate", () => {
  it("prints a calendar date in the mockups' shape", () => {
    expect(formatDate("2026-03-15")).toBe("15 Mar 2026");
  });

  // en-GB abbreviates September to "Sept", not "Sep" — pinned so a future formatter swap is a
  // deliberate choice rather than a silent change to what every date cell renders.
  it("keeps the en-GB four-letter September", () => {
    expect(formatDate("2026-09-15")).toBe("15 Sept 2026");
  });

  it("prints a dash rather than an empty cell when there is no date", () => {
    expect(formatDate(null)).toBe("—");
    expect(formatDate(undefined)).toBe("—");
    expect(formatDate("")).toBe("—");
  });

  it("reads the date as local, so the day never slips a timezone", () => {
    expect(formatDate("2026-01-01")).toBe("01 Jan 2026");
  });
});

describe("formatMonthYear", () => {
  it("drops the day, which a joined line does not want", () => {
    expect(formatMonthYear("2026-03-15T12:00:00Z")).toBe("Mar 2026");
  });

  it("returns null so the caller can drop the clause entirely", () => {
    expect(formatMonthYear(null)).toBeNull();
    expect(formatMonthYear(undefined)).toBeNull();
    expect(formatMonthYear("not an instant")).toBeNull();
  });
});

describe("formatInstantDate", () => {
  // Midday UTC, so the local calendar day is the same one on every timezone a developer or CI box
  // is plausibly set to.
  it("tells the reader the day an instant fell on", () => {
    expect(formatInstantDate("2026-03-15T12:00:00Z")).toBe("15 Mar 2026");
  });

  it("returns null on absent or unparseable input", () => {
    expect(formatInstantDate(null)).toBeNull();
    expect(formatInstantDate("nonsense")).toBeNull();
  });
});

describe("formatNumber", () => {
  it("groups a package figure, where the exact number is the point", () => {
    expect(formatNumber(450_000)).toBe("450,000");
    expect(formatNumber(0)).toBe("0");
  });
});

describe("abbreviateAmount", () => {
  it("compacts a thousands figure and rounds to the nearest K", () => {
    expect(abbreviateAmount(450_000)).toBe("450K");
    expect(abbreviateAmount(1_500)).toBe("2K");
  });

  it("leaves anything under a thousand alone", () => {
    expect(abbreviateAmount(999)).toBe("999");
    expect(abbreviateAmount(0)).toBe("0");
  });
});

describe("initials", () => {
  it("takes the first and last name, never a middle one", () => {
    expect(initials("Sara Al-Mansour")).toBe("SA");
    expect(initials("Ahmed bin Khalid Al-Fahim")).toBe("AA");
  });

  it("repeats nothing when there is only one name", () => {
    expect(initials("Cher")).toBe("C");
  });

  it("survives a name that is only whitespace", () => {
    expect(initials("   ")).toBe("?");
  });
});

describe("formatRelativeTime", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  function at(isoNow: string) {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(isoNow));
  }

  it("calls anything inside one access-token lifetime active", () => {
    at("2026-08-30T12:00:00Z");
    expect(formatRelativeTime("2026-08-30T11:56:00Z")).toBe("active now");
  });

  it("counts minutes, then hours, then days", () => {
    at("2026-08-30T12:00:00Z");
    expect(formatRelativeTime("2026-08-30T11:10:00Z")).toBe("50 minutes ago");
    expect(formatRelativeTime("2026-08-30T09:00:00Z")).toBe("3 hours ago");
    expect(formatRelativeTime("2026-08-27T12:00:00Z")).toBe("3 days ago");
  });

  it("says one, not 1s", () => {
    at("2026-08-30T12:00:00Z");
    expect(formatRelativeTime("2026-08-30T11:00:00Z")).toBe("1 hour ago");
    expect(formatRelativeTime("2026-08-29T12:00:00Z")).toBe("1 day ago");
  });

  it("prints a dash rather than NaN on an unparseable instant", () => {
    expect(formatRelativeTime("nonsense")).toBe("—");
  });
});

describe("formatMoney", () => {
  it("reads null as unknown, not as zero", () => {
    expect(formatMoney(null)).toBe("—");
    expect(formatMoney(0)).toBe("$0");
  });

  it("keeps one decimal below ten and drops it above", () => {
    expect(formatMoney(1_200_000_000)).toBe("$1.2B");
    expect(formatMoney(12_000_000_000)).toBe("$12B");
    expect(formatMoney(2_000_000)).toBe("$2M");
    expect(formatMoney(2_500_000)).toBe("$2.5M");
  });

  it("groups anything under a million rather than abbreviating it", () => {
    expect(formatMoney(999_999)).toBe("$999,999");
  });
});

describe("joined", () => {
  it("returns null for an empty list, so a cell reads as absent", () => {
    expect(joined([])).toBeNull();
  });

  it("comma-separates the rest", () => {
    expect(joined(["Arabic", "English"])).toBe("Arabic, English");
  });
});
