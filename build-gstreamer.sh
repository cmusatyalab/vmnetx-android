#!/bin/bash
#
# Build GStreamer SDK from source, since upstream doesn't release sources
# (in violation of the LGPL)
# https://bugzilla.gnome.org/742830
#
# Copyright (c) 2015 Carnegie Mellon University
# All rights reserved.
#
# This script is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License, version 2, as published
# by the Free Software Foundation.
#
# This script is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
# more details.
#
# You should have received a copy of the GNU General Public License along
# with this script.  If not, see <http://www.gnu.org/licenses/>.
#

set -e

cerbero_url="git://anongit.freedesktop.org/gstreamer/cerbero"
cerbero_tag="1.4.5"
cerbero_arches="armv7 x86"

mkconf() {
    # $1 = architecture
    cat > cerbero.conf <<EOF
$(cat config/cross-android-${1}.cbc)

home_dir = '$(pwd)/build'
local_sources = '$(pwd)/source'

packager = 'Olive Executable Archive <android@olivearchive.org>'
allow_parallel_build = True
variants += ['nodebug']
EOF
}

build_arch() {
    # $1 = architecture
    local suffix
    mkconf "${1}"
    ./cerbero-uninstalled -c cerbero.conf package gstreamer-1.0

    for suffix in -runtime.tar.bz2 -runtime.zip .zip
    do
        rm "gstreamer-1.0-android-${1}-${cerbero_tag}${suffix}"
    done
    mkdir -p ../tar
    mv "gstreamer-1.0-android-${1}-${cerbero_tag}.tar.bz2" \
            "../tar/gstreamer-1.0-android-${1}-release-${cerbero_tag}.tar.bz2"
}

clean_builds() {
    rm -rf build
    git clean -dxfq -e "/source"
}

build() {
    local arch

    # Set up repo
    if [ ! -e cerbero ] ; then
        git clone "${cerbero_url}" cerbero
    fi
    cd cerbero
    git fetch
    git checkout "${cerbero_tag}"
    clean_builds
    mkconf armv7
    ./cerbero-uninstalled -c cerbero.conf bootstrap

    # Build
    for arch in ${cerbero_arches}
    do
        build_arch "${arch}"
    done

    # Create source tarball
    clean_builds
    tar czf ../vmnetx-android-gstreamer-sdk-source.tar.gz -C .. \
            build-gstreamer.sh cerbero
}

clean() {
    rm -rf cerbero
}

case "$1" in
build)
    build
    ;;
clean)
    clean
    ;;
*)
    echo "Usage: $0 {build|clean}"
    exit 1
    ;;
esac
