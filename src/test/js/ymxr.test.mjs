// wasm/ymxr.js as a page runs it, under Node (doc/tools.md 20.6). The
// three scripts and the module go into a directory of their own, as a page
// serves them, and the Web Worker ymxr.js starts is a thread of
// worker_threads with the self, postMessage and fetch a page's worker has.
// WasmTest runs this with YMXR_WASM naming the module it built:
//
//   YMXR_WASM=ymxr.wasm node --test src/test/js/ymxr.test.mjs

import assert from "node:assert/strict";
import { copyFile, mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { pathToFileURL } from "node:url";
import { Worker as Thread } from "node:worker_threads";

const served = await mkdtemp(join(tmpdir(), "ymxr-page-"));
for (const script of ["ymxr.js", "ymxr-worker.js", "ymxr-wasi.js"]) {
  await copyFile(join("wasm", script), join(served, script));
}
await copyFile(process.env.YMXR_WASM ?? "wasm/ymxr.wasm", join(served, "ymxr.wasm"));

// What a page's worker reads its messages from and posts them through. A
// message posted before the worker's script has run waits for it, as a
// browser queues it.
const prelude = join(served, "worker-prelude.mjs");
await writeFile(prelude, `
import { parentPort, workerData } from "node:worker_threads";
import { readFile } from "node:fs/promises";
globalThis.self = globalThis;
globalThis.postMessage = (message, transfer) => parentPort.postMessage(message, transfer);
globalThis.fetch = async (url) => new Response(await readFile(new URL(url)),
  { headers: { "content-type": "application/wasm" } });
const early = [];
parentPort.on("message", (data) => (self.onmessage ? self.onmessage({ data }) : early.push(data)));
await import(workerData.script);
for (const data of early) self.onmessage({ data });
`);

// The Web Worker ymxr.js starts, on a thread.
globalThis.Worker = class {
  constructor(script) {
    this.thread = new Thread(pathToFileURL(prelude), { workerData: { script: String(script) } });
    // the page's worker lives as long as the page: here the tests end the process
    this.thread.unref();
    this.listening = new Map();
  }
  postMessage(message, transfer) {
    this.thread.postMessage(message, transfer);
  }
  addEventListener(type, listener) {
    const heard = (data) => listener({ data });
    this.listening.set(listener, heard);
    this.thread.on(type, heard);
  }
  removeEventListener(type, listener) {
    this.thread.off(type, this.listening.get(listener));
    this.listening.delete(listener);
  }
  terminate() {
    this.thread.terminate();
  }
};

const page = await import(pathToFileURL(join(served, "ymxr.js")));
const wasi = await import(pathToFileURL(join(served, "ymxr-wasi.js")));
const module = await WebAssembly.compile(await readFile(join(served, "ymxr.wasm")));

const dump = await readFile("ym/test/DBA 2.ym");
const structure = (await page.run("ym-to-ymxs", ["-silent"], dump)).output;

/** A report without its progress lines, which the clock spaces
 *  (WasmTest). */
const steady = (report) =>
  report.split("\n").filter((line) => !/^ {2}(packing the columns|read) \d+ of \d+ \(\d+%\)$/.test(line)).join("\n");

/** The run in this thread, through the WASI functions alone: what the
 *  worker's run is read against. */
async function direct(tool, flags, input) {
  const ran = await wasi.run(module, tool, flags, { input });
  return { exitCode: ran.exitCode, output: ran.output, report: new TextDecoder().decode(ran.report) };
}

test("a run writes the bytes, the report and the exit code of the run in this thread", async () => {
  const lines = [];
  const ran = await page.run("ymxs-to-sndh", [], structure, { onReport: (line) => lines.push(line) });
  const expected = await direct("ymxs-to-sndh", [], structure);
  assert.equal(ran.exitCode, 0);
  assert.deepEqual(ran.output, expected.output);
  assert.equal(steady(ran.report), steady(expected.report));
  assert.equal(lines.join("\n") + "\n", ran.report, "each line of standard error, as it is written");
});

test("runs end in the order they are asked for", async () => {
  const ended = [];
  await Promise.all([
    page.run("ymxs-to-sndh", ["-silent"], structure).then(() => ended.push("sndh")),
    page.run("ymxs-to-prg", ["-silent"], structure).then(() => ended.push("prg")),
    page.run("ymxr-check", [], dump).then(() => ended.push("check")),
  ]);
  assert.deepEqual(ended, ["sndh", "prg", "check"]);
});

test("runTool reads an array of byte values and calls back with each line", async () => {
  const lines = [];
  const ran = await page.runTool("ymxs-check", [], [...structure], (line) => lines.push(line));
  assert.equal(ran.exitCode, 0);
  assert.deepEqual(ran.output, structure, "ymxs-check passes the structure on");
  assert.match(lines.at(-1), /every rule of SPEC\.md 6 is satisfied/);
});

test("a wrong call exits 2 as the executable does", async () => {
  const ran = await page.run("ymxs-to-sndh", ["-z"], structure);
  assert.equal(ran.exitCode, 2);
  assert.equal(ran.output.length, 0);
});

test("stop() ends the run in progress, and the run after it starts another worker", async () => {
  const stopped = page.run("ymxs-to-sndh", ["-copies"], structure);
  // the run starts once the queue reaches it, a turn of the event loop on
  await new Promise((resolve) => setTimeout(resolve, 0));
  page.stop();
  await assert.rejects(stopped, { name: "AbortError" });
  const next = await page.run("ymxs-to-sndh", ["-silent"], structure);
  assert.equal(next.exitCode, 0);
  assert.deepEqual(next.output, (await direct("ymxs-to-sndh", ["-silent"], structure)).output);
});

test("a signal stops a run, and one aborted already stops it before it starts", async () => {
  const controller = new AbortController();
  const stopped = page.run("ymxs-to-sndh", ["-copies"], structure, { signal: controller.signal });
  await new Promise((resolve) => setTimeout(resolve, 0));
  controller.abort();
  await assert.rejects(stopped, { name: "AbortError" });
  await assert.rejects(page.run("ymxs-to-sndh", [], structure, { signal: AbortSignal.abort() }),
    { name: "AbortError" });
  assert.equal((await page.run("ymxs-to-prg", ["-silent"], structure)).exitCode, 0);
});

test("stop() with no run in progress leaves the next run as it is", async () => {
  page.stop();
  assert.equal((await page.run("ymxs-to-prg", ["-silent"], structure)).exitCode, 0);
});
