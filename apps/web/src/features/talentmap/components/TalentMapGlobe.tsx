import mapboxgl, { type GeoJSONSource, type MapMouseEvent } from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";
import "./talentMapGlobe.css";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { boundsOf, type PinCollection } from "../lib/talentMapFeatures";

const SOURCE = "talent-map";
const LIGHT_STYLE = "mapbox://styles/mapbox/light-v11";
const DARK_STYLE = "mapbox://styles/mapbox/dark-v11";
const GULF: [number, number] = [48, 25];
const INITIAL_ZOOM = 2.6;
/**
 * Where selecting a row flies to. Deliberately past the city: a city+country geocode puts every
 * company in Dubai on one centroid, and the spread that separates them is a couple of kilometres —
 * at z9 that is four pixels, so the pins, and the executives ringed around their company, read as
 * one dot. This is the zoom at which they read as what they are.
 */
const CITY_ZOOM = 11;

/** Pin colours: the ink of the app's text for a company, sky for a person, amber for the selection. */
const INK = { light: "#15213a", dark: "#e2e8f0" };
const SKY = "#2563eb";
const AMBER = "#e2b65c";
const GROUND = { light: "#ffffff", dark: "#16171a" };

const CONTROL_BUTTON =
  "grid size-8 place-items-center rounded-[6px] border border-line bg-panel text-text2 shadow-panel " +
  "transition hover:text-text";

/**
 * The globe itself — the one component that imports `mapbox-gl`, and loaded lazily for it, so the
 * table view never downloads a map library.
 *
 * <p>Everything drawn comes from one GeoJSON source the parent computes; this component owns only
 * the Mapbox map, the layers over that source, the popup anchored to the selection, and the
 * theme, which it follows off `body.dark` because the style has to be swapped rather than restyled.
 * Selection and hover are the parent's state, mirrored here and in the panel, so the two views
 * never disagree about which row is which.
 */
