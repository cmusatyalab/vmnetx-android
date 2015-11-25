#!/bin/bash
#
# A script for building VMNetX dependencies for Android
# Based on build.sh from openslide-winbuild
#
# Copyright (c) 2011-2015 Carnegie Mellon University
# All rights reserved.
#
# This script is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation; either version 2 of the License, or (at your option)
# any later version.
#
# This script is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
# more details.
#
# You should have received a copy of the GNU General Public License along
# with this script.  If not, see <http://www.gnu.org/licenses/>.
#

set -eE

platform="android-16"
abis="armeabi-v7a x86"
packages="configguess configsub celt openssl spiceprotocol spicegtk gstreamer_src"

# Package versions
configsub_ver="bf654c7e"
configguess_ver="28d244f1"
celt_ver="0.5.1.3"  # spice-gtk requires 0.5.1.x specifically
openssl_ver="1.0.2d"
spicegtk_ver="0.30"
spiceprotocol_ver="0.12.10"
gstreamer_ver="1.6.1"

# Tarball URLs
configguess_url="http://git.savannah.gnu.org/gitweb/?p=config.git;a=blob_plain;f=config.guess;hb=${configguess_ver}"
configsub_url="http://git.savannah.gnu.org/gitweb/?p=config.git;a=blob_plain;f=config.sub;h=${configsub_ver}"
celt_url="http://downloads.xiph.org/releases/celt/celt-${celt_ver}.tar.gz"
openssl_url="http://www.openssl.org/source/openssl-${openssl_ver}.tar.gz"
spicegtk_url="http://www.spice-space.org/download/gtk/spice-gtk-${spicegtk_ver}.tar.bz2"
spiceprotocol_url="http://www.spice-space.org/download/releases/spice-protocol-${spiceprotocol_ver}.tar.bz2"
gstreamer_armeabi_url="http://gstreamer.freedesktop.org/data/pkg/android/${gstreamer_ver}/gstreamer-1.0-android-arm-${gstreamer_ver}.tar.bz2"
gstreamer_armeabiv7a_url="http://gstreamer.freedesktop.org/data/pkg/android/${gstreamer_ver}/gstreamer-1.0-android-armv7-${gstreamer_ver}.tar.bz2"
gstreamer_x86_url="http://gstreamer.freedesktop.org/data/pkg/android/${gstreamer_ver}/gstreamer-1.0-android-x86-${gstreamer_ver}.tar.bz2"
gstreamer_src_url="http://gstreamer.freedesktop.org/data/pkg/src/${gstreamer_ver}/cerbero-${gstreamer_ver}.tar.bz2"

# Unpacked source trees
celt_build="celt-${celt_ver}"
openssl_build="openssl-${openssl_ver}"
spicegtk_build="spice-gtk-${spicegtk_ver}"
spiceprotocol_build="spice-protocol-${spiceprotocol_ver}"

# Installed libraries
celt_artifacts="libcelt051.a"
openssl_artifacts="libssl.a libcrypto.a"
spicegtk_artifacts="libspice-client-glib-2.0.a"
spiceprotocol_artifacts="spice-protocol/spice.proto"

# Update-checking URLs
celt_upurl="http://downloads.xiph.org/releases/celt/"
openssl_upurl="http://www.openssl.org/source/"
spicegtk_upurl="http://www.spice-space.org/download/gtk/"
spiceprotocol_upurl="http://www.spice-space.org/download/releases/"
gstreamer_upurl="http://gstreamer.freedesktop.org/data/pkg/android/"

# Update-checking regexes
celt_upregex="celt-(0\.5\.1\.[0-9]+)\.tar"
openssl_upregex="openssl-([0-9.]+[a-z]?)\.tar"
spicegtk_upregex="spice-gtk-([0-9.]+)\.tar"
spiceprotocol_upregex="spice-protocol-([0-9.]+)\.tar"
gstreamer_upregex=">([0-9.]+)/<"

expand() {
    # Print the contents of the named variable
    # $1  = the name of the variable to expand
    echo "${!1}"
}

tarpath() {
    # Print the tarball path for the specified package
    # $1  = the name of the program
    case "$1" in
    configguess)
        echo "tar/config.guess"
        ;;
    configsub)
        echo "tar/config.sub"
        ;;
    *)
        echo "tar/$(basename $(expand ${1}_url))"
        ;;
    esac
}

fetch() {
    # Fetch the specified package
    # $1  = package shortname
    local url
    url="$(expand ${1}_url)"
    mkdir -p tar
    if [ ! -e "$(tarpath $1)" ] ; then
        echo "Fetching ${1}..."
        case "$1" in
        configguess|configsub)
            wget -q -O "$(tarpath $1)" "$url"
            ;;
        *)
            wget -P tar -q "$url"
            ;;
        esac
    fi
}

unpack() {
    # Remove the package build directory and re-unpack it
    # $1  = package shortname
    local path
    fetch "${1}"
    mkdir -p "${build}"
    path="${build}/$(expand ${1}_build)"
    echo "Unpacking ${1}..."
    rm -rf "${path}"
    tar xf "$(tarpath $1)" -C "${build}"
}

