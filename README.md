# ShellPilot

ShellPilot is a [Secure Shell](https://en.wikipedia.org/wiki/Secure_Shell)
client for Android with AI CLI tool support. It is a fork of ConnectBot,
modernized with Kotlin, Compose, Room, and ShellPilot-specific package IDs.


## How to Install

### Build locally

ShellPilot currently builds from this source checkout. Release distribution
should use ShellPilot artifacts, not upstream ConnectBot package IDs. There
are two Android flavors:

-  "`google`" &mdash; for a version that uses Google Play Services
to handle upgrading the cryptography provider
-  "`oss`" &mdash; includes the cryptography provider in the APK which
   increases its size by a few megabytes.
## Compiling

### Android Studio

ShellPilot is most easily developed in [Android Studio](
https://developer.android.com/studio/). Open this checkout's `connectbot/`
directory as the Android project root.

### Command line

To compile ShellPilot using `gradlew`, use the Android Studio bundled JBR:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleOssDebug
```

### Continuous Integration

ShellPilot keeps the existing Android CI workflow in `.github/workflows/ci.yml`.

#### Running Workflows Locally with act

In general, simply running `./gradlew build` should cover all the
checks run in the GitHub Actions continuous integration workflow, but you can
run GitHub Actions workflows locally using [`nektos/act`](https://github.com/nektos/act).
This requires Docker to be installed and running.

To run the main CI workflow (`ci.yml`):

```sh
act -W .github/workflows/ci.yml
```


## Translations

Translation files are inherited from the ConnectBot fork point. When changing
ShellPilot-specific branding, update the default strings and brand tokens in
localized resources without rewriting unrelated translations.