export default function TalentMapGlobe({
  accessToken,
  features,
  selectedId,
  hoveredId,
  showExecutives,
  onSelect,
  onHover,
  onToggleExecutives,
  onUnsupported,
  renderPopup,
}: {
  accessToken: string;
  features: PinCollection;
  selectedId: string | null;
  hoveredId: string | null;
  showExecutives: boolean;
  onSelect: (id: string | null) => void;
  onHover: (id: string | null) => void;
  onToggleExecutives: () => void;
  /** No WebGL: the parent swaps in an explanation, and the Table toggle still works. */
  onUnsupported: () => void;
  /** The popup's body for a selected row; null closes it. */
  renderPopup: (id: string) => ReactNode;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<mapboxgl.Map | null>(null);
  const popupRef = useRef<mapboxgl.Popup | null>(null);
  const popupNodeRef = useRef<HTMLDivElement | null>(null);
  const [popupOpenFor, setPopupOpenFor] = useState<string | null>(null);
  const [dark, setDark] = useState(() => document.body.classList.contains("dark"));
  const [styleReady, setStyleReady] = useState(0);

  // The latest of everything the style-load handler needs, because that handler fires again on every
  // theme swap and must redraw from the present rather than from the render that created the map.
  const latest = useRef({ features, selectedId, hoveredId, dark, onSelect, onHover });
  latest.current = { features, selectedId, hoveredId, dark, onSelect, onHover };

  useEffect(() => {
    const observer = new MutationObserver(() =>
      setDark(document.body.classList.contains("dark")),
    );
    observer.observe(document.body, { attributes: true, attributeFilter: ["class"] });
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!containerRef.current) return;
    let map: mapboxgl.Map;
    try {
      map = new mapboxgl.Map({
        container: containerRef.current,
        accessToken,
        style: latest.current.dark ? DARK_STYLE : LIGHT_STYLE,
        projection: "globe",
        center: GULF,
        zoom: INITIAL_ZOOM,
        attributionControl: true,
      });
    } catch {
      onUnsupported();
      return;
    }
    mapRef.current = map;
    map.addControl(new mapboxgl.NavigationControl({ showCompass: false }), "top-right");

    const popupNode = document.createElement("div");
    popupNodeRef.current = popupNode;
    popupRef.current = new mapboxgl.Popup({
      closeButton: false,
      closeOnClick: false,
      offset: 16,
      maxWidth: "300px",
      className: "lm-map-popup",
    });

    map.on("style.load", () => {
      const { dark: isDark, features: current, selectedId: selected, hoveredId: hovered } = latest.current;
      map.setFog({
        color: isDark ? "rgb(18, 20, 26)" : "rgb(222, 228, 238)",
        "high-color": isDark ? "rgb(24, 30, 44)" : "rgb(196, 210, 232)",
        "horizon-blend": 0.02,
        "space-color": isDark ? "rgb(8, 8, 12)" : "rgb(241, 243, 247)",
        "star-intensity": isDark ? 0.35 : 0,
      });
      ensureLayers(map, current, isDark);
      applySelection(map, selected, isDark);
      applyHover(map, hovered);
      setStyleReady((tick) => tick + 1);
    });

    const pinLayers = ["companies", "executives"];
    map.on("mousemove", pinLayers, (event: MapMouseEvent) => {
      const feature = event.features?.[0];
      map.getCanvas().style.cursor = "pointer";
      latest.current.onHover(feature ? String(feature.properties?.rowId ?? "") || null : null);
    });
    map.on("mouseleave", pinLayers, () => {
      map.getCanvas().style.cursor = "";
      latest.current.onHover(null);
    });
    map.on("mouseenter", "clusters", () => {
      map.getCanvas().style.cursor = "pointer";
    });
    map.on("mouseleave", "clusters", () => {
      map.getCanvas().style.cursor = "";
    });
    map.on("click", pinLayers, (event: MapMouseEvent) => {
      const feature = event.features?.[0];
      const rowId = feature ? String(feature.properties?.rowId ?? "") : "";
      if (rowId) latest.current.onSelect(rowId);
    });
    map.on("click", "clusters", (event: MapMouseEvent) => {
      const feature = event.features?.[0];
      const clusterId = feature?.properties?.cluster_id as number | undefined;
      if (feature?.geometry.type !== "Point" || clusterId === undefined) return;
      const [longitude, latitude] = feature.geometry.coordinates as [number, number];
      (map.getSource(SOURCE) as GeoJSONSource).getClusterExpansionZoom(clusterId, (error, zoom) => {
        if (error || zoom === null || zoom === undefined) return;
        map.easeTo({ center: [longitude, latitude], zoom });
      });
    });
    map.on("click", (event: MapMouseEvent) => {
      const hit = map.queryRenderedFeatures(event.point, { layers: [...pinLayers, "clusters"] });
      if (hit.length === 0) latest.current.onSelect(null);
    });

    const resize = new ResizeObserver(() => map.resize());
    resize.observe(containerRef.current);

    return () => {
      resize.disconnect();
      popupRef.current?.remove();
      popupRef.current = null;
      map.remove();
      mapRef.current = null;
    };
    // The map is created once; everything that changes afterwards is applied by the effects below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken]);

  // The theme is a different style, and a style swap drops every layer: the style.load handler
  // above redraws them from `latest`. Tracked in a ref rather than read back off the map, because
  // the map's own style is not queryable until it has loaded.
  const appliedDark = useRef(dark);
  useEffect(() => {
    const map = mapRef.current;
    if (!map || appliedDark.current === dark) return;
    appliedDark.current = dark;
    map.setStyle(dark ? DARK_STYLE : LIGHT_STYLE);
  }, [dark]);

  useEffect(() => {
    const map = mapRef.current;
    const source = map?.getSource(SOURCE) as GeoJSONSource | undefined;
    source?.setData(features);
  }, [features, styleReady]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.getLayer("companies")) return;
    applySelection(map, selectedId, dark);
  }, [selectedId, dark, styleReady]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.getLayer("hover-label")) return;
    applyHover(map, hoveredId);
  }, [hoveredId, styleReady]);

  // The popup follows the selection: opened on the pin, moved to the next one, closed on none. The
  // camera moves only when the selection changes — a refetch that redraws the pins re-anchors the
  // popup below and must not yank the map back to it.
  useEffect(() => {
    const map = mapRef.current;
    const popup = popupRef.current;
    const node = popupNodeRef.current;
    if (!map || !popup || !node) return;
    const feature = selectedId
      ? latest.current.features.features.find((candidate) => candidate.id === selectedId)
      : undefined;
    if (!feature) {
      popup.remove();
      setPopupOpenFor(null);
      return;
    }
    const coordinates = feature.geometry.coordinates;
    popup.setLngLat(coordinates).setDOMContent(node).addTo(map);
    setPopupOpenFor(selectedId);
    map.easeTo({ center: coordinates, zoom: Math.max(map.getZoom(), CITY_ZOOM), duration: 700 });
  }, [selectedId]);

  useEffect(() => {
    const popup = popupRef.current;
    if (!popup || !popupOpenFor) return;
    const feature = features.features.find((candidate) => candidate.id === popupOpenFor);
    if (!feature) {
      popup.remove();
      setPopupOpenFor(null);
      return;
    }
    popup.setLngLat(feature.geometry.coordinates);
  }, [features, popupOpenFor]);

  const fitToMapping = () => {
    const map = mapRef.current;
    const bounds = boundsOf(features);
    if (!map || !bounds) return;
    map.fitBounds(bounds, { padding: 64, maxZoom: CITY_ZOOM, duration: 900 });
  };

  return (
    <div className="lm-map-globe relative size-full">
      <div ref={containerRef} className="size-full" data-testid="talent-map-globe" />

      <div className="absolute end-2.5 top-[84px] flex flex-col gap-1.5">
        <button
          type="button"
          onClick={fitToMapping}
          aria-label="Fit to mapping"
          title="Fit to mapping"
          className={CONTROL_BUTTON}
        >
          <Icon d={ICONS.fit} size={15} />
        </button>
        <button
          type="button"
          onClick={onToggleExecutives}
          aria-pressed={showExecutives}
          aria-label={showExecutives ? "Hide executives" : "Show executives"}
          title={showExecutives ? "Hide executives" : "Show executives"}
          className={CONTROL_BUTTON}
        >
          <Icon d={showExecutives ? ICONS.eye : ICONS.eyeOff} size={15} />
        </button>
      </div>

      {popupOpenFor && popupNodeRef.current
        ? createPortal(renderPopup(popupOpenFor), popupNodeRef.current)
        : null}
    </div>
  );
}

