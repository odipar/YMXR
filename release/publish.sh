#!/bin/sh
# The standalone YMXR executables: the eleven tools of doc/tools.md, one
# set per platform, each containing the nine 68000 binaries and DTX's
# twenty-two images, so a machine with neither this repository nor a
# toolchain can convert a dump and write a program that plays it.
#
#   release/publish.sh [version]      # the six platforms below
#   TARGETS="linux-x64" release/publish.sh
#   OUT=dir release/publish.sh
#
# They are built from go/, so there are six of them: go build
# cross-compiles to any target from any host, with no toolchain installed
# for that target.
#
# NO JAVA RUNS AT RELEASE TIME, but the Maven build runs first: it
# assembles the nine binaries with rmac and writes them into
# go/binaries/data, which go:embed reads. The Java tree is the reference
# and ParityTest checks the two against one another, byte for byte; a
# release is built from one tree.
#
# A Go executable runs as it stands: a Java tool runs through bin/run,
# which finds the classpath first, and these run with no wrapper.
set -e
cd "$(dirname "$0")/.."
REPO=$(pwd)
OUT=${OUT:-dist}
# A relative OUT counts from the repository, and every use below is the
# resolved one: a build runs from go/, where a relative path counts from
# somewhere else.
case $OUT in /*) ;; *) OUT=$REPO/$OUT ;; esac
TARGETS=${TARGETS:-"win-x64 win-arm64 osx-x64 osx-arm64 linux-x64 linux-arm64"}
TOOLS="ym-to-ymxs ymxs-to-ymxr ymxs-to-sndh ymxs-to-prg \
       ym-to-ymxr ymxr-multi ymxr-bind ymxr-sndh ymxr-prg \
       ymxr-trace ymxr-check"

# The version names the zips. The pom is where it is recorded, and this
# reads the text rather than running anything.
VERSION=${1:-$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' pom.xml | head -1)}
if [ -z "$VERSION" ]; then
    echo "publish: pom.xml does not name a version" >&2
    exit 1
fi

# What go:embed reads: the nine binaries the build assembles. A tree whose
# build has not run has none, and a tool would then have no core to put
# a tune behind.
BINARIES=go/binaries/data
for binary in YMXR_sndh.bin YMXR_sndh-perf.bin YMXR_sndh-lean.bin \
              YMXR_sndh-perf-lean.bin YMXR_prg.bin; do
    if [ ! -f "$BINARIES/$binary" ]; then
        echo "publish: $BINARIES/$binary is not built: run mvn process-classes" >&2
        exit 1
    fi
done

rm -rf "$OUT/release"
mkdir -p "$OUT/release"

for target in $TARGETS; do
    case "$target" in
        win-*)   os=windows; ext=.exe ;;
        osx-*)   os=darwin;  ext= ;;
        linux-*) os=linux;   ext= ;;
        *) echo "publish: $target is not a platform this builds" >&2; exit 1 ;;
    esac
    case "$target" in
        *-x64)   arch=amd64 ;;
        *-arm64) arch=arm64 ;;
        *) echo "publish: $target does not name an architecture" >&2; exit 1 ;;
    esac

    # The directory is where a built tool gets tried out, so the build
    # starts from an empty one.
    rm -rf "$OUT/$target"
    mkdir -p "$OUT/$target"
    for tool in $TOOLS; do
        # CGO off makes the binary static and the cross-build runs; -s -w
        # drop the symbol and debug tables, which no tool here reads.
        (cd go && CGO_ENABLED=0 GOOS=$os GOARCH=$arch \
            go build -ldflags="-s -w" -o "$OUT/$target/$tool$ext" \
            ./cmd/"$tool")
    done
    zip="ymxr-tools-$target-v$VERSION.zip"
    (cd "$OUT/$target" && zip -q -X "../release/$zip" *)
    echo "$OUT/release/$zip: $(wc -c < "$OUT/release/$zip" | tr -d ' ') bytes"
done

# What the release contains, by name, size and hash: release/manifest.sh.
release/manifest.sh "$VERSION" "$OUT/release"

# The host's executables, tried as a user would: from a directory that is
# not this repository, with no other file beside them and an empty
# environment.
# A dump goes in and a TOS program comes out, which is the whole pipeline
# in one run.
case "$(uname -s)-$(uname -m)" in
    Darwin-arm64) host=osx-arm64 ;;
    Darwin-x86_64) host=osx-x64 ;;
    Linux-x86_64) host=linux-x64 ;;
    Linux-aarch64) host=linux-arm64 ;;
    *) host= ;;
esac
if [ -n "$host" ] && [ -d "$OUT/$host" ]; then
    try=$(mktemp -d)
    cp "ym/test/Turrican - world 4-3.ym" "$try/tune.ym"
    (cd "$try" && env -i "$OUT/$host/ym-to-ymxs" -silent < tune.ym \
        | env -i "$OUT/$host/ymxs-to-prg" -silent > TUNE.PRG)
    echo "tried: a dump through two tools from $OUT/$host, outside the repository," \
         "$(wc -c < "$try/TUNE.PRG" | tr -d ' ') bytes of program"
    rm -rf "$try"
fi

echo "$OUT/release is this release: the tools, one zip a platform."
