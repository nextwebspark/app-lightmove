import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import type { TalentHub } from "../api/types";

const SOURCE = "report-hubs";
const LIGHT_STYLE = "mapbox://styles/mapbox/light-v11";
const DARK_STYLE = "mapbox://styles/mapbox/dark-v11";
const FIT_PADDING = 48;
const MAX_FIT_ZOOM = 6;
const SINGLE_HUB_ZOOM = 4;

/**
 * The market chapter's hub map: one circle per city, its area scaled to the executives there.
 *
 * <p><b>Its own component, not the Companies screen's globe.</b> That one is built around a mandate's
 * companies and people — globe projection, a fixed Gulf centre, navigation controls, an executives
 * toggle — and bending it to draw eight circles would put report concerns inside the screen the
 * grid depends on. This draws hubs, fits to them, and does nothing else.
 *
 * <p>Circles are sized by the square root of the count so that <i>area</i> tracks headcount. Scaling
 * the radius instead makes a hub of forty look four times a hub of ten rather than twice it.
 */
export default function HubGlobe({
  accessToken,
  hubs,
  selectedCity,
  onSelect,
  onUnsupported,
}: {
  accessToken: string;
  /** Only hubs the geocoder has placed; the caller filters. */
  hubs: TalentHub[];
  selectedCity: string | null;
  onSelect: (city: string) => void;
  /** No WebGL: the caller drops back to the bar list alone. */
  onUnsupported: () => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<mapboxgl.Map | null>(null);
  const [dark, setDark] = useState(() => document.body.classList.contains("dark"));
  const [styleReady, setStyleReady] = useState(0);

  // The style-load handler fires again on every theme swap, so it must read the present rather than
  // the render that created the map. A layout effect, never a write during render: a render can be
  // discarded or run twice, and this has to hold what was committed.
  const latest = useRef({ hubs, selectedCity, onSelect });
  useLayoutEffect(() => {
    latest.current = { hubs, selectedCity, onSelect };
  });

  useEffect(() => {
    const observer = new MutationObserver(() => setDark(document.body.classList.contains("dark")));
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
        style: document.body.classList.contains("dark") ? DARK_STYLE : LIGHT_STYLE,
        center: [48, 25],
        zoom: 2,
        attributionControl: true,
      });
    } catch {
      onUnsupported();
      return;
    }
    mapRef.current = map;

    map.on("style.load", () => {
      map.addSource(SOURCE, {
        type: "geojson",
        data: collectionOf(latest.current.hubs, latest.current.selectedCity),
      });
      map.addLayer({
        id: `${SOURCE}-circle`,
        type: "circle",
        source: SOURCE,
        paint: {
          "circle-radius": ["get", "radius"],
          "circle-color": "#2563eb",
          "circle-opacity": 0.28,
          "circle-stroke-width": ["case", ["get", "selected"], 2.5, 1.2],
          "circle-stroke-color": "#2563eb",
        },
      });
      map.addLayer({
        id: `${SOURCE}-label`,
        type: "symbol",
        source: SOURCE,
        layout: {
          "text-field": ["get", "count"],
          "text-size": 11,
          "text-allow-overlap": true,
        },
        paint: {
          "text-color": document.body.classList.contains("dark") ? "#f8fafc" : "#111113",
        },
      });
      setStyleReady((tick) => tick + 1);
    });

    map.on("click", `${SOURCE}-circle`, (event) => {
      const city = event.features?.[0]?.properties?.city;
      if (city) latest.current.onSelect(String(city));
    });
    map.on("mouseenter", `${SOURCE}-circle`, () => {
      map.getCanvas().style.cursor = "pointer";
    });
    map.on("mouseleave", `${SOURCE}-circle`, () => {
      map.getCanvas().style.cursor = "";
    });

    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, [accessToken, onUnsupported]);

  useEffect(() => {
    mapRef.current?.setStyle(dark ? DARK_STYLE : LIGHT_STYLE);
  }, [dark]);

  useEffect(() => {
    const source = mapRef.current?.getSource(SOURCE) as mapboxgl.GeoJSONSource | undefined;
    source?.setData(collectionOf(hubs, selectedCity));
  }, [hubs, selectedCity, styleReady]);

  // Fit once the points are up. A lone hub has no bounds to fit, so it is centred at a zoom that
  // still shows the country around it rather than the maximum the fit would pick.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || hubs.length === 0) return;
    if (hubs.length === 1) {
      map.jumpTo({ center: pointOf(hubs[0]), zoom: SINGLE_HUB_ZOOM });
      return;
    }
    const bounds = hubs.reduce(
      (box, hub) => box.extend(pointOf(hub)),
      new mapboxgl.LngLatBounds(pointOf(hubs[0]), pointOf(hubs[0])),
    );
    map.fitBounds(bounds, { padding: FIT_PADDING, maxZoom: MAX_FIT_ZOOM, duration: 0 });
  }, [hubs, styleReady]);

  return <div ref={containerRef} className="size-full" aria-label="Talent hubs on a map" />;
}

const pointOf = (hub: TalentHub): [number, number] => [hub.point!.longitude, hub.point!.latitude];

function collectionOf(hubs: TalentHub[], selectedCity: string | null): GeoJSON.FeatureCollection {
  const largest = Math.max(...hubs.map((hub) => hub.count), 1);
  return {
    type: "FeatureCollection",
    features: hubs.map((hub) => ({
      type: "Feature",
      geometry: { type: "Point", coordinates: pointOf(hub) },
      properties: {
        city: hub.city,
        count: String(hub.count),
        // Area, not radius, tracks the headcount — see the component doc.
        radius: 9 + Math.sqrt(hub.count / largest) * 19,
        selected: hub.city === selectedCity,
      },
    })),
  };
}
