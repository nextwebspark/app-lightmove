import { KeyboardSensor, PointerSensor, useSensor, useSensors } from "@dnd-kit/core";
import { restrictToParentElement, restrictToVerticalAxis } from "@dnd-kit/modifiers";
import { sortableKeyboardCoordinates } from "@dnd-kit/sortable";

/** A few pixels before a drag starts, so a click on the handle is still a click. */
const POINTER_SENSOR = { activationConstraint: { distance: 4 } };
const KEYBOARD_SENSOR = { coordinateGetter: sortableKeyboardCoordinates };

/** Holds a dragged row to its list's axis and inside the element that parents the rows. */
export const VERTICAL_LIST_MODIFIERS = [restrictToVerticalAxis, restrictToParentElement];

/**
 * The sensors of a list reordered by its rows' handles, pointer and keyboard.
 *
 * <p>The options are module constants, never inline: a fresh object each render gives useSensor a new
 * descriptor, which hands DndContext a new sensors array and makes it re-initialise — a reorder quietly
 * stopped working after the first keystroke in a row.
 */
export function useSortableSensors() {
  return useSensors(useSensor(PointerSensor, POINTER_SENSOR), useSensor(KeyboardSensor, KEYBOARD_SENSOR));
}
