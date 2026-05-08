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

初回のストア配布前に、現在の `versionCode` より小さい値を生成するタグを使わないでください。ShellPilot は ConnectBot 由来の Git 履歴を保持しているため、`git describe` ベースの versionCode が既存タグに影響されます。ストアへ一度アップロードした後は、以降のタグ・ビルドが必ず単調増加することを確認します。

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
- `targetSdk` が Google Play の最新要件を満たしていること
- `TerminalManager` の foreground service type が申告用途と一致していること
- Room schema export が最新 version までコミット済みであること
- `full_backup_content.xml` / `data_extraction_rules.xml` が DB と保存パスワードを OS 標準 backup から除外していること
- 秘密鍵バックアップは、ユーザーが全体設定と鍵ごとの対象設定を有効にした場合だけ含まれること
- `review/`、`.claude/`、logcat、hprof、mapping、署名鍵が commit に混ざっていないこと

## 5. GitHub Releases

GitHub Releases には必要な配布物だけをアップロードします。

- APK または AAB
- CHANGELOG / リリースノート
- 必要に応じて署名済み checksum

review 用キャプチャや ImageGen モックは、通常のリリース成果物には含めません。
