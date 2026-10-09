import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useRef, useState, type ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { useFocusTrap } from "./useFocusTrap";

function Layer({
  label,
  startAt,
  children,
}: {
  label: string;
  startAt?: "field" | "panel";
  children: ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useFocusTrap(ref, true, { startAt });
  return (
    <div ref={ref} role="dialog" aria-label={label} tabIndex={-1}>
      {children}
    </div>
  );
}

function Page({ children }: { children: (close: () => void) => ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)}>Open</button>
      <button>Behind</button>
      {open && children(() => setOpen(false))}
    </>
  );
}

describe("useFocusTrap", () => {
  it("starts at the first field, ahead of any button before it", async () => {
    render(
      <Layer label="Form">
        <button>Help</button>
        <input aria-label="Name" />
      </Layer>,
    );

    expect(screen.getByRole("textbox", { name: "Name" })).toHaveFocus();
  });

  it("starts at the first control when there is no field", () => {
    render(
      <Layer label="Confirm">
        <button>Keep</button>
        <button>Remove</button>
      </Layer>,
    );

    expect(screen.getByRole("button", { name: "Keep" })).toHaveFocus();
  });

  it("starts at the panel itself when asked to", () => {
    render(
      <Layer label="Record" startAt="panel">
        <input aria-label="Name" />
      </Layer>,
    );

    expect(screen.getByRole("dialog", { name: "Record" })).toHaveFocus();
  });

  it("keeps Tab and Shift-Tab inside the panel", async () => {
    const user = userEvent.setup();
    render(
      <Page>
        {() => (
          <Layer label="Form">
            <input aria-label="First" />
            <button>Last</button>
          </Layer>
        )}
      </Page>,
    );
    await user.click(screen.getByRole("button", { name: "Open" }));

    await user.tab();
    expect(screen.getByRole("button", { name: "Last" })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole("textbox", { name: "First" })).toHaveFocus();
    await user.tab({ shift: true });
    expect(screen.getByRole("button", { name: "Last" })).toHaveFocus();
  });

  it("hands focus back to whatever opened it", async () => {
    const user = userEvent.setup();
    render(
      <Page>
        {(close) => (
          <Layer label="Form">
            <button onClick={close}>Close</button>
          </Layer>
        )}
      </Page>,
    );

    await user.click(screen.getByRole("button", { name: "Open" }));
    expect(screen.getByRole("button", { name: "Close" })).toHaveFocus();
    await user.keyboard("{Enter}");

    expect(screen.getByRole("button", { name: "Open" })).toHaveFocus();
  });

  it("traps only the top layer, and returns into the one beneath when it closes", async () => {
    const user = userEvent.setup();
    function Stack() {
      const [confirmOpen, setConfirmOpen] = useState(false);
      return (
        <Layer label="Drawer">
          <button onClick={() => setConfirmOpen(true)}>Remove</button>
          <button>Edit</button>
          {confirmOpen && (
            <Layer label="Confirm">
              <button onClick={() => setConfirmOpen(false)}>Cancel</button>
              <button>Confirm</button>
            </Layer>
          )}
        </Layer>
      );
    }
    render(<Stack />);
    await user.click(screen.getByRole("button", { name: "Remove" }));

    expect(screen.getByRole("button", { name: "Cancel" })).toHaveFocus();
    await user.tab();
    await user.tab();
    expect(screen.getByRole("button", { name: "Cancel" })).toHaveFocus();

    await user.keyboard("{Enter}");
    expect(screen.getByRole("button", { name: "Remove" })).toHaveFocus();
    await user.tab();
    await user.tab();
    expect(screen.getByRole("button", { name: "Remove" })).toHaveFocus();
  });
});
