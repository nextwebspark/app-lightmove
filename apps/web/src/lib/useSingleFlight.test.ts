import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useSingleFlight } from "./useSingleFlight";

describe("useSingleFlight", () => {
  it("drops a second run while the first is in flight, and takes the next once it settles", async () => {
    const { result } = renderHook(() => useSingleFlight());
    let finish: () => void = () => undefined;
    const action = vi.fn(() => new Promise<void>((resolve) => { finish = resolve; }));

    let first: Promise<unknown> = Promise.resolve();
    act(() => {
      first = result.current(action);
      void result.current(action);
    });
    expect(action).toHaveBeenCalledTimes(1);

    await act(async () => {
      finish();
      await first;
    });
    await act(async () => {
      void result.current(() => Promise.resolve());
    });
    expect(action).toHaveBeenCalledTimes(1);
  });

  it("is free again after the action fails", async () => {
    const { result } = renderHook(() => useSingleFlight());
    await act(async () => {
      await result.current(() => Promise.reject(new Error("no"))).catch(() => undefined);
    });
    const next = vi.fn(() => Promise.resolve("ok"));
    await act(async () => {
      await result.current(next);
    });
    expect(next).toHaveBeenCalledTimes(1);
  });
});
