import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

/** Opens the status picker whose label matches and picks the status with that label. */
export async function pickStatus(picker: RegExp, statusLabel: string) {
  await userEvent.click(screen.getByRole("button", { name: picker }));
  const list = await screen.findByRole("listbox");
  await userEvent.click(await within(list).findByRole("option", { name: statusLabel }));
}