/** Adds the source and every layer over it, once per style — safe to call again after a swap. */
function ensureLayers(map: mapboxgl.Map, data: PinCollection, dark: boolean) {
  if (map.getSource(SOURCE)) return;
  const ink = dark ? INK.dark : INK.light;
  const ground = dark ? GROUND.dark : GROUND.light;

  map.addSource(SOURCE, {
    type: "geojson",
    data,
    cluster: true,
    clusterRadius: 40,
    clusterMaxZoom: 10,
    clusterProperties: {
      companies: ["+", ["case", ["==", ["get", "kind"], "company"], 1, 0]],
      executives: ["+", ["case", ["==", ["get", "kind"], "executive"], 1, 0]],
    },
  });

  map.addLayer({
    id: "clusters",
    type: "circle",
    source: SOURCE,
    filter: ["has", "point_count"],
    paint: {
      "circle-color": ink,
      "circle-opacity": 0.88,
      "circle-radius": ["step", ["get", "point_count"], 14, 10, 18, 50, 23],
      "circle-stroke-width": 2,
      "circle-stroke-color": ground,
    },
  });
  map.addLayer({
    id: "cluster-count",
    type: "symbol",
    source: SOURCE,
    filter: ["has", "point_count"],
    layout: {
      "text-field": [
        "concat",
        ["to-string", ["get", "companies"]],
        " · ",
        ["to-string", ["get", "executives"]],
      ],
      "text-size": 11,
      "text-font": ["DIN Pro Medium", "Arial Unicode MS Regular"],
      "text-allow-overlap": true,
    },
    paint: { "text-color": ground },
  });
  map.addLayer({
    id: "selected-halo",
    type: "circle",
    source: SOURCE,
    filter: ["==", ["get", "rowId"], ""],
    paint: {
      "circle-color": AMBER,
      "circle-opacity": 0.28,
      "circle-radius": ["interpolate", ["linear"], ["zoom"], 2, 12, 10, 22],
    },
  });
  map.addLayer({
    id: "companies",
    type: "circle",
    source: SOURCE,
    filter: ["all", ["!", ["has", "point_count"]], ["==", ["get", "kind"], "company"]],
    paint: {
      "circle-color": ink,
      "circle-opacity": 0.92,
      "circle-radius": [
        "interpolate",
        ["linear"],
        ["zoom"],
        2,
        ["step", ["get", "employees"], 4, 500, 5, 5000, 6.5],
        10,
        ["step", ["get", "employees"], 8, 500, 10, 5000, 13],
      ],
      "circle-stroke-width": 1.5,
      "circle-stroke-color": ground,
    },
  });
  map.addLayer({
    id: "executives",
    type: "circle",
    source: SOURCE,
    filter: ["all", ["!", ["has", "point_count"]], ["==", ["get", "kind"], "executive"]],
    paint: {
      "circle-color": SKY,
      "circle-opacity": 0.92,
      "circle-radius": ["interpolate", ["linear"], ["zoom"], 2, 3, 10, 6],
      "circle-stroke-width": 1.5,
      "circle-stroke-color": ground,
    },
  });
  map.addLayer({
    id: "hover-label",
    type: "symbol",
    source: SOURCE,
    filter: ["==", ["get", "rowId"], ""],
    layout: {
      "text-field": ["get", "hoverLabel"],
      "text-size": 12,
      "text-font": ["DIN Pro Medium", "Arial Unicode MS Regular"],
      "text-anchor": "bottom",
      "text-offset": [0, -1.4],
      "text-allow-overlap": true,
      "text-ignore-placement": true,
    },
    paint: {
      "text-color": ink,
      "text-halo-color": ground,
      "text-halo-width": 1.6,
    },
  });
}

function applySelection(map: mapboxgl.Map, selectedId: string | null, dark: boolean) {
  const ink = dark ? INK.dark : INK.light;
  const id = selectedId ?? "";
  map.setFilter("selected-halo", ["==", ["get", "rowId"], id]);
  map.setPaintProperty("companies", "circle-color", ["case", ["==", ["get", "rowId"], id], AMBER, ink]);
  map.setPaintProperty("executives", "circle-color", ["case", ["==", ["get", "rowId"], id], AMBER, SKY]);
}

function applyHover(map: mapboxgl.Map, hoveredId: string | null) {
  map.setFilter("hover-label", ["==", ["get", "rowId"], hoveredId ?? ""]);
}
