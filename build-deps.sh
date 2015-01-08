#!/bin/bash
#
# A script for building VMNetX dependencies for Android
# Based on build.sh from openslide-winbuild
#
# Copyright (c) 2011-2015 Carnegie Mellon University
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

set -eE

platform="android-16"
abis="armeabi-v7a x86"
packages="configguess configsub jpeg celt openssl"

# Package versions
configsub_ver="bf654c7e"
configguess_ver="28d244f1"
jpeg_ver="1.4.0"
celt_ver="0.5.1.3"  # spice-gtk requires 0.5.1.x specifically
openssl_ver="1.0.1k"
gstreamer_ver="1.4.5"

# Tarball URLs
configguess_url="http://git.savannah.gnu.org/gitweb/?p=config.git;a=blob_plain;f=config.guess;hb=${configguess_ver}"
configsub_url="http://git.savannah.gnu.org/gitweb/?p=config.git;a=blob_plain;f=config.sub;h=${configsub_ver}"
jpeg_url="http://prdownloads.sourceforge.net/libjpeg-turbo/libjpeg-turbo-${jpeg_ver}.tar.gz"
celt_url="http://downloads.xiph.org/releases/celt/celt-${celt_ver}.tar.gz"
openssl_url="http://www.openssl.org/source/openssl-${openssl_ver}.tar.gz"
gstreamer_armeabi_url="http://gstreamer.freedesktop.org/data/pkg/android/${gstreamer_ver}/gstreamer-1.0-android-arm-release-${gstreamer_ver}.tar.bz2"
gstreamer_armeabiv7a_url="http://gstreamer.freedesktop.org/data/pkg/android/${gstreamer_ver}/gstreamer-1.0-android-armv7-release-${gstreamer_ver}.tar.bz2"
gstreamer_x86_url="http://gstreamer.freedesktop.org/data/pkg/android/${gstreamer_ver}/gstreamer-1.0-android-x86-release-${gstreamer_ver}.tar.bz2"

# Unpacked source trees
jpeg_build="libjpeg-turbo-${jpeg_ver}"
celt_build="celt-${celt_ver}"
openssl_build="openssl-${openssl_ver}"

# Installed libraries
jpeg_artifacts="libjpeg.a"
celt_artifacts="libcelt051.a"
openssl_artifacts="libssl.a libcrypto.a"

# Update-checking URLs
jpeg_upurl="http://sourceforge.net/projects/libjpeg-turbo/files/"
celt_upurl="http://downloads.xiph.org/releases/celt/"
openssl_upurl="http://www.openssl.org/source/"
gstreamer_upurl="http://gstreamer.freedesktop.org/data/pkg/android/"

# Update-checking regexes
jpeg_upregex="files/([0-9.]+)/"
celt_upregex="celt-(0\.5\.1\.[0-9]+)\.tar"
openssl_upregex="openssl-([0-9.]+[a-z]?)\.tar"
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
            PKG_CONFIG=pkg-config \
            PKG_CONFIG_LIBDIR="${root}/lib/pkgconfig" \
            PKG_CONFIG_PATH= \
            CPPFLAGS="${cppflags} -I${root}/include" \
            CFLAGS="${cflags}" \
            CXXFLAGS="${cxxflags}" \
            LDFLAGS="${ldflags} -L${root}/lib" \
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
    jpeg)
        cp "${basedir}/$(tarpath configsub)" .
        do_configure \
                --without-turbojpeg
        make $parallel
        make install
        ;;
    celt)
        cp "${basedir}/$(tarpath configsub)" .
        do_configure \
                --without-ogg
        cd libcelt
        make $parallel
        make install
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
    esac

    popd >/dev/null
}

sdist() {
    # Build source distribution
    local package zipdir
    zipdir="vmnetx-android-dependencies"
    rm -rf "${zipdir}"
    mkdir -p "${zipdir}"
    for package in $packages
    do
        fetch "$package"
        cp "$(tarpath ${package})" "${zipdir}"
    done
    rm -f "${zipdir}.zip"
    zip -r "${zipdir}.zip" "${zipdir}"
    rm -r "${zipdir}"
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
    local package curabi pkgstr gstpath

    # Set up build environment
    setup "$1"
    fetch configsub

    # Unpack GStreamer SDK
    for curabi in $abis
    do
        gstpath="deps/${curabi}/gstreamer"
        if [ ! -e "${gstpath}/lib/libglib-2.0.a" ] ; then
            pkgstr="gstreamer_$(echo ${curabi} | tr -d -)"
            fetch "${pkgstr}"
            echo "Unpacking ${pkgstr}..."
            rm -rf "${gstpath}"
            mkdir -p "${gstpath}"
            tar xf "$(tarpath ${pkgstr})" -C "${gstpath}"
        fi
    done

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
