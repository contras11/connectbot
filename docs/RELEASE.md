# Release Procedure

ShellPilot のリリース作業では、署名鍵と認証情報を Git に含めないことを最優先にします。

## 1. 事前確認

```bash
cd /Users/contras11/codes/SSH_App/connectbot
git status -sb
git diff --check
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testOssDebugUnitTest
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleOssDebug :app:lintOssDebug
```

DB、SSH、認証、backup / restore に変更がある場合は、既存 AVD `OshiCue_Medium_Phone_API_36_1` で計測テストも実行します。

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew connectedOssDebugAndroidTest
```

## 2. 署名設定

release signing は Gradle property から読み込みます。

```properties
keystoreFile=/absolute/path/to/release.keystore
keystorePassword=...
keystoreAlias=...
```

`*.jks`、`*.keystore`、`keystore.properties`、`signing.properties` は `.gitignore` 対象です。リポジトリには入れません。

## 3. ビルド

OSS flavor:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleOssRelease :app:bundleOssRelease
```

Google flavor:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleGoogleRelease :app:bundleGoogleRelease
```

## 4. 成果物確認

- APK / AAB の applicationId が `io.shellpilot.app` であること
- Debug applicationId `io.shellpilot.app.debug` の成果物をリリースしないこと
- Room schema export が最新 version までコミット済みであること
- `full_backup_content.xml` / `data_extraction_rules.xml` が DB と保存パスワードを OS 標準 backup から除外していること
- `review/`、`.claude/`、logcat、hprof、mapping、署名鍵が commit に混ざっていないこと

## 5. GitHub Releases

GitHub Releases には必要な配布物だけをアップロードします。

- APK または AAB
- CHANGELOG / リリースノート
- 必要に応じて署名済み checksum

review 用キャプチャや ImageGen モックは、通常のリリース成果物には含めません。
