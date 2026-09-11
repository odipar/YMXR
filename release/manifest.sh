#!/bin/sh
# MANIFEST.txt for a release directory: what the release contains, by name,
# size and sha256, so a caller can tell one release's file from another's
# without opening it.
#
#   release/manifest.sh VERSION DIR
#   COMMIT=e39c110 release/manifest.sh VERSION DIR
#
# The zips are listed by what each contains. The source commit is HEAD
# unless COMMIT names the commit DIR was built from.
set -e
VERSION=$1
DIR=$2
if [ -z "$VERSION" ] || [ ! -d "$DIR" ]; then
    echo "manifest: release/manifest.sh VERSION DIR" >&2
    exit 1
fi
COMMIT=${COMMIT:-$(git rev-parse --short HEAD 2>/dev/null || echo unknown)}

sha() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d' ' -f1
    else
        shasum -a 256 "$1" | cut -d' ' -f1
    fi
}
size() {
    wc -c < "$1" | tr -d ' '
}

MANIFEST="$DIR/MANIFEST.txt"
{
    echo "YMXR tools - release $VERSION"
    echo "source commit $COMMIT"
    echo "doc/SPEC.md is the format, doc/BINARIES.md the files a tool"
    echo "writes, and doc/tools.md the tools"
    echo
    echo "the zips"
    echo "name  bytes  sha256  contents"
    for zip in "$DIR"/ymxr-tools-*.zip; do
        name=$(basename "$zip")
        platform=$(echo "$name" | sed "s/^ymxr-tools-//; s/-v$VERSION\.zip$//")
        echo "$name  $(size "$zip")  $(sha "$zip")  the tools for $platform"
    done
} > "$MANIFEST"
echo "$MANIFEST: $(grep -c . "$MANIFEST") lines"
