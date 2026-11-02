import { useEffect, useState } from "react";
import Cropper, { type Area, type Point } from "react-easy-crop";
import { Button, FormError, Modal } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { croppedPhoto } from "../lib/croppedPhoto";

const UNOPENABLE = "This browser can't open that image. Use a JPG or PNG.";
const UNREACHABLE = "Could not reach Uncava. Check your connection and try again.";

/**
 * Frames the picked picture as the square everyone sees — drag to move, the slider zooms — and saves it.
 * `Settings.dc.html`'s Profile photo dialog. Loaded on demand, so the cropper stays out of the main bundle.
 */
export default function ProfilePhotoDialog({
  file,
  onClose,
  onSave,
}: {
  file: File;
  onClose: () => void;
  onSave: (photo: Blob) => Promise<void>;
}) {
  const imageUrl = useObjectUrl(file);
  const [crop, setCrop] = useState<Point>({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [area, setArea] = useState<Area | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Two failures, said apart: drawing the square is the browser's, sending it is the network's or the server's.
  const handleSave = async () => {
    if (!imageUrl || !area) return;
    setError(null);
    setSaving(true);
    let photo: Blob;
    try {
      photo = await croppedPhoto(imageUrl, area);
    } catch {
      setError(UNOPENABLE);
      setSaving(false);
      return;
    }
    try {
      await onSave(photo);
    } catch (failure) {
      setError(failure instanceof ApiRequestError ? (failure.fieldErrors.file ?? messageFor(failure)) : UNREACHABLE);
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Profile photo"
      subtitle="Drag to position, zoom to fit your face"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={handleSave} loading={saving} disabled={!area}>Save photo</Button>
        </div>
      }
    >
      <FormError message={error} />
      <div className="relative h-[300px] overflow-hidden rounded-lg bg-u-bg">
        {imageUrl && (
          <Cropper
            image={imageUrl}
            crop={crop}
            zoom={zoom}
            aspect={1}
            cropShape="round"
            showGrid={false}
            onCropChange={setCrop}
            onZoomChange={setZoom}
            onCropComplete={(_, pixels) => setArea(pixels)}
            mediaProps={{ onError: () => setError(UNOPENABLE) }}
          />
        )}
      </div>
      <label className="mt-4 flex items-center gap-3">
        <span className="type-label text-u-text3">Zoom</span>
        <input
          type="range"
          min={1}
          max={3}
          step={0.01}
          value={zoom}
          aria-label="Zoom"
          onChange={(event) => setZoom(Number(event.target.value))}
          className="flex-1 accent-u-accent"
        />
      </label>
    </Modal>
  );
}

/** An object URL is a registration: revoked when the file changes or the dialog closes, or it pins the file. */
function useObjectUrl(file: File): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    const created = URL.createObjectURL(file);
    setUrl(created);
    return () => URL.revokeObjectURL(created);
  }, [file]);
  return url;
}
