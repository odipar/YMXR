// The Web Worker ymxr.js runs a tool in (doc/tools.md 20), so a tool's run
// leaves the page's thread free. It compiles ymxr.wasm once, from the URL
// the first message names, and runs one tool a message: each line of
// standard error is a message as the tool writes it, and the run ends with
// a message of the exit code, standard output and standard error.

import { run } from "./ymxr-wasi.js";

let compiled;

async function handle(data, post) {
  const { id, wasm, tool, flags, input, files } = data;
  try {
    compiled ??= data.module ?? WebAssembly.compileStreaming(fetch(wasm));
    const module = await compiled;
    const decoder = new TextDecoder();
    let pending = "";
    const result = await run(module, tool, flags, {
      input,
      files,
      onReport: (chunk) => {
        pending += decoder.decode(chunk, { stream: true });
        let end;
        while ((end = pending.indexOf("\n")) >= 0) {
          post({ id, line: pending.slice(0, end).replace(/\r$/, "") });
          pending = pending.slice(end + 1);
        }
      },
    });
    if (pending !== "") {
      post({ id, line: pending });
    }
    const message = {
      id, exitCode: result.exitCode, output: result.output, report: new TextDecoder().decode(result.report),
    };
    if (tool === "_ymxr-plan-prg" && result.exitCode === 0) message.module = module;
    post(message, [result.output.buffer]);
  } catch (thrown) {
    post({ id, error: String(thrown && thrown.stack ? thrown.stack : thrown) });
  }
}

if (typeof self === "undefined") {
  const { parentPort } = await import("node:worker_threads");
  parentPort.on("message", data => handle(data, (message, transfer) => parentPort.postMessage(message, transfer)));
} else {
  self.onmessage = ({ data }) => handle(data, (message, transfer) => self.postMessage(message, transfer));
}
