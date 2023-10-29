import com.google.protobuf.gradle.*

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.google.protobuf") version "0.9.4"
}

val generatedDir = "$buildDir/libre-japanese-input"
val generatedAssetsDir = "$generatedDir/assets"
val generatedResDir = "$generatedDir/res"
val generatedSrcDir = "$generatedDir/src"
val generatedSvgZipDir = "$generatedDir/svg"

val genEmojiDataScript = "scripts/gen_emoji_data.py"
val genEmoticonDataScript = "scripts/gen_emoticon_data.py"

task<Copy>("copyCredits") {
  from("../third_party/mozc/src/data/installer/credits_en.html")
  into("$generatedAssetsDir/credits_en.html")
}

val svgImageTemplateDir = "scripts/images/template"
val svgImageTransformScript = "$svgImageTemplateDir/transform.py"
val svgImageTemplateOutput = "$generatedSvgZipDir/transformed.zip"

task<Exec>("generateSvgTemplateZip") {
  inputs.files(svgImageTransformScript, svgImageTemplateDir)
  outputs.files(svgImageTemplateOutput)

  commandLine(
    "python",
    svgImageTransformScript,
    "--input_dir=$svgImageTemplateDir",
    "--output_zip=$svgImageTemplateOutput"
  )
}

val svgImageAsIsDir = "scripts/images/svg"
val svgImageAsIsOutput = "$generatedSvgZipDir/asis.zip"

task<Exec>("generateSvgAsIsZip") {
  inputs.files(svgImageAsIsDir)
  outputs.files(svgImageAsIsOutput)

  commandLine(
    "zip",
    "-q",
    "-1",
    "-j",
    "-r",
    svgImageAsIsOutput,
    svgImageAsIsDir,
  )
}

val genMozcDrawableScript = "scripts/gen_mozc_drawable.py"
val genMozcDrawableLog = "$generatedDir/gen_mozc_drawable.log"

task<Exec>("generateMozcDrawable") {
  dependsOn("generateSvgTemplateZip")
  dependsOn("generateSvgAsIsZip")

  inputs.files(genMozcDrawableScript, svgImageTemplateOutput, svgImageAsIsOutput)
  outputs.dirs("$generatedResDir/raw")
  outputs.files(genMozcDrawableLog)

  commandLine(
    "python",
    genMozcDrawableScript,
    "--svg_paths=$svgImageAsIsOutput,$svgImageTemplateOutput",
    "--output_dir=$generatedResDir/raw",
    "--build_log=$genMozcDrawableLog"
  )
}

// TODO: replace with upstream
// val emojiData = "../third_party/mozc/src/data/emoji/emoji_data.tsv"
val emojiData = "scripts/emoji_data.tsv"
val generatedEmojiDataFile = "$generatedSrcDir/sh/eliza/japaneseinput/emoji/EmojiData.java"

task<Exec>("generateEmojiData") {
  inputs.files(genEmojiDataScript, emojiData)
  outputs.files(generatedEmojiDataFile)

  commandLine(
    "python",
    genEmojiDataScript,
    "--emoji_data=$emojiData",
    "--output=$generatedEmojiDataFile"
  )
}

// TODO: replace with upstream
// val emoticonData = "../third_party/mozc/src/data/emoticon/categorized.tsv"
val emoticonData = "scripts/emoticon_categorized.tsv"
val generatedEmoticonDataFile = "$generatedSrcDir/sh/eliza/japaneseinput/EmoticonData.java"

task<Exec>("generateEmoticonData") {
  inputs.files(genEmoticonDataScript, emoticonData)
  outputs.files(generatedEmoticonDataFile)

  commandLine(
    "python",
    genEmoticonDataScript,
    "--input=$emoticonData",
    "--output=$generatedEmoticonDataFile",
    "--class_name=EmoticonData",
    "--value_column=0",
    "--category_column=1",
  )
}

// TODO: replace with upstream
// val symbolData = "../third_party/mozc/src/data/symbol/categorized.tsv"
val symbolData = "scripts/symbol_categorized.tsv"
val generatedSymbolDataFile = "$generatedSrcDir/sh/eliza/japaneseinput/SymbolData.java"

task<Exec>("generateSymbolData") {
  inputs.files(genEmoticonDataScript, emoticonData)
  outputs.files(generatedSymbolDataFile)

  commandLine(
    "python",
    genEmoticonDataScript,
    "--input=$symbolData",
    "--output=$generatedSymbolDataFile",
    "--class_name=SymbolData",
    "--value_column=0",
    "--category_column=1",
  )
}

tasks.preBuild {
  dependsOn("copyCredits")
  dependsOn("generateMozcDrawable")
  dependsOn("generateEmojiData")
  dependsOn("generateEmoticonData")
  dependsOn("generateSymbolData")
}

android {
  namespace = "sh.eliza.japaneseinput"
  compileSdk = 33
  buildToolsVersion = "33.0.2"

  defaultConfig {
    applicationId = "sh.eliza.japaneseinput"
    minSdk = 26
    targetSdk = 33
    versionCode = 100
    versionName = "0.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  sourceSets {
    getByName("main").run {
      assets.srcDirs(generatedAssetsDir)
      java.srcDirs(generatedSrcDir)
      res.srcDirs(generatedResDir)
      proto {
        // TODO: Figure out a way to more cleanly specify a single include directory.
        srcDirs("../third_party/mozc/src")
        exclude("third_party/**/*.proto")
        include("../protocol/*.proto")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions { jvmTarget = "11" }
}

dependencies {
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.preference:preference:1.2.0")
  implementation("com.google.android.material:material:1.9.0")
  implementation("com.google.guava:guava:32.1.3-android")
  implementation("com.google.protobuf:protobuf-javalite:3.8.0")

  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// https://github.com/google/protobuf-gradle-plugin#default-outputs
protobuf { generateProtoTasks { all().forEach { it.builtins { id("java") { option("lite") } } } } }
