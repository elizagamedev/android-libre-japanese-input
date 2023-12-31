# This flake is based on the lovely example here:
# https://github.com/fcitx5-android/fcitx5-android/blob/master/flake.nix
{
  description = "Dev shell flake for libre-japanese-input";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  inputs.flake-compat = {
    url = "github:edolstra/flake-compat";
    flake = false;
  };

  outputs = { self, nixpkgs, ... }:
    let
      pkgs = import nixpkgs {
        system = "x86_64-linux";
        config.android_sdk.accept_license = true;
        config.allowUnfree = true;
        overlays = [ self.overlays.default ];
      };
    in
    with pkgs;
    with project-android-sdk;
    {

      devShells.x86_64-linux.default =
        let
          build-tools = "${androidComposition.androidsdk}/libexec/android-sdk/build-tools/${buildToolsVersion}";
        in
        mkShell {
          buildInputs = [
            androidComposition.androidsdk
            androidStudioPackages.beta
            google-java-format
            kotlin-language-server
            protobuf
            python3
            zip
          ];
          ANDROID_SDK_ROOT =
            "${androidComposition.androidsdk}/libexec/android-sdk";
          GRADLE_OPTS =
            "-Dorg.gradle.project.android.aapt2FromMavenOverride=${build-tools}/aapt2";
          JAVA_HOME = "${jdk17}";
          shellHook = ''
            echo sdk.dir=$ANDROID_SDK_ROOT > local.properties
            export PATH="$PATH:${build-tools}"
          '';
        };
    } // {
      overlays.default = final: prev: {
        project-android-sdk = rec {
          buildToolsVersion = "34.0.0";
          androidComposition = prev.androidenv.composeAndroidPackages {
            platformToolsVersion = "34.0.4";
            buildToolsVersions = [ buildToolsVersion ];
            platformVersions = [ "34" ];
            abiVersions = [ "arm64-v8a" "armeabi-v7a" ];
            includeNDK = false;
            includeEmulator = false;
            useGoogleAPIs = false;
          };
        };
      };
    };
}
