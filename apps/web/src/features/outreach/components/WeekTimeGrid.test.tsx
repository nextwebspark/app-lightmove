import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CallChoice } from "../lib/weekGrid";
import { WeekTimeGrid } from "./WeekTimeGrid";

const DAYS = ["2099-10-05", "2099-10-06"];

function renderGrid(onChoose = vi.fn(), choice: CallChoice | null = null) {
  render(
    <WeekTimeGrid
      days={DAYS}
      timeZone="Asia/Dubai"
      busy={[{ start: "2099-10-05T06:00:00Z", end: "2099-10-05T07:00:00Z" }]}
      workingHours={[{ start: "2099-10-05T04:00:00Z", end: "2099-10-05T14:00:00Z" }]}
      choice={choice}
      clickMinutes={30}
      minMinutes={5}
      maxMinutes={480}
      now={new Date("2099-10-01T00:00:00Z")}
      onChoose={onChoose}
    />,
  );
  return onChoose;
}

/** jsdom lays nothing out: the day column is given a box at the top of the viewport, 0.8px a minute. */
function dayColumn(date: string) {
  const column = screen.getByTestId(`day-${date}`);
  column.getBoundingClientRect = () => ({ top: 0, left: 0, right: 104, bottom: 1152, width: 104, height: 1152, x: 0, y: 0, toJSON: () => ({}) });
  return column;
}

describe("WeekTimeGrid", () => {
  it("draws each day of the week with its busy time", () => {
    renderGrid();

    expect(screen.getByRole("group", { name: "Your week" })).toBeInTheDocument();
    expect(screen.getByText("MON")).toBeInTheDocument();
    expect(screen.getByText("TUE")).toBeInTheDocument();
    expect(screen.getAllByText("Busy")).toHaveLength(1);
  });

  it("picks the dragged stretch from any time to any time", () => {
    const onChoose = renderGrid();
    const column = dayColumn("2099-10-06");

    fireEvent.pointerDown(column, { button: 0, clientY: 615 * 0.8, pointerId: 1 });
    fireEvent.pointerMove(column, { clientY: 665 * 0.8, pointerId: 1 });
    fireEvent.pointerUp(column, { clientY: 665 * 0.8, pointerId: 1 });

    expect(onChoose).toHaveBeenCalledWith({ start: "2099-10-06T06:15:00.000Z", minutes: 50 });
  });

  it("takes the chosen length from a click", () => {
    const onChoose = renderGrid();
    const column = dayColumn("2099-10-05");

    fireEvent.pointerDown(column, { button: 0, clientY: 1210 * 0.8, pointerId: 1 });
    fireEvent.pointerUp(column, { clientY: 1210 * 0.8, pointerId: 1 });

    expect(onChoose).toHaveBeenCalledWith({ start: "2099-10-05T16:00:00.000Z", minutes: 30 });
  });

  describe("once a call is picked", () => {
    // 10:00–11:00 in Dubai: 600 to 660 minutes down the column.
    const PICKED: CallChoice = { start: "2099-10-06T06:00:00.000Z", minutes: 60 };

    const press = (column: HTMLElement, from: number, to = from) => {
      fireEvent.pointerDown(column, { button: 0, clientY: from * 0.8, pointerId: 1 });
      fireEvent.pointerMove(column, { clientY: to * 0.8, pointerId: 1 });
      fireEvent.pointerUp(column, { clientY: to * 0.8, pointerId: 1 });
    };

    it("changes nothing on a press inside it, dragged or not", () => {
      const onChoose = renderGrid(vi.fn(), PICKED);
      const column = dayColumn("2099-10-06");

      press(column, 630);
      press(column, 630, 700);

      expect(onChoose).not.toHaveBeenCalled();
    });

    it("moves its end by dragging its bottom edge, and its start by its top, the other edge staying put", () => {
      const onChoose = renderGrid(vi.fn(), PICKED);
      const column = dayColumn("2099-10-06");

      press(column, 658, 688);
      expect(onChoose).toHaveBeenLastCalledWith({ start: "2099-10-06T06:00:00.000Z", minutes: 90 });

      press(column, 602, 572);
      expect(onChoose).toHaveBeenLastCalledWith({ start: "2099-10-06T05:30:00.000Z", minutes: 90 });
    });

    it("leaves it as it was when an edge is pressed and let go without moving", () => {
      const onChoose = renderGrid(vi.fn(), PICKED);

      press(dayColumn("2099-10-06"), 664);

      expect(onChoose).toHaveBeenCalledWith(PICKED);
    });

    it("picks afresh on a press anywhere else", () => {
      const onChoose = renderGrid(vi.fn(), PICKED);

      press(dayColumn("2099-10-06"), 800);

      expect(onChoose).toHaveBeenCalledWith({ start: "2099-10-06T09:15:00.000Z", minutes: 30 });
    });
  });
});

describe("WeekTimeGrid — on a phone", () => {
  const desktop = window.matchMedia;
  beforeEach(() => {
    window.matchMedia = (query: string) => ({ ...desktop(query), matches: false });
  });
  afterEach(() => {
    window.matchMedia = desktop;
  });

  const isShown = (date: string) => !screen.getByTestId(`day-${date}`).closest("[hidden]");

  it("shows one day, the first when neither a pick nor today is in the week", () => {
    renderGrid();

    expect(screen.getByRole("group", { name: "Day" })).toBeInTheDocument();
    expect(isShown("2099-10-05")).toBe(true);
    expect(isShown("2099-10-06")).toBe(false);
  });

  it("opens on the chosen call's day, and a tap on the strip moves to another", () => {
    renderGrid(vi.fn(), { start: "2099-10-06T06:15:00.000Z", minutes: 30 });
    expect(isShown("2099-10-06")).toBe(true);

    fireEvent.click(screen.getByRole("button", { name: /MON 5/ }));

    expect(isShown("2099-10-05")).toBe(true);
    expect(isShown("2099-10-06")).toBe(false);
    expect(screen.getByRole("button", { name: /MON 5/ })).toHaveAttribute("aria-pressed", "true");
  });

  it("still picks a time with a tap", () => {
    const onChoose = renderGrid();
    const column = dayColumn("2099-10-05");

    fireEvent.pointerDown(column, { button: 0, clientY: 1210 * 0.8, pointerId: 1 });
    fireEvent.pointerUp(column, { clientY: 1210 * 0.8, pointerId: 1 });

    expect(onChoose).toHaveBeenCalledWith(expect.objectContaining({ minutes: 30 }));
  });
});
