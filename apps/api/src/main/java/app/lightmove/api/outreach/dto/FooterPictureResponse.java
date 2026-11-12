package app.lightmove.api.outreach.dto;

import app.lightmove.api.core.storage.constant.PictureShape;

/** A footer picture as the editor shows it: {@code src} is the upload as sent, the shape applied on top. */
public record FooterPictureResponse(String token, String src, int width, int height, PictureShape shape) {}
