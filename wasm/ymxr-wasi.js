// The WASI host ymxr.wasm runs under (doc/tools.md 20): standard input
// from a buffer, standard output and standard error into buffers, the
// arguments, the clocks, random bytes, and a read-only directory of the
// files the caller passes, open as "/". A run instantiates the module,
// runs it to its exit, and returns the exit code with what it wrote. The
// same file serves a Web Worker (ymxr-worker.js) and Node (ymxr-node.mjs):
// it reads no part of either beyond WebAssembly, TextEncoder, crypto and
// performance.

const SUCCESS = 0;
const BADF = 8;
const INVAL = 28;
const ISDIR = 31;
const NOENT = 44;
const NOTDIR = 54;
const ROFS = 69;

const CHARACTER_DEVICE = 2;
const DIRECTORY = 3;
const REGULAR_FILE = 4;

// oflags of path_open
const CREAT = 1;
const DIRECTORY_FLAG = 2;
const TRUNC = 8;

/** A tool's call to proc_exit, which ends the run with its code. */
class Exit {
  constructor(code) {
    this.code = code;
  }
}

/** The files as a tree: a path, "" the root, to a directory's names or a
 *  file's bytes. A key "a/b.ym" makes the directory "a". */
function tree(files) {
  const nodes = new Map([["", { names: new Set() }]]);
  for (const [path, data] of Object.entries(files)) {
    const parts = path.split("/").filter((part) => part !== "" && part !== ".");
    let at = "";
    parts.forEach((part, i) => {
      const next = at === "" ? part : at + "/" + part;
      nodes.get(at).names.add(part);
      if (i === parts.length - 1) {
        nodes.set(next, { data: data instanceof Uint8Array ? data : new Uint8Array(data) });
      } else if (!nodes.has(next)) {
        nodes.set(next, { names: new Set() });
      }
      at = next;
    });
  }
  return nodes;
}

/** The path {@code name} reaches from the directory at {@code from}, ".."
 *  and "." resolved, or null where it climbs past the root. */
function join(from, name) {
  const parts = from === "" ? [] : from.split("/");
  for (const part of name.split("/")) {
    if (part === "" || part === ".") {
      continue;
    }
    if (part === "..") {
      if (parts.length === 0) {
        return null;
      }
      parts.pop();
    } else {
      parts.push(part);
    }
  }
  return parts.join("/");
}

function concat(chunks) {
  const out = new Uint8Array(chunks.reduce((sum, chunk) => sum + chunk.length, 0));
  let at = 0;
  for (const chunk of chunks) {
    out.set(chunk, at);
    at += chunk.length;
  }
  return out;
}

/**
 * Runs the tool {@code tool} of the compiled ymxr.wasm {@code module} once.
 *
 * @param module a WebAssembly.Module of ymxr.wasm
 * @param tool the tool's name, ymxs-to-sndh for one
 * @param flags the tool's arguments, as its executable reads them
 * @param options input, the bytes of standard input; files, an object of
 *     path to bytes a tool reads by name; onReport, called with each chunk
 *     of standard error as the tool writes it
 * @returns the exit code, standard output and standard error
 */
