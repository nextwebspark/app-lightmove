import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Modal } from "./Modal";
import { ToastProvider, useToast } from "./Toast";

/** Both dismissal paths must work — a modal that traps the user eats whatever they typed elsewhere. */
describe("Modal", () => {
  it("closes on Escape and on overlay click, but not on a click inside", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose} title="New project">
        <button>inside</button>
      </Modal>,
    );

    await user.click(screen.getByText("inside"));
    expect(onClose).not.toHaveBeenCalled();

    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("dialog").parentElement!);
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});

/** The actions are pinned beneath the body, so a short screen scrolls the form and never the buttons. */
describe("Modal — footer", () => {
  it("renders the footer outside the scrolling body", () => {
    render(
      <Modal open onClose={vi.fn()} title="New position" footer={<button>Create</button>}>
        <p>form</p>
      </Modal>,
    );

    const body = screen.getByText("form").parentElement!;
    expect(body).toHaveClass("overflow-y-auto", "[scrollbar-width:thin]");
    expect(body).not.toContainElement(screen.getByRole("button", { name: "Create" }));
  });
});

describe("Toast", () => {
  function Trigger({ message }: { message: string }) {
    const toast = useToast();
    return <button onClick={() => toast(message)}>fire</button>;
  }

  it("shows a toast and auto-dismisses it", () => {
    vi.useFakeTimers();
    render(
      <ToastProvider>
        <Trigger message="first" />
      </ToastProvider>,
    );

    fireEvent.click(screen.getByText("fire"));
    expect(screen.getByRole("status")).toHaveTextContent("first");

    act(() => vi.advanceTimersByTime(2300));
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    vi.useRealTimers();
  });
});
