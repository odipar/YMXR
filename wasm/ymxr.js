// The twelve tools of YMXR in a web page (doc/tools.md 20). A run is the
// tool's executable run once: its flags, standard input in, standard
// output and the exit code out, and standard error a line at a time while
// it runs. The tools run in a Web Worker, one run at a time in the order
// they are asked for, from ymxr.wasm beside this file.
//
//   import { run } from "./ymxr/ymxr.js";
//   const { exitCode, output, report } = await run("ymxs-to-sndh", ["-silent"], json,
//       { onReport: (line) => console.log(line) });
//
// exitCode is 0 where the tool wrote its output, 1 where it rejected the
// input and 2 where the call is wrong, as for the executable.

const wasm = new URL("./ymxr.wasm", import.meta.url).href;
const script = new URL("./ymxr-worker.js", import.meta.url);

let worker;
let runs = 0;
let queue = Promise.resolve();

function started() {
  worker ??= new Worker(script, { type: "module" });
  return worker;
}

/**
 * Runs a tool once.
 *
 * @param tool the tool's name: ymxs-to-sndh, ymxs-to-prg, ymxs-to-ymxr or
 *     any other of the twelve
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
  const one = () =>
    new Promise((resolve, reject) => {
      if (signal?.aborted) {
        reject(new DOMException("The run was stopped before it started", "AbortError"));
        return;
      }
      const id = ++runs;
      const at = started();
      const stop = () => {
        // a tool runs to its end once started, so a stopped run ends the
        // worker, and the next run starts another
        at.terminate();
        worker = undefined;
        reject(new DOMException("The run was stopped", "AbortError"));
      };
      const listen = ({ data }) => {
        if (data.id !== id) {
          return;
        }
        if (data.line !== undefined) {
          onReport?.(data.line);
          return;
        }
        at.removeEventListener("message", listen);
        signal?.removeEventListener("abort", stop);
        if (data.error !== undefined) {
          reject(new Error(data.error));
        } else {
          resolve({ exitCode: data.exitCode, output: data.output, report: data.report });
        }
      };
      at.addEventListener("message", listen);
      signal?.addEventListener("abort", stop, { once: true });
      at.postMessage({ id, wasm, tool, flags: [...flags], input: bytes, files });
    });
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