is_built() {
    # Return true if the specified package is already built
    # $1  = package shortname
    local artifact
    for artifact in $(expand ${1}_artifacts)
    do
        if [ ! -e "${root}/lib/${artifact}" ] ; then
            return 1
        fi
    done
    return 0
}

do_configure() {
    # Run configure with the appropriate parameters.
    # Additional parameters can be specified as arguments.
    #
    # Fedora's ${build_host}-pkg-config clobbers search paths; avoid it
    #
    # Use only our pkg-config library directory, even on cross builds
    # https://bugzilla.redhat.com/show_bug.cgi?id=688171
    ./configure \
            --host=${build_host} \
            --build=${build_system} \
            --prefix="$root" \
            --enable-static \
            --disable-shared \
            --disable-dependency-tracking \
            PKG_CONFIG="pkg-config --static" \
            PKG_CONFIG_LIBDIR="${root}/share/pkgconfig:${root}/lib/pkgconfig:${gst}/lib-fixed/pkgconfig" \
            PKG_CONFIG_PATH= \
            CPPFLAGS="${cppflags} -I${root}/include -I${gst}/include" \
            CFLAGS="${cflags}" \
            CXXFLAGS="${cxxflags}" \
            LDFLAGS="${ldflags} -L${root}/lib -L${gst}/lib-fixed" \
            "$@"
}

build_one() {
    # Build the specified package if not already built
    # $1  = package shortname
    local basedir builddir

    if is_built "$1" ; then
        return
    fi

    unpack "$1"

    echo "Building ${1}..."
    basedir="$(pwd)"
    builddir="${build}/$(expand ${1}_build)"
    pushd "$builddir" >/dev/null
    case "$1" in
    celt)
        cp "${basedir}/$(tarpath configsub)" .
        do_configure \
                --without-ogg
        cd libcelt
        make $parallel
        make install
        cd ..
        mkdir -p "${root}/lib/pkgconfig"
        cp -a celt051.pc "${root}/lib/pkgconfig"
        ;;
    openssl)
        local os
        case "$abi" in
        armeabi)
            os=android
            ;;
        armeabi-v7a)
            os=android-armv7
            ;;
        x86)
            os=android-x86
            ;;
        *)
            echo "Unsupported ABI: $abi"
            exit 1
            ;;
        esac
        ./Configure \
                "${os}" \
                --prefix="$root" \
                --cross-compile-prefix="${build_host}-" \
                no-zlib \
                no-hw \
                no-ssl2 \
                no-ssl3 \
                ${cppflags} \
                ${cflags} \
                ${ldflags}
        make depend
        make
        make install_sw
        ;;
    spiceprotocol)
        autoreconf -fi
        do_configure
        make $parallel
        make install
        ;;
    spicegtk)
        autoreconf -fi
        do_configure \
                --with-gtk=no \
                --enable-dbus=no \
                --enable-controller=no \
                --with-audio=gstreamer \
                LIBS="-lm"
        make $parallel

        # Patch to avoid SIGBUS due to unaligned accesses on ARM7
        patch -p1 < "${basedir}/spice-marshaller-sigbus.patch"
        make $parallel

        make install
        ;;
    esac

    popd >/dev/null
}

sdist() {
    # Build source distribution
    local package tardir
    tardir="vmnetx-android-dependencies"
    rm -rf "${tardir}"
    mkdir -p "${tardir}"
    for package in $packages
    do
        fetch "$package"
        cp "$(tarpath ${package})" "${tardir}"
    done
    rm -f "${tardir}.tar.gz"
    tar czf "${tardir}.tar.gz" "${tardir}"
    rm -r "${tardir}"
}

setup() {
    # Configure the build environment and set up variables
    # $1 = ABI
    local system_arg
    if [ -z "${origpath}" ] ; then
        origpath="$PATH"
    fi

    cppflags=""
    cflags="-O2"
    cxxflags="${cflags}"
    ldflags=""

    abi="${1}"
    case "$abi" in
    armeabi)
        arch=arm
        build_host="arm-linux-androideabi"
        ;;
    armeabi-v7a)
        arch=arm
        build_host="arm-linux-androideabi"
        cflags="${cflags} -march=armv7-a -mfloat-abi=softfp -mfpu=neon"
        ldflags="${ldflags} -march=armv7-a -Wl,--fix-cortex-a8"
        ;;
    arm64-v8a)
        arch=arm64
        build_host="aarch64-linux-android"
        ;;
    mips)
        arch=mips
        build_host="mipsel-linux-android"
        ;;
    mips64)
        arch=mips64
        build_host="mips64el-linux-android"
        ;;
    x86)
        arch=x86
        build_host="i686-linux-android"
        ;;
    x86-64)
        arch=x86_64
        build_host="x86_64-linux-android"
        ;;
    *)
        echo "Unknown ABI: $abi"
        exit 1
    esac

    build="deps/${abi}/build"
    root="$(pwd)/deps/${abi}/root"
    toolchain="$(pwd)/deps/${abi}/toolchain"
    gst="$(pwd)/deps/${abi}/gstreamer"
    mkdir -p "${root}"

    fetch configguess
    build_system=$(sh tar/config.guess)

    # Set up build environment
    if ! [ -e "${toolchain}/bin/${build_host}-gcc" ] ; then
        if [ -z "${ndkdir}" ] ; then
            echo "No toolchain configured and NDK directory not set."
            exit 1
        fi
        if [ $(uname -p) = "x86_64" ] ; then
            system_arg="--system=linux-x86_64"
        fi
        ${ndkdir}/build/tools/make-standalone-toolchain.sh \
                --platform="${platform}" \
                --arch="${arch}" \
                --install-dir="${toolchain}" \
                "${system_arg}"
    fi
    if ! [ -e "${toolchain}/bin/${build_host}-gcc" ] ; then
        echo "Couldn't configure compiler."
        exit 1
    fi
    PATH="${toolchain}/bin:${origpath}"
}

