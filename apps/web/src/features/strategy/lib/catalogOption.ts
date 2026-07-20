/**
 * One entry of a fixed strategy catalog: `value` is the stable wire token the API speaks (and
 * validates against its enum), `label` is the display name only this side renders. Keeping the two
 * apart means UI copy can change without an API break or a data migration.
 */
export interface CatalogOption {
  value: string;
  label: string;
}
