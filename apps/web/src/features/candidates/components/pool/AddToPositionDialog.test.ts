import { describe, expect, it } from "vitest";
import { addedMessageOf } from "./AddToPositionDialog";

describe("addedMessageOf", () => {
  it("names who went in when nobody was left out", () => {
    expect(addedMessageOf({ added: 3, alreadyIn: 0, suppressed: 0 }, "CFO", null)).toBe("Added 3 people to CFO");
    expect(addedMessageOf({ added: 1, alreadyIn: 0, suppressed: 0 }, "CFO", "Omar Said")).toBe(
      "Added Omar Said to CFO",
    );
    expect(addedMessageOf({ added: 0, alreadyIn: 2, suppressed: 0 }, "CFO", null)).toBe("Everyone was already in CFO");
  });

  it("says the suppression list left people out", () => {
    expect(addedMessageOf({ added: 2, alreadyIn: 0, suppressed: 1 }, "CFO", null)).toBe(
      "Added 2 people to CFO. 1 person is on your workspace's suppression list and was left out",
    );
    expect(addedMessageOf({ added: 0, alreadyIn: 1, suppressed: 2 }, "CFO", null)).toBe(
      "Nobody was added to CFO: 2 people are on your workspace's suppression list",
    );
    expect(addedMessageOf({ added: 0, alreadyIn: 0, suppressed: 1 }, "CFO", "Noor Haddad")).toBe(
      "Noor Haddad is on your workspace's suppression list, so they can't be added",
    );
  });
});
