import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { useEscapeKey } from "./useEscapeKey";

/**
 * That Escape reaches the topmost overlay and only that one.
 *
 * <p>Every overlay used to listen on `document` itself, which was invisible while they were modal
 * and mutually exclusive. The assistant panel is neither — it docks beside the page so a drawer can
 * be opened from the grid while it is open — which turned "one press closes two things" into the
 * ordinary case.
 */
function Layer({ name, onClose }: { name: string; onClose: () => void }) {
  useEscapeKey(true, onClose);
  return <div>{name}</div>;
}

describe("useEscapeKey", () => {
  it("closes only the last overlay opened", async () => {
    const closeUnder = vi.fn();
    const closeOver = vi.fn();
    render(
      <>
        <Layer name="under" onClose={closeUnder} />
        <Layer name="over" onClose={closeOver} />
      </>,
    );

    await userEvent.keyboard("{Escape}");

    expect(closeOver).toHaveBeenCalledOnce();
    expect(closeUnder).not.toHaveBeenCalled();
  });

  it("hands Escape back to the one underneath once the top one unmounts", async () => {
    const closeUnder = vi.fn();
    const closeOver = vi.fn();

    function Stack() {
      const [overOpen, setOverOpen] = useState(true);
      return (
        <>
          <Layer name="under" onClose={closeUnder} />
          {overOpen && (
            <Layer
              name="over"
              onClose={() => {
                closeOver();
                setOverOpen(false);
              }}
            />
          )}
        </>
      );
    }

    render(<Stack />);
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByText("over")).not.toBeInTheDocument();

    await userEvent.keyboard("{Escape}");

    expect(closeUnder).toHaveBeenCalledOnce();
  });

  it("ignores a layer that is not active", async () => {
    const closeInactive = vi.fn();
    function Inactive() {
      useEscapeKey(false, closeInactive);
      return null;
    }
    render(<Inactive />);

    await userEvent.keyboard("{Escape}");

    expect(closeInactive).not.toHaveBeenCalled();
  });
});
