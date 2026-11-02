# Contributing to Visor Launcher

Thanks for looking. Visor is a plain framework Java Android app: no AndroidX, no Gradle, no INTERNET permission, minSdk 28, targetSdk 34. Please keep it that way. A change that adds a dependency or a permission needs a good reason in the pull request.

## A note on the name

The project was called Deck before it was called Visor. The old name is still inside the code on purpose, so that nobody's install breaks: the package is `com.nokoding.deck`, the classes are `HomeActivity`, `DeckCmd` and so on, the adb extra is `deck_cmd`, the folders on the TV are under `Android/data/com.nokoding.deck/`, and some script and setting names start with `deck`. Do not rename them. What you see on the TV and in the docs is Visor.

## Build and test on your own computer

You need JDK 17 or newer, `zip`, `python3` and `bash` (on Windows use Git Bash or WSL).

1. Android SDK. Download the command line tools from https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip (use the macOS or Windows build from https://developer.android.com/studio#command-line-tools-only if you are not on Linux), unzip so that `sdkmanager` ends up in `$HOME/sdk/cmdline-tools/latest/bin`, then:
   ```
   yes | $HOME/sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/sdk "platforms;android-34" "build-tools;34.0.0"
   ```
2. The org.json jar the tests use (Android's org.json is not on a normal JVM):
   ```
   curl -LO https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar
   ```
3. Tests (about 5,000 checks, they need no device):
   ```
   bash deck/tools/test.sh json-20231013.jar
   ```
   A new pure class goes in the file list inside `deck/tools/test.sh`.
4. A debug build with a key of your own:
   ```
   keytool -genkeypair -keystore my.keystore -alias key -keyalg RSA -keysize 2048 -validity 10000
   echo "your-password" > pw.txt
   bash deck/tools/build.sh $HOME/sdk my.keystore pw.txt out/visor.apk
   ```
   The version code and name are set in the `aapt2 link` line of `deck/tools/build.sh` and in `deck/AndroidManifest.xml`. Bump both together.
   A build signed with your own key will not install over a release. Uninstall first.
5. Lint is optional: run Android lint on the Java files with `SKIP_JDK_VERSION_CHECK=1` and `--config deck/lint.xml`. The project expects 0 errors and the same 16 warnings.

## Rules for changes

- Every new Setup row needs a one sentence help text in `RowHelp` (a test checks this).
- Respect Reduce effects: no motion when it is on.
- Pure logic goes in classes with no Android types and gets tests in `deck/tests`.
- Docs are for people who have never seen the project. Plain words, no promises that were not tested. If something was not tried on a real TV, say so.
- Keep the README, `docs/adb-commands.md` and `deck/tools/setup-all.sh` in step: every command appears in all of them.
- Do not add logos, icons or artwork that you did not make or that are not clearly free to redistribute.

## Releases and signing (for maintainers)

The release workflow (`.github/workflows/release.yml`) builds the APK in GitHub Actions and signs it with a key kept in repository secrets. The key is never in the repository and never in chat.

1. Make a key once, on your own machine: `keytool -genkeypair -keystore visor.keystore -alias visor -keyalg RSA -keysize 2048 -validity 10000`.
   Keep a safe copy. Updates only install over an older build that was signed with the same key.
2. Encode it: `base64 -w0 visor.keystore` (on macOS `base64 -i visor.keystore`).
3. GitHub repository, Settings, Secrets and variables, Actions, New repository secret. Add two secrets:
   - `DECK_KEYSTORE_B64`: the base64 text from step 2.
   - `DECK_KS_PASS`: the keystore password.
   Optional: a repository variable `EXPECTED_CERT_SHA256` with the certificate's SHA-256 digest (lower case hex, no colons). The workflow then refuses to publish if the key does not match.
4. Actions tab, "Release", Run workflow, enter a tag such as `v2.18-beta`. It runs the tests, builds, signs, and publishes a prerelease with the SHA-256 in the notes.

The package name stays `com.nokoding.deck` so that existing installs keep updating.

## Reporting a bug

Open an issue with the bug report form. Say what TV or box, what controller, the Android version, what you pressed, and what you expected. Most features have only been tried on one box, so every report helps.
