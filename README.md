# Libre Japanese Input for Android

## What is this?

Libre Japanese Input is a WIP restoration and continuation of the standalone
[Google Japanese Input/mozc](https://github.com/google/mozc) app which has been
discontinued in favor of the monolithic gBoard app which requires an internet
connection. Unfortunately, its most recent, several-year old build has become
increasingly incompatible with recent devices and versions of Android,
motivating the creation of this project.

This project is not affiliated with Google.

## What works/What's been done already?

The Android client code has had the bare minimum changed to be able to correctly
build on the standard Android Studio/gradle build system. The app builds for and
runs on modern Android devices.

## What doesn't work/What still needs to be done?

- Actual text entry/henkan. The native mozc protocol has changed a decent amount
  in the years since the standalone mozc app was discontinued. Rather than opt
  to try to build the old binary, I instead opted to bring the client-side to
  speed with the latest version of the protocol. Unfortunately, that currently
  means a ton of randomly commented-out code marked with `TODO(exv)`. Plus, the
  native mozc server isn't even built and running on the device yet.
- Proper handling of emoji/emoticons. The client code has some weird per-carrier
  pre-unicode standardization code that needs to be gutted and refactored.
- Layout/theming looks incorrect. Keyboard key text sizes are either too small
  or too big in some places. Currently unsure of the cause.

## Development

First, ensure you have initialized all submodules.

```shell
git submodule update --init --recursive
```

The Libre Japanese Input dev environment is provided as a Nix flake and is very
easy to build with [Nix](https://nixos.org/).

```shell
nix develop
./gradlew installDebug
```

## Building the mozc JNI library

The best way to do this is via the [Docker setup described in the mozc
repo](https://github.com/google/mozc/blob/master/docs/build_mozc_in_docker.md).
Libre Japanese Input depends on the exact version 2.29.5160.102.

[TODO: Revise these instructions once Android builds have been fixed
upstream.](https://github.com/google/mozc/issues/840)

```shell
# Create Docker image.
git clone https://github.com/google/mozc.git
cd mozc
# check out known good commit
git checkout 51e0d20285de63d2f0f5007d01c7bf63c0a8dfae
cd docker/ubuntu22.04
docker build --rm --tag mozc_ubuntu22.04 .
docker create --interactive --tty --name mozc_build mozc_ubuntu22.04

# Start and configure Docker build image.
docker start mozc_build
docker exec -it mozc_build bash
git checkout 2.29.5160.102
git submodule deinit -f .
git submodule update --init --recursive
exit

# Build the image for armeabi.
docker exec -it mozc_build bash
bazel build package --config oss_android --fat_apk_cpu=arm64-v8a --android_crosstool_top=@androidndk//:toolchain --cpu=armeabi-v7a
exit
docker cp \
       mozc_build:/home/mozc_builder/work/mozc/src/bazel-bin/android/jni/libmozcjni.so \
       /path/to/libre-japanese-input/app/src/main/jniLibs/armeabi-v7a

# Build the image for aarch64.
docker exec -it mozc_build bash
bazel build package --config oss_android --cpu=arm64-v8a
exit
docker cp \
       mozc_build:/home/mozc_builder/work/mozc/src/bazel-bin/android/jni/libmozcjni.so \
       /path/to/libre-japanese-input/app/src/main/jniLibs/arm64-v8a

# Clean up.
docker stop mozc_build
docker rm mozc_build
docker image rm mozc_ubuntu22.04
```
