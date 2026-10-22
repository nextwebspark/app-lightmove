/**
 * Installed in the page before Meet's own code runs (`evaluateOnNewDocument`). It mixes every remote audio track
 * the call's peer connections receive into one stream and records it in self-contained 30-second WebM pieces —
 * a new recorder per piece, so any piece can be transcribed alone and a crash loses only the one being made.
 * Each finished piece is handed to Node through the exposed `__uncavaAudioPiece` as base64.
 *
 * Self-contained like the screen readers: it is sent to the page as its source text.
 */
export function installAudioRecorder(): void {
  const page = window as unknown as Record<string, unknown> & Window;
  const sources = new Map<string, MediaStreamAudioSourceNode>();
  let context: AudioContext | null = null;
  let destination: MediaStreamAudioDestinationNode | null = null;

  const ensureMixer = () => {
    if (!context) {
      context = new AudioContext();
      destination = context.createMediaStreamDestination();
    }
    return { context, destination: destination as MediaStreamAudioDestinationNode };
  };

  const addTrack = (track: MediaStreamTrack) => {
    if (track.kind !== "audio" || sources.has(track.id)) {
      return;
    }
    const mixer = ensureMixer();
    const source = mixer.context.createMediaStreamSource(new MediaStream([track]));
    source.connect(mixer.destination);
    sources.set(track.id, source);
    track.addEventListener("ended", () => {
      source.disconnect();
      sources.delete(track.id);
    });
  };

  const Original = window.RTCPeerConnection;
  const Wrapped = function (this: RTCPeerConnection, ...args: ConstructorParameters<typeof RTCPeerConnection>) {
    const connection = new Original(...args);
    connection.addEventListener("track", (event) => addTrack(event.track));
    return connection;
  } as unknown as typeof RTCPeerConnection;
  Wrapped.prototype = Original.prototype;
  Object.setPrototypeOf(Wrapped, Original);
  window.RTCPeerConnection = Wrapped;

  const toBase64 = (bytes: Uint8Array) => {
    let binary = "";
    for (let offset = 0; offset < bytes.length; offset += 0x8000) {
      binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
    }
    return btoa(binary);
  };

  let recorder: MediaRecorder | null = null;
  let timer: ReturnType<typeof setInterval> | null = null;

  const startPiece = () => {
    const mixer = ensureMixer();
    const chunks: Blob[] = [];
    const current = new MediaRecorder(mixer.destination.stream, { mimeType: "audio/webm;codecs=opus" });
    current.ondataavailable = (event) => {
      if (event.data.size > 0) {
        chunks.push(event.data);
      }
    };
    current.onstop = async () => {
      const bytes = new Uint8Array(await new Blob(chunks, { type: "audio/webm" }).arrayBuffer());
      const send = page.__uncavaAudioPiece as ((encoded: string) => Promise<void>) | undefined;
      if (bytes.byteLength > 0 && send) {
        await send(toBase64(bytes));
      }
    };
    current.start();
    recorder = current;
  };

  page.__uncavaStartRecording = (pieceMs: number) => {
    const mixer = ensureMixer();
    void mixer.context.resume();
    startPiece();
    timer = setInterval(() => {
      recorder?.stop();
      startPiece();
    }, pieceMs);
  };

  page.__uncavaStopRecording = () =>
    new Promise<void>((resolve) => {
      if (timer) {
        clearInterval(timer);
      }
      if (!recorder || recorder.state === "inactive") {
        resolve();
        return;
      }
      recorder.addEventListener("stop", () => setTimeout(resolve, 1_000));
      recorder.stop();
    });
}
