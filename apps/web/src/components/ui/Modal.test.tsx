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

  it("ignores Escape and the overlay when not dismissible", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose} dismissible={false} title="Copy your key">
        <button>inside</button>
      </Modal>,
    );

    await user.keyboard("{Escape}");
    await user.click(screen.getByRole("dialog").parentElement!);

    expect(onClose).not.toHaveBeenCalled();
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

/** A transformed ancestor would otherwise pin the overlay inside it, as the executive drawer's did. */
describe("Modal — placement", () => {
  it("renders on the document body, outside the element that opened it", () => {
    render(
      <div data-testid="drawer" style={{ transform: "translateY(0)" }}>
        <Modal open onClose={vi.fn()} title="Book a call">
          <p>slots</p>
        </Modal>
      </div>,
    );

    expect(screen.getByTestId("drawer")).not.toContainElement(screen.getByRole("dialog"));
    expect(screen.getByRole("dialog").parentElement!.parentElement).toBe(document.body);
  });

  it("an overlay click does not reach a click handler around the modal", async () => {
    const user = userEvent.setup();
    const onOuterClick = vi.fn();
    const onClose = vi.fn();
    render(
      <div onClick={onOuterClick}>
        <Modal open onClose={onClose} title="Add to sequence">
          <p>people</p>
        </Modal>
      </div>,
    );

    await user.click(screen.getByRole("dialog").parentElement!);
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onOuterClick).not.toHaveBeenCalled();
  });
});

describe("Modal — header", () => {
  it("keeps the subtitle and the aside out of the scrolling body, and closes from the X", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal
        open
        onClose={onClose}
        title="Add to sequence"
        subtitle="Free times on me@firm.com"
        headerAside={<span>Choose</span>}
        closeButton
      >
        <p>form</p>
      </Modal>,
    );

    const body = screen.getByText("form").parentElement!;
    expect(body).not.toContainElement(screen.getByText("Free times on me@firm.com"));
    expect(body).not.toContainElement(screen.getByText("Choose"));

    await user.click(screen.getByRole("button", { name: "Close" }));
    expect(onClose).toHaveBeenCalledTimes(1);
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
