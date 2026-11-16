#!/bin/sh
# Copies the shared test vectors from the iOS repository's Spec folder (../Hearth/Spec, the one place they are
# edited) into the test resources here: the vectors and wire examples for the domain's tests, the wire examples
# for the data layer's. Run it after changing anything in Spec; SpecCopyTests fails until the copies match.
set -eu

here=$(cd "$(dirname "$0")/.." && pwd)
spec=${1:-"$here/../Hearth/Spec"}
if [ ! -d "$spec/vectors" ] || [ ! -d "$spec/wire" ]; then
    echo "No Spec folder at $spec" >&2
    exit 1
fi

copy() {
    rm -rf "$2"
    mkdir -p "$2"
    cp "$1"/*.json "$2"/
}

copy "$spec/vectors" "$here/domain/src/test/resources/spec/vectors"
copy "$spec/wire" "$here/domain/src/test/resources/spec/wire"
copy "$spec/wire" "$here/data/src/test/resources/spec/wire"
echo "Copied the Spec folder from $spec"
