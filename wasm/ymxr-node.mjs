#!/usr/bin/env node
// A tool of ymxr.wasm run under Node, as its executable runs (doc/tools.md
// 20):
//
//   node ymxr-node.mjs ymxs-to-sndh -silent < tune.json > tune.sndh
//
// Standard input, standard output, standard error and the exit code are
// the process's. An argument that names a file or a directory puts it in
// the directory the tool reads files from, a directory with the files
// directly under it, so ymxr-multi and ymxr-check read what they name;
// those read no standard input then, and neither does the runner.
// ymxr.wasm is read beside this file, or from YMXR_WASM where that is set.

import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { run } from "./ymxr-wasi.js";

const [tool, ...flags] = process.argv.slice(2);
const files = {};
let named = false;
for (const arg of flags) {
  if (arg.startsWith("-")) {
    continue;
  }
  named = true;
  try {
    if (statSync(arg).isDirectory()) {
      for (const name of readdirSync(arg)) {
        const path = join(arg, name);
        if (statSync(path).isFile()) {
          files[path] = readFileSync(path);
        }
      }
    } else {
      files[arg] = readFileSync(arg);
    }
  } catch {
    // a name the host lacks is one the tool reports as absent
  }
}
/** Standard input whole, read as a stream: a pipe may be one a read finds
 *  empty for now, where a single read fails. */
async function standardInput() {
  if (process.stdin.isTTY) {
    return new Uint8Array(0);
  }
  const chunks = [];
  for await (const chunk of process.stdin) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

const wasm = process.env.YMXR_WASM ?? new URL("./ymxr.wasm", import.meta.url);
const module = await WebAssembly.compile(readFileSync(wasm));
const result = await run(module, tool ?? "", flags, {
  input: named ? new Uint8Array(0) : await standardInput(),
  files,
  onReport: (chunk) => process.stderr.write(chunk),
});
process.stdout.write(result.output);
process.exitCode = result.exitCode;
