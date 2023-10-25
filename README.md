# neomozc

## What is this?

neomozc is a WIP restoration and continuation of the standalone [Google Japanese
Input/mozc](https://github.com/google/mozc) app which has been discontinued in
favor of the monolithic, data-harvesting gBoard app. Unfortunately, its most
recent, several-year old build has become increasingly incompatible with recent
devices and versions of Android, motivating the creation of this project.

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

The neomozc dev environment is provided as a Nix flake and is very easy to build
with [Nix](https://nixos.org/).

``` shell
nix develop
./gradlew installDebug
```

TODO: Add build instructions for the native mozc Android library.
