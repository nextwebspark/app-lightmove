import Image from "@tiptap/extension-image";

/** Where Uncava serves an email's pictures; the only pictures an editor that takes them keeps. */
export const EMAIL_IMAGE_PATH = "/api/v1/email-images/";

export const PICTURE_SIZES = ["small", "medium", "large"] as const;
export type PictureSize = (typeof PICTURE_SIZES)[number];

/** The box each size is drawn inside, as the server's `SignaturePictureSize` draws it in the email. */
const SIZE_BOX: Record<PictureSize, { width: number; height: number }> = {
  small: { width: 200, height: 40 },
  medium: { width: 300, height: 64 },
  large: { width: 600, height: 200 },
};

export const PICTURE_SHAPES = ["square", "rounded", "circle"] as const;
export type PictureShape = (typeof PICTURE_SHAPES)[number];

/** Each shape as a 24-unit icon path, for the buttons that pick it. */
export const PICTURE_SHAPE_GLYPHS: Record<PictureShape, string> = {
  square: "M5 5h14v14H5z",
  rounded: "M9 5h6a4 4 0 0 1 4 4v6a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4V9a4 4 0 0 1 4-4z",
  circle: "M12 5a7 7 0 1 0 0 14a7 7 0 1 0 0-14z",
};

function wordsOf(title: unknown): string[] {
  return typeof title === "string" ? title.trim().toLowerCase().split(/\s+/) : [];
}

export function pictureSizeOf(title: unknown): PictureSize {
  return wordsOf(title).find((word): word is PictureSize => word === "small" || word === "large") ?? "medium";
}

export function pictureShapeOf(title: unknown): PictureShape {
  return wordsOf(title).find((word): word is PictureShape => word === "rounded" || word === "circle") ?? "square";
}

/** The title a picture keeps its size and shape in, `"small circle"`; medium and square are left unsaid. */
export function pictureTitleOf(size: PictureSize, shape: PictureShape): string | null {
  const words = [size === "medium" ? null : size, shape === "square" ? null : shape].filter(Boolean);
  return words.length > 0 ? words.join(" ") : null;
}

/** As the server cuts the picture: a circle is its centred square, a rounded one keeps its proportions. */
function shapeStyleOf(shape: PictureShape, box: { width: number; height: number }): string {
  if (shape === "circle") return `width:${box.height}px;height:${box.height}px;object-fit:cover;border-radius:50%;`;
  if (shape === "rounded") return "border-radius:12px;";
  return "";
}

/**
 * A picture Uncava hosts, inline so several sit in a row, kept in Markdown as `![alt](src "size shape")` — the title is
 * its size and shape. A pasted `<img>` from anywhere else is not taken in: the email would never draw it.
 */
export const NotePictureNode = Image.extend({
  parseHTML() {
    return [{ tag: `img[src^="${EMAIL_IMAGE_PATH}"]` }];
  },
  renderHTML({ HTMLAttributes }) {
    const box = SIZE_BOX[pictureSizeOf(HTMLAttributes.title)];
    const shape = pictureShapeOf(HTMLAttributes.title);
    return [
      "img",
      {
        src: HTMLAttributes.src,
        alt: HTMLAttributes.alt ?? "",
        "data-size": pictureSizeOf(HTMLAttributes.title),
        "data-shape": shape,
        style: `display:inline-block;vertical-align:middle;max-width:${box.width}px;max-height:${box.height}px;margin:0 12px 6px 0;${shapeStyleOf(shape, box)}`,
      },
    ];
  },
}).configure({ inline: true, allowBase64: false });
