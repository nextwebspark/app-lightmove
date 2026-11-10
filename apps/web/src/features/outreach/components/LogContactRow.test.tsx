import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { LogContactRow } from "./LogContactRow";

describe("LogContactRow", () => {
  it("offers Log WhatsApp beside the other ways to log, drawn with WhatsApp's own mark", async () => {
    const onLog = vi.fn();
    render(<LogContactRow onLog={onLog} />);

    const button = screen.getByRole("button", { name: "Log WhatsApp" });
    expect(button.querySelector('img[src="/brand/whatsapp-glyph-black.svg"]')).not.toBeNull();
    await userEvent.click(button);

    expect(onLog).toHaveBeenCalledWith("whatsapp");
    for (const name of ["Log call", "Log meeting", "Log LinkedIn"]) {
      expect(screen.getByRole("button", { name })).toBeInTheDocument();
    }
  });
});
