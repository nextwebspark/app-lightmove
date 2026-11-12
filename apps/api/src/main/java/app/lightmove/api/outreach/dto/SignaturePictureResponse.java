package app.lightmove.api.outreach.dto;

/** A picture uploaded for a Custom signature: {@code src} is what the editor writes into the signature's Markdown. */
public record SignaturePictureResponse(String token, String src, int width, int height) {}
