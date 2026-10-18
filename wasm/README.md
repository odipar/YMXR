# The YMXR tools as WebAssembly

The twelve tools of YMXR as one WebAssembly module, `ymxr.wasm`, and the
scripts that run it in a web page and under Node. A run is the tool's
executable run once: its flags, standard input in, standard output and
the exit code out, and standard error a line at a time while it runs.
[doc/tools.md](../doc/tools.md) defines the tools, and its section 20.5
the module.

| file | what it is |
|---|---|
| `ymxr.wasm` | the twelve tools, a WASI command module: the first argument is the tool, the rest its flags |
| `ymxr.js` | the module's interface in a page: `run`, which runs a tool in a Web Worker |
| `ymxr-worker.js` | the Web Worker `ymxr.js` starts |
| `ymxr-wasi.js` | the WASI functions the module imports, for the worker and for Node |
| `ymxr-node.mjs` | a tool run under Node, as its executable runs |

## In a page

Serve the five files as one folder beside the page: a module script and
a worker load over HTTP, and a page opened from disk loads neither. Then:

```js
import { run } from "./ymxr/ymxr.js";

const json = new TextEncoder().encode(structure);   // a YMXS structure
const { exitCode, output, report } = await run("ymxs-to-sndh", [], json, {
  // standard error, a line at a time
  onReport: (line) => console.log(line),
});
// exitCode 0: output is the SNDH file; 1: the structure was rejected,
// and the last line of report says why; 2: the call is wrong
```

`run(tool, flags, input, options)` returns a `Promise` of `{ exitCode,
output, report }`: `output` the bytes of standard output, `report`
standard error whole. `input` is a `Uint8Array` or a string. The options
are `onReport`, called with each line of standard error as the tool
writes it, packing progress among them; `files`, an object of path to
bytes, for the two tools that read files by name; and `signal`, an
`AbortSignal` that stops the run and rejects its `Promise` with an
`AbortError`.

Runs go to one worker, one at a time in the order they are asked for,
and the worker compiles `ymxr.wasm` once. A run blocks the worker and
leaves the page's thread free, so a page stays responsive while a tool
packs.

The calls a page makes to write a file of a structure:

| file | call |
|---|---|
| SNDH | `run("ymxs-to-sndh", flags, json)` |
| TOS program | `run("ymxs-to-prg", flags, json)` |
| tune file, or multi file | `run("ymxs-to-ymxr", flags, json)` |

The flags are those of the executables: `-kK`, `-mN`, `-copies[S]`,
`-tTITLE`, `-cCOMPOSER`, `-perf`, `-lean`, `-pcrel`, `-abs`, `-vbl`, and
`-rROWS` for a program. A page that checks a structure before writing it
reads the converter's warnings: `ymxs-to-sndh` and `ymxs-to-prg` run
YMXS's check and report each finding as a warning line.

## From .NET in the browser

`runTool(tool, flags, input, onReport)` is `run` with the lines of
standard error as a fourth argument rather than an option, and `input`
may be an array of byte values as well as a `Uint8Array`. An application
on .NET WebAssembly imports `ymxr.js` with `JSHost.ImportAsync`, declares
`runTool` with `[JSImport]`, and reads `exitCode`, `output` and `report`
as the properties of the object the `Promise` resolves to.

An application reads the exit code, the output and each line of standard
error from the executable's process, so code that runs the executable
runs the module with the same reading of the three.

## Under Node

```sh
node ymxr-node.mjs ymxs-to-sndh -silent < tune.json > tune.sndh
```

Standard input, standard output, standard error and the exit code are
the process's. `YMXR_WASM` names a module other than the one beside the
script.

## What the module needs

The module is a WASI preview 1 command module, its first argument the
tool. It imports 21 WASI functions, which `ymxr-wasi.js` supplies with no
dependency beyond WebAssembly, `TextEncoder`, `crypto` and `performance`.

The tools write the same bytes, the same report and the same exit code
as the executables of the same release, which `WasmTest` reads the
module against.
