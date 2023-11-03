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

The bulk of the Android client code has had the bare minimum changed to be able
to correctly build on the standard Android Studio/gradle build system. Some work
has been done to modernize and replace deprecated APIs and theming. The app
builds for and runs on modern Android devices. Basic kana entry and henkan work.

## What doesn't work/What still needs to be done?

- Lots of visual glitches; henkan works, but completion candidates are not
  rendered correctly.
- Layout/theming looks incorrect. Keyboard key text sizes are either too small
  or too big in some places. Currently unsure of the cause.
- Proper handling of emoji/emoticons. The client code has some weird per-carrier
  pre-unicode standardization code that needs to be gutted and refactored.
- Some preferences cannot be set correctly.

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

## Building the mozc JNI library and dataset

The best way to do this is via the [Docker setup described in the mozc
repo](https://github.com/google/mozc/blob/master/docs/build_mozc_in_docker.md).
Libre Japanese Input depends on the exact revision
`ddd9730b068387631e3b4d212314ef0ed93befe0`. Specific instructions are provided
below.

```shell
# Clone and setup Libre Japanese Input.
git clone https://github.com/elizagamedev/android-libre-japanese-input.git
cd android-libre-japanese-input
git submodule update --init --recursive

# Create Docker image.
cd third_party/mozc/docker/ubuntu22.04
docker build --rm --tag mozc_ubuntu22.04 .
docker create --interactive --tty --name mozc_build mozc_ubuntu22.04

# Build everything.
docker start mozc_build
docker exec -it mozc_build bash
# Check out the exact version of mozc that LJI uses.
git checkout ddd9730b068387631e3b4d212314ef0ed93befe0
git submodule deinit -f .
git submodule update --init --recursive
bazel build package --config oss_android
bazel build //data_manager/oss:mozc_dataset_for_oss --config linux
exit

# Extract artifacts.
cd ../../../../app/src/main
docker cp mozc_build:/home/mozc_builder/work/mozc/src/bazel-bin/android/jni/native_libs.zip .
docker cp mozc_build:/home/mozc_builder/work/mozc/src/bazel-bin/data_manager/oss/mozc.data assets
ln -s jniLibs lib
unzip native_libs.zip
rm -f lib native_libs.zip

# Clean up Docker.
docker stop mozc_build
docker rm mozc_build
docker image rm mozc_ubuntu22.04
```
