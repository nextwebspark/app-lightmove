import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TOAST_DURATION_MS, TOAST_WITH_UNDO_DURATION_MS, ToastProvider, useToast, type ToastFn } from "./Toast";

let toast: ToastFn;

function Capture() {
  toast = useToast();
  return null;
}

const renderToasts = () =>
  render(
    <ToastProvider>
      <Capture />
    </ToastProvider>,
  );

const send = (fire: () => void) => act(fire);

describe("Toast", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("keeps a plain call working as a notice that leaves on its own", () => {
    renderToasts();

    send(() => toast("Saved the filter"));
    expect(screen.getByRole("status")).toHaveTextContent("Saved the filter");

    act(() => vi.advanceTimersByTime(TOAST_DURATION_MS + 100));
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("shows two outcomes in quick succession as two toasts, and never more than three", () => {
    renderToasts();

    send(() => toast.success("Acme moved to the shortlist"));
    send(() => toast.success("Globex moved to the shortlist"));
    expect(screen.getAllByRole("status")).toHaveLength(2);

    send(() => toast.info("third"));
    send(() => toast.info("fourth"));
    const shown = screen.getAllByRole("status").map((toastBox) => toastBox.textContent);
    expect(shown).toEqual(["Globex moved to the shortlist", "third", "fourth"]);
  });

  it("does not stack a repeat of the same notice", () => {
    renderToasts();

    send(() => toast("Phone copied"));
    send(() => toast("Phone copied"));

    expect(screen.getAllByRole("status")).toHaveLength(1);
  });

  it("announces an error as an alert that stays until it is dismissed", () => {
    renderToasts();

    send(() => toast.error("Couldn't move Acme. Try again."));
    act(() => vi.advanceTimersByTime(60_000));

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("Couldn't move Acme. Try again.");
    fireEvent.click(within(alert).getByRole("button", { name: "Dismiss" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("runs Retry and takes the error away", () => {
    renderToasts();
    const retry = vi.fn();

    send(() => toast.error("Couldn't save", { retry }));
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(retry).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("gives a toast with Undo longer, and runs the undo once", () => {
    renderToasts();
    const undo = vi.fn();

    send(() => toast.success("Acme moved to declined", { undo }));
    act(() => vi.advanceTimersByTime(TOAST_DURATION_MS + 100));
    expect(screen.getByRole("status")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Undo" }));
    expect(undo).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("leaves an Undo toast on its own once its longer time is up", () => {
    renderToasts();

    send(() => toast.success("Acme moved to declined", { undo: vi.fn() }));
    act(() => vi.advanceTimersByTime(TOAST_WITH_UNDO_DURATION_MS + 100));

    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("holds a toast while the pointer is over it, then lets the rest of its time run", () => {
    renderToasts();

    send(() => toast.success("Acme moved to the shortlist"));
    act(() => vi.advanceTimersByTime(3_000));
    fireEvent.mouseEnter(screen.getByRole("status"));
    act(() => vi.advanceTimersByTime(30_000));
    expect(screen.getByRole("status")).toBeInTheDocument();

    fireEvent.mouseLeave(screen.getByRole("status"));
    act(() => vi.advanceTimersByTime(TOAST_DURATION_MS - 3_000 + 100));
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
