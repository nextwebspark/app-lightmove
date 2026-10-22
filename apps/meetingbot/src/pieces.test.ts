import { describe, expect, it } from "vitest";
import { RunGoneError, type NotetakerApi, type PieceKind } from "./api.js";
import { PieceUploader } from "./pieces.js";

function recordingApi(failures: number[] = []) {
  const uploads: Array<[PieceKind, number, number]> = [];
  let calls = 0;
  const api: NotetakerApi = {
    report: async () => undefined,
    heartbeat: async () => ({ leave: false }),
    complete: async () => undefined,
    async upload(kind, sequence, content) {
      calls++;
      if (failures.includes(calls)) {
        throw new Error("connection reset");
      }
      uploads.push([kind, sequence, content.byteLength]);
    },
  };
  return { api, uploads };
}

const noWait = async () => undefined;

describe("PieceUploader", () => {
  it("numbers each kind's pieces from one and sends them in the order they were made", async () => {
    const { api, uploads } = recordingApi();
    const uploader = new PieceUploader(api, noWait);
    uploader.enqueue("audio", new Uint8Array(3));
    uploader.enqueue("captions", new Uint8Array(2));
    uploader.enqueue("audio", new Uint8Array(5));
    uploader.enqueue("audio", new Uint8Array(0));

    expect(await uploader.drain()).toBe(0);
    expect(uploads).toEqual([["audio", 1, 3], ["captions", 1, 2], ["audio", 2, 5]]);
  });

  it("sends a piece again after a failure, under the same number", async () => {
    const { api, uploads } = recordingApi([1, 2]);
    const uploader = new PieceUploader(api, noWait);
    uploader.enqueue("audio", new Uint8Array(3));
    expect(await uploader.drain()).toBe(0);
    expect(uploads).toEqual([["audio", 1, 3]]);
  });

  it("gives a piece up after its attempts, and at once when the run is gone", async () => {
    const gone: NotetakerApi = { ...recordingApi().api, upload: async () => {
      throw new RunGoneError("closed");
    } };
    const uploader = new PieceUploader(gone, noWait);
    uploader.enqueue("audio", new Uint8Array(1));
    uploader.enqueue("audio", new Uint8Array(1));
    expect(await uploader.drain()).toBe(2);
  });
});
