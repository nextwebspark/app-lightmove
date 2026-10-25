import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WeekTimeGrid } from "./WeekTimeGrid";

const DAYS = ["2099-10-05", "2099-10-06"];

function renderGrid(onChoose = vi.fn()) {
  render(
    <WeekTimeGrid
      days={DAYS}
      timeZone="Asia/Dubai"
      busy={[{ start: "2099-10-05T06:00:00Z", end: "2099-10-05T07:00:00Z" }]}
      workingHours={[{ start: "2099-10-05T04:00:00Z", end: "2099-10-05T14:00:00Z" }]}
      choice={null}
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
});