export async function run(module, tool, flags = [], { input = new Uint8Array(0), files = {}, onReport } = {}) {
  const encoder = new TextEncoder();
  const args = ["ymxr", tool, ...flags].map((arg) => encoder.encode(arg + "\0"));
  const nodes = tree(files);
  const output = [];
  const report = [];
  // the open files: 0 to 2 the standard streams, 3 the root, opened as "/"
  const fds = new Map([
    [0, { device: true, data: input, at: 0 }],
    [1, { device: true }],
    [2, { device: true }],
    [3, { path: "" }],
  ]);
  let next = 4;
  let memory;
  const view = () => new DataView(memory.buffer);
  const bytes = () => new Uint8Array(memory.buffer);
  const text = (at, length) => new TextDecoder().decode(bytes().subarray(at, at + length));

  const node = (fd) => {
    const open = fds.get(fd);
    return open && open.path !== undefined ? nodes.get(open.path) : undefined;
  };

  const filestat = (at, kind, size) => {
    const v = view();
    for (let i = 0; i < 64; i += 8) {
      v.setBigUint64(at + i, 0n, true);
    }
    v.setUint8(at + 16, kind);
    v.setBigUint64(at + 24, 1n, true);
    v.setBigUint64(at + 32, BigInt(size), true);
  };

  const statOf = (entry, at) => {
    if (entry.names) {
      filestat(at, DIRECTORY, 0);
    } else {
      filestat(at, REGULAR_FILE, entry.data.length);
    }
  };

  const wasi = {
    args_sizes_get(count, size) {
      view().setUint32(count, args.length, true);
      view().setUint32(size, args.reduce((sum, arg) => sum + arg.length, 0), true);
      return SUCCESS;
    },
    args_get(argv, buffer) {
      let at = buffer;
      args.forEach((arg, i) => {
        view().setUint32(argv + 4 * i, at, true);
        bytes().set(arg, at);
        at += arg.length;
      });
      return SUCCESS;
    },
    environ_sizes_get(count, size) {
      view().setUint32(count, 0, true);
      view().setUint32(size, 0, true);
      return SUCCESS;
    },
    environ_get() {
      return SUCCESS;
    },
    clock_time_get(id, precision, at) {
      const now = id === 0 ? BigInt(Date.now()) * 1000000n : BigInt(Math.round(performance.now() * 1e6));
      view().setBigUint64(at, now, true);
      return SUCCESS;
    },
    random_get(at, length) {
      for (let done = 0; done < length; done += 65536) {
        crypto.getRandomValues(bytes().subarray(at + done, at + Math.min(length, done + 65536)));
      }
      return SUCCESS;
    },
    sched_yield() {
      return SUCCESS;
    },
    proc_exit(code) {
      throw new Exit(code);
    },
    fd_write(fd, iovs, count, written) {
      if (fd !== 1 && fd !== 2) {
        return BADF;
      }
      let total = 0;
      for (let i = 0; i < count; i++) {
        const at = view().getUint32(iovs + 8 * i, true);
        const length = view().getUint32(iovs + 8 * i + 4, true);
        const chunk = bytes().slice(at, at + length);
        (fd === 1 ? output : report).push(chunk);
        if (fd === 2 && onReport) {
          onReport(chunk);
        }
        total += length;
      }
      view().setUint32(written, total, true);
      return SUCCESS;
    },
    fd_read(fd, iovs, count, read) {
      const open = fds.get(fd);
      if (!open || fd === 1 || fd === 2) {
        return BADF;
      }
      if (node(fd)?.names) {
        return ISDIR;
      }
      const data = open.device ? open.data : node(fd).data;
      let total = 0;
      for (let i = 0; i < count && open.at < data.length; i++) {
        const at = view().getUint32(iovs + 8 * i, true);
        const length = view().getUint32(iovs + 8 * i + 4, true);
        const chunk = data.subarray(open.at, Math.min(data.length, open.at + length));
        bytes().set(chunk, at);
        open.at += chunk.length;
        total += chunk.length;
      }
      view().setUint32(read, total, true);
      return SUCCESS;
    },
    fd_close(fd) {
      if (!fds.has(fd)) {
        return BADF;
      }
      if (fd > 3) {
        fds.delete(fd);
      }
      return SUCCESS;
    },
    fd_fdstat_get(fd, at) {
      const open = fds.get(fd);
      if (!open) {
        return BADF;
      }
      const v = view();
      v.setUint8(at, open.device ? CHARACTER_DEVICE : node(fd)?.names ? DIRECTORY : REGULAR_FILE);
      v.setUint16(at + 2, 0, true);
      v.setBigUint64(at + 8, 0xffffffffffffffffn, true);
      v.setBigUint64(at + 16, 0xffffffffffffffffn, true);
      return SUCCESS;
    },
    fd_fdstat_set_flags(fd) {
      return fds.has(fd) ? SUCCESS : BADF;
    },
    fd_filestat_get(fd, at) {
      const open = fds.get(fd);
      if (!open) {
        return BADF;
      }
      if (open.device) {
        filestat(at, CHARACTER_DEVICE, 0);
      } else {
        statOf(node(fd), at);
      }
      return SUCCESS;
    },
    fd_prestat_get(fd, at) {
      if (fd !== 3) {
        return BADF;
      }
      view().setUint8(at, 0);
      view().setUint32(at + 4, 1, true);
      return SUCCESS;
    },
    fd_prestat_dir_name(fd, at, length) {
      if (fd !== 3) {
        return BADF;
      }
      if (length >= 1) {
        bytes()[at] = 0x2f;
      }
      return SUCCESS;
    },
    path_open(fd, dirflags, at, length, oflags, base, inheriting, fdflags, opened) {
      const from = node(fd);
      if (!from?.names) {
        return from ? NOTDIR : BADF;
      }
      const path = join(fds.get(fd).path, text(at, length));
      const entry = path === null ? undefined : nodes.get(path);
      if (!entry) {
        return oflags & CREAT ? ROFS : NOENT;
      }
      if (oflags & TRUNC) {
        return ROFS;
      }
      if (oflags & DIRECTORY_FLAG && !entry.names) {
        return NOTDIR;
      }
      fds.set(next, { path, at: 0 });
      view().setUint32(opened, next, true);
      next += 1;
      return SUCCESS;
    },
    path_filestat_get(fd, flags, at, length, stat) {
      const from = node(fd);
      if (!from?.names) {
        return from ? NOTDIR : BADF;
      }
      const path = join(fds.get(fd).path, text(at, length));
      const entry = path === null ? undefined : nodes.get(path);
      if (!entry) {
        return NOENT;
      }
      statOf(entry, stat);
      return SUCCESS;
    },
    fd_readdir(fd, at, length, cookie, used) {
      const open = fds.get(fd);
      const dir = node(fd);
      if (!dir?.names) {
        return open ? NOTDIR : BADF;
      }
      const names = [".", "..", ...[...dir.names].sort()];
      const chunks = [];
      for (let i = Number(cookie); i < names.length; i++) {
        const name = encoder.encode(names[i]);
        const entry = new Uint8Array(24 + name.length);
        const v = new DataView(entry.buffer);
        const child = i < 2 ? dir : nodes.get(join(open.path, names[i]));
        v.setBigUint64(0, BigInt(i + 1), true);
        v.setBigUint64(8, BigInt(i + 1), true);
        v.setUint32(16, name.length, true);
        v.setUint8(20, child.names ? DIRECTORY : REGULAR_FILE);
        entry.set(name, 24);
        chunks.push(entry);
      }
      const all = concat(chunks);
      const written = Math.min(length, all.length);
      bytes().set(all.subarray(0, written), at);
      view().setUint32(used, written, true);
      return SUCCESS;
    },
    poll_oneoff(subscriptions, events, count, done) {
      // Every stream is ready, so each subscription is an event at once; a
      // clock waits until its time, which the tools reach only through the
      // runtime's timers.
      const v = view();
      for (let i = 0; i < count; i++) {
        const at = subscriptions + 48 * i;
        const kind = v.getUint8(at + 8);
        if (kind === 0) {
          // the timeout in milliseconds, on the clock the subscription names:
          // 0 the time of day, any other the monotonic clock
          const now = v.getUint32(at + 16, true) === 0 ? () => Date.now() : () => performance.now();
          const timeout = Number(v.getBigUint64(at + 24, true)) / 1e6;
          const absolute = (v.getUint16(at + 40, true) & 1) !== 0;
          const until = absolute ? timeout : now() + timeout;
          while (now() < until) {
            // the clock subscription is a sleep, which a run without
            // threads spends here
          }
        }
        const event = events + 32 * i;
        v.setBigUint64(event, v.getBigUint64(at, true), true);
        v.setUint16(event + 8, SUCCESS, true);
        v.setUint8(event + 10, kind);
        v.setBigUint64(event + 16, kind === 1 ? BigInt(Math.max(0, input.length - fds.get(0).at)) : 0n, true);
        v.setUint16(event + 24, 0, true);
      }
      v.setUint32(done, count, true);
      return SUCCESS;
    },
  };

  const instance = await WebAssembly.instantiate(module, { wasi_snapshot_preview1: wasi });
  memory = instance.exports.memory;
  let exitCode = 0;
  try {
    instance.exports._start();
  } catch (thrown) {
    if (!(thrown instanceof Exit)) {
      throw thrown;
    }
    exitCode = thrown.code;
  }
  return { exitCode, output: concat(output), report: concat(report) };
}
