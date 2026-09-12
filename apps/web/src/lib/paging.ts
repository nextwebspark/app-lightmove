/**
 * How many rows a company list asks for. Strategy and Triage page the same way, off the same numbers.
 *
 * <p>100 is the ceiling because it is the API's: `company.list.max-page-size` refuses a larger `size`
 * rather than clamping it, so an option offered here that the server would reject is a broken page.
 */
export const PAGE_SIZE_OPTIONS = [25, 50, 75, 100];

export const DEFAULT_PAGE_SIZE = 50;
