// The Web Worker ymxr.js runs a tool in (doc/tools.md 20), so a tool's run
// leaves the page's thread free. It compiles ymxr.wasm once, from the URL
// the first message names, and runs one tool a message: each line of
// standard error is a message as the tool writes it, and the run ends with
// a message of the exit code, standard output and standard error.

import { run } from "./ymxr-wasi.js";

let compiled;

self.onmessage = async ({ data }) => {
  const { id, wasm, tool, flags, input, files } = data;
  try {
    compiled ??= WebAssembly.compileStreaming(fetch(wasm));
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
          self.postMessage({ id, line: pending.slice(0, end).replace(/\r$/, "") });
          pending = pending.slice(end + 1);
        }
      },
    });
    if (pending !== "") {
      self.postMessage({ id, line: pending });
    }
    self.postMessage(
      { id, exitCode: result.exitCode, output: result.output, report: new TextDecoder().decode(result.report) },
      [result.output.buffer],
    );
  } catch (thrown) {
    self.postMessage({ id, error: String(thrown && thrown.stack ? thrown.stack : thrown) });
  }
};
