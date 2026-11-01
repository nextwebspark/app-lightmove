import type { Area } from "react-easy-crop";

/** What the server keeps; anything larger would only be shrunk again on its side. */
export const PHOTO_EDGE_PX = 512;

/**
 * Draws the framed square of a picture the browser has already decoded, as a JPEG. Done here rather than on the
 * server so any format the browser opens (an iPhone's HEIC in Safari, WebP) arrives as one the server reads.
 */
export async function croppedPhoto(imageUrl: string, area: Area): Promise<Blob> {
  const image = await loadImage(imageUrl);
  const edge = Math.min(PHOTO_EDGE_PX, Math.round(area.width));
  const canvas = document.createElement("canvas");
  canvas.width = edge;
  canvas.height = edge;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("No 2D canvas");
  // A transparent PNG would turn black as a JPEG.
  context.fillStyle = "#ffffff";
  context.fillRect(0, 0, edge, edge);
  context.drawImage(image, area.x, area.y, area.width, area.height, 0, 0, edge, edge);
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("Could not draw the photo"))), "image/jpeg", 0.9);
  });
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("The browser could not open the picture"));
    image.src = url;
  });
}
