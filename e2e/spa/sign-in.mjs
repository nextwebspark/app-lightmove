/** Fills /login's two steps, email then password, on a page already showing it. */
export async function fillSignIn(page, email, password) {
  await page.getByPlaceholder("you@firm.com").fill(email);
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByPlaceholder("••••••••").fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}