build() {
    # Build binaries
    # $1 = ABI
    local package pkgstr origroot

    # Set up build environment
    setup "$1"
    fetch configsub

    # Unpack GStreamer SDK
    if [ ! -e "${gst}/lib/libglib-2.0.a" ] ; then
        pkgstr="gstreamer_$(echo ${abi} | tr -d -)"
        fetch "${pkgstr}"
        echo "Unpacking ${pkgstr}..."
        rm -rf "${gst}"
        mkdir -p "${gst}"
        tar xf "$(tarpath ${pkgstr})" -C "${gst}"
        # The .la files point to shared libraries that don't exist, so
        # linking fails.  We can't delete the .la files outright because
        # the GStreamer ndk-build glue depends on them.  Create a separate
        # lib directory with no .la files.
        cp -a "${gst}/lib" "${gst}/lib-fixed"
        rm -f ${gst}/lib-fixed/*.la
        # Fix paths in .pc files
        origroot=$(grep '^prefix' "${gst}/lib/pkgconfig/gstreamer-1.0.pc" | \
                sed -e 's/prefix=//')
        sed -i -e "s|${origroot}/lib|${gst}/lib-fixed|g" \
               -e "s|${origroot}|${gst}|g" \
                ${gst}/lib-fixed/pkgconfig/*.pc
        # Add pkg-config file for libjpeg so Android.mk can ask for its
        # symbols to be exposed in the gstreamer .so
        cat > ${gst}/lib/pkgconfig/jpeg.pc <<EOF
prefix=${origroot}
exec_prefix=\${prefix}
libdir=\${prefix}/lib
includedir=\${prefix}/include

Name: jpeg
Description: JPEG library
Version: 8
Libs: -L\${libdir} -ljpeg
Cflags: -I\${includedir}
EOF
        # Drop pkg-config file for opus, since static libopus and static
        # libcelt051 can't be linked into the same binary due to symbol
        # conflicts, and RHEL's libspice-server doesn't link with opus
        rm -f ${gst}/lib-fixed/pkgconfig/opus.pc
    fi

    # Build
    for package in $packages
    do
        build_one "$package"
    done
}

clean() {
    # Clean built files
    local package artifact curabi
    if [ $# -gt 0 ] ; then
        for package in "$@"
        do
            echo "Cleaning ${package}..."
            for curabi in $abis; do
                setup "${curabi}"
                for artifact in $(expand ${package}_artifacts)
                do
                    rm -f "${root}/lib/${artifact}"
                done
            done
        done
    else
        echo "Cleaning..."
        rm -rf deps
    fi
}

updates() {
    # Report new releases of software packages
    local package url curver newver
    for package in $packages gstreamer
    do
        url="$(expand ${package}_upurl)"
        if [ -z "$url" ] ; then
            continue
        fi
        curver="$(expand ${package}_ver)"
        newver=$(wget -q -O- "$url" | \
                sed -nr "s%.*$(expand ${package}_upregex).*%\\1%p" | \
                sort -uV | \
                tail -n 1)
        if [ "${curver}" != "${newver}" ] ; then
            printf "%-15s %10s  => %10s\n" "${package}" "${curver}" "${newver}"
        fi
    done
}

fail_handler() {
    # Report failed command
    echo "Failed: $BASH_COMMAND (line $BASH_LINENO)"
    exit 1
}


# Set up error handling
trap fail_handler ERR

# Parse command-line options
parallel=""
ndkdir=""
while getopts "j:n:" opt
do
    case "$opt" in
    j)
        parallel="-j${OPTARG}"
        ;;
    n)
        ndkdir="${OPTARG}"
        ;;
    esac
done
shift $(( $OPTIND - 1 ))

# Process command-line arguments
case "$1" in
sdist)
    sdist
    ;;
build)
    for curabi in $abis
    do
        build "$curabi"
    done
    ;;
clean)
    shift
    clean "$@"
    ;;
updates)
    updates
    ;;
*)
    cat <<EOF
Usage: $0 sdist
       $0 [-j<parallelism>] [-n<ndk-dir>] build
       $0 clean [package...]
       $0 updates

Packages:
$packages
EOF
    exit 1
    ;;
esac
exit 0
