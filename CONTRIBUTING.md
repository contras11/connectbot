# How to contribute

ShellPilot is maintained as a fork of ConnectBot. Contributions should keep
the ShellPilot app name, `io.shellpilot.app` package namespace, and existing
SSH/core compatibility intact.

## Getting started

* Make sure you have a [GitHub account](https://github.com/signup/free)
* Open an issue in the ShellPilot repository if one doesn't already exist.
* Fork the repository on GitHub and then clone your fork.
* `connectbot/` ディレクトリを Android Studio project root として開いてください。
* Try to build for the first time:
  * `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleOssDebug`
* Run the tests:
  * `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testOssDebugUnitTest`

## Making changes

* Create a topic branch from where you want to base your work.
  * active な ShellPilot branch または repository の default branch をベースにしてください。
  * topic branch を作成する例:
    * `git checkout -b my_fix`
  * 論理的な単位で commit してください。
  * commit message は日本語で、変更理由が分かる本文を付けてください。
  * 基本形は次のとおりです。
````
    短い変更概要

    なぜ変更したか、何を守ったか、どの検証を通したかを簡潔に書く。

    動作確認: :app:assembleOssDebug / :app:testOssDebugUnitTest
````
  * Make sure you have added necessary tests to your changes.
  * Room database version を変更した場合は、`app/schemas/` の generated schema と migration test を同じ変更に含めます。
  * Android backup または device transfer の挙動を変更した場合は、`app/src/main/res/xml/full_backup_content.xml`、`app/src/main/res/xml/data_extraction_rules.xml`、`docs/SECURITY.md` を更新します。
  * Check for unnecessary whitespace:
    * `git diff --check`
  * Make sure no new Android lint issues pop up:
    * `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:lintOssDebug`
    * Read the output to see if any of your newly-added or changed lines have lint errors.
  * Make sure all the checks and tests pass:
    * `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testOssDebugUnitTest :app:assembleOssDebug :app:lintOssDebug`
  * DB、SSH、認証、backup / restore、UI navigation の変更では、次も実行します。
    * `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew connectedOssDebugAndroidTest`

## Worktree hygiene

ローカルレビュー資産、認証情報、端末固有ファイルはコミットしません。

* `.claude/`
* `review/`
* `local.properties`
* release signing keys and keystore properties
* APK/AAB outputs, logcat files, hprof files, and temporary archives

変更に含まれる場合は、次の generated / policy file はコミット対象です。

* Room schema exports in `app/schemas/`
* backup / data extraction XML in `app/src/main/res/xml/`
* tests under `app/src/test/` and `app/src/androidTest/`

詳細なルールは `docs/WORKTREE_HYGIENE.md` を参照してください。

## Submitting changes

* Push your changes to a topic branch in your fork of the repository.
* Start a pull request for ShellPilot.

## Additional resources

* Preserve ConnectBot attribution where required by license headers, dependency names, and legacy migration docs.
