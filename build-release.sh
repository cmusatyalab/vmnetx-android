#!/bin/bash
#
# Build release of VMNetX for Android
#
# Copyright (c) 2015 Carnegie Mellon University
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

set -e

parallel=-j8

if [ -z "$3" ] ; then
    echo "Usage: $0 <version> <sdk-path> <keystore-path>" >&2
    exit 1
fi
version="$1"
sdk="$2"
keystore="$3"

ndk="$sdk/ndk-bundle"

# Validate
if [ ! -e "${sdk}/build-tools" ] ; then
    echo "Invalid SDK path."
    exit 1
fi
zipalign=$(echo ${sdk}/build-tools/*/zipalign | awk '{print $1}')
if [ ! -x "${zipalign}" ] ; then
    echo "Couldn't locate zipalign."
    exit 1
fi
if [ ! -e "${ndk}/ndk-build" ] ; then
    echo "Invalid NDK path."
    exit 1
fi
if [ "${keystore}" = "${keystore%.keystore}" -o ! -e "${keystore}" ] ; then
    echo "Invalid keystore path."
    exit 1
fi

# Export Git to source tarball
git archive --format tar "v${version}" "--prefix=vmnetx-android-${version}/" \
        -o "vmnetx-android-${version}.tar"
xz -9f "vmnetx-android-${version}.tar"

# Clean and rebuild binary dependencies
./build-deps.sh clean
./build-deps.sh sdist
mv vmnetx-android-dependencies.tar.gz \
        "vmnetx-android-${version}-dependencies.tar.gz"
./build-deps.sh $parallel -n "${ndk}" build

# NDK build
( cd app/src/main && "${ndk}/ndk-build" clean )
( cd app/src/main && NDK_HOST_SED=sed "${ndk}/ndk-build" )
touch deps/built

# Java build
APK="app/build/outputs/apk/app-release-unsigned.apk"
cat <<EOF

Refresh Android Studio, use "Build" -> "Build APK" to build an
unsigned APK, then hit Enter.
EOF
while true
do
    #read
    sleep 10
    if [ -e "$APK" -a "$APK" -nt "deps/built" ] ; then
        break
    fi
    echo "APK not found.  Try again."
done

# Sign
cp "$APK" VMNetX.apk
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
        -keystore "${keystore}" VMNetX.apk vmnetx
"${zipalign}" -v 4 VMNetX.apk "vmnetx-${version}.apk"
rm VMNetX.apk

echo -e "\nOK."
