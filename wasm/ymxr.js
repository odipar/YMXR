// The twelve tools of YMXR and YMXS's ymxs-check in a web page
// (doc/tools.md 20). A run is the tool's executable run once: its flags,
// standard input in, standard output and the exit code out, and standard
// error a line at a time while it runs. The tools run in a Web Worker, one
// run at a time in the order they are asked for, from ymxr.wasm beside this
// file.
//
//   import { run } from "./ymxr/ymxr.js";
//   const { exitCode, output, report } = await run("ymxs-to-sndh", ["-silent"], json,
//       { onReport: (line) => console.log(line) });
//
// exitCode is 0 where the tool wrote its output, 1 where it rejected the
// input and 2 where the call is wrong, as for the executable.

import { runParallel } from "./ymxr-wasi.js";

const wasm = new URL("./ymxr.wasm", import.meta.url).href;
const script = new URL("./ymxr-worker.js", import.meta.url);

let worker;
let runs = 0;
let queue = Promise.resolve();
// the stop of the run in progress, which stop() calls
let running;

function started() {
  worker ??= new Worker(script, { type: "module" });
  return worker;
}

function call(at, tool, flags, { input, files, onReport }, signal, module) {
  return new Promise((resolve, reject) => {
    const id = ++runs;
    const end = () => {
      at.removeEventListener("message", listen);
      at.removeEventListener("error", failed);
      at.removeEventListener("messageerror", failed);
      signal.removeEventListener("abort", abort);
    };
    const abort = () => {
      end();
      reject(new DOMException("The run was stopped", "AbortError"));
    };
    const failed = event => {
      end();
      reject(new Error(event.message ?? "The worker could not run the tool"));
    };
    const listen = ({ data }) => {
      if (data.id !== id) return;
      if (data.line !== undefined) {
        onReport?.(data.line);
        return;
      }
      end();
      if (data.error !== undefined) reject(new Error(data.error));
      else resolve(data);
    };
    if (signal.aborted) {
      abort();
      return;
    }
    at.addEventListener("message", listen);
    at.addEventListener("error", failed);
    at.addEventListener("messageerror", failed);
    signal.addEventListener("abort", abort, { once: true });
    try {
      at.postMessage({ id, wasm, module, tool, flags: [...flags], input, files },
        module ? [input.buffer] : []);
    } catch (error) {
      end();
      reject(error);
    }
  });
}

/**
 * Runs a tool once.
 *
 * @param tool the tool's name: ymxs-to-sndh, ymxs-to-prg, ymxs-to-ymxr,
 *     ymxs-check or any other of the thirteen
 * @param flags the tool's arguments, as its executable reads them
 * @param input standard input: a Uint8Array, a string, an ArrayBuffer or
 *     an array of byte values
 * @param options onReport, called with each line of standard error as the
 *     tool writes it; files, an object of path to bytes a tool reads by
 *     name; signal, an AbortSignal that stops the run, its {@code Promise} then
 *     rejected with an AbortError
 * @returns a {@code Promise} of { exitCode, output: Uint8Array, report: string }
 */
export function run(tool, flags = [], input = new Uint8Array(0), { onReport, files = {}, signal } = {}) {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input)
      : input instanceof Uint8Array ? input : new Uint8Array(input);
  const one = async () => {
    if (signal?.aborted) throw new DOMException("The run was stopped before it started", "AbortError");
    const controller = new AbortController();
    const at = started();
    const stop = () => {
      if (controller.signal.aborted) return;
      controller.abort();
      at.terminate();
      worker = undefined;
    };
    const invoke = (tool, flags, options) => call(at, tool, flags, options, controller.signal);
    const pack = async (input, module) => {
      controller.signal.throwIfAborted();
      const packer = new Worker(script, { type: "module" });
      try {
        return await call(packer, "_ymxr-pack-columns", [], { input }, controller.signal, module);
      } finally {
        packer.terminate();
      }
    };
    const progress = [];
    running = stop;
    signal?.addEventListener("abort", stop, { once: true });
    try {
      const cores = globalThis.navigator?.hardwareConcurrency ?? 1;
      const { exitCode, output, report } = await runParallel(invoke, pack, Math.min(cores, 4),
        tool, flags, { input: bytes, files, onReport }, line => {
          progress.push(line + "\n");
          onReport?.(line);
        });
      return { exitCode, output, report: progress.join("") + report };
    } catch (error) {
      stop();
      throw error;
    } finally {
      signal?.removeEventListener("abort", stop);
      running = undefined;
    }
  };
  const done = queue.then(one, one);
  queue = done.catch(() => undefined);
  return done;
}

/**
 * {@link run} with the lines of standard error as a fourth argument, for a
 * caller whose interop passes a function but no object of options.
 */
export function runTool(tool, flags, input, onReport) {
  return run(tool, flags, input, { onReport });
}

/**
 * Stops the run in progress as its signal would, for a caller whose
 * interop passes no AbortSignal: its {@code Promise} rejects with an
 * AbortError, and the runs after it start as asked for.
 */
export function stop() {
  running?.();
}
