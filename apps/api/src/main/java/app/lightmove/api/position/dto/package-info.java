/**
 * <b>The position brief's HTTP contract.</b> One read for the whole brief, a snapshot PUT per section.
 * Size and range ceilings are enforced; cross-field agreement deliberately is not, so autosave can
 * persist a half-typed section.
 */
package app.lightmove.api.position.dto;
