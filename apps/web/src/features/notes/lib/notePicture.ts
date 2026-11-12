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

export function pictureSizeOf(title: unknown): PictureSize {
  return title === "small" || title === "large" ? title : "medium";
}

export function isOwnPicture(src: unknown): src is string {
  return typeof src === "string" && src.startsWith(EMAIL_IMAGE_PATH);
}

/**
 * A picture Uncava hosts, inline so several sit in a row, kept in Markdown as `![alt](src "size")` — the title is its
 * size. A pasted `<img>` from anywhere else is not taken in: the email would never draw it.
 */
export const NotePictureNode = Image.extend({
  parseHTML() {
    return [{ tag: `img[src^="${EMAIL_IMAGE_PATH}"]` }];
  },
  renderHTML({ HTMLAttributes }) {
    const box = SIZE_BOX[pictureSizeOf(HTMLAttributes.title)];
    return [
      "img",
      {
        src: HTMLAttributes.src,
        alt: HTMLAttributes.alt ?? "",
        "data-size": pictureSizeOf(HTMLAttributes.title),
        style: `display:inline-block;vertical-align:middle;max-width:${box.width}px;max-height:${box.height}px;margin:0 12px 6px 0`,
      },
    ];
  },
}).configure({ inline: true, allowBase64: false });
