import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useSerialQueue } from "./useSerialQueue";

/** A promise and the function that settles it, so a test decides when each call finishes. */
function deferred<T>() {
  let resolve: (value: T) => void = () => {};
  let reject: (reason: unknown) => void = () => {};
  const promise = new Promise<T>((settle, refuse) => {
    resolve = settle;
    reject = refuse;
  });
  return { promise, resolve, reject };
}

describe("useSerialQueue", () => {
  it("starts a call only once the one before it has finished", async () => {
    const { result } = renderHook(() => useSerialQueue());
    const first = deferred<string>();
    const started: string[] = [];

    const firstDone = result.current(() => {
      started.push("first");
      return first.promise;
    });
    const secondDone = result.current(async () => {
      started.push("second");
      return "second";
    });
    await Promise.resolve();
    expect(started).toEqual(["first"]);

    first.resolve("first");
    await expect(firstDone).resolves.toBe("first");
    await expect(secondDone).resolves.toBe("second");
    expect(started).toEqual(["first", "second"]);
  });

  it("rejects a failed call to its caller and still runs the next one", async () => {
    const { result } = renderHook(() => useSerialQueue());
    const refused = deferred<string>();

    const failed = result.current(() => refused.promise);
    const next = result.current(async () => "next");
    refused.reject(new Error("409"));

    await expect(failed).rejects.toThrow("409");
    await expect(next).resolves.toBe("next");
  });
});
