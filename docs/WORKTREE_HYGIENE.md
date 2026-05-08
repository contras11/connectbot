# Worktree Hygiene

この文書は、ShellPilot の作業ツリーで「Git に入れるもの」と「ローカルに残すもの」を分けるための運用ルールです。

## Git に入れるもの

- アプリ本体: `app/src/main/`
- 単体テスト: `app/src/test/`
- 計測テスト: `app/src/androidTest/`
- Room schema export: `app/schemas/io.shellpilot.app.data.ShellPilotDatabase/<version>.json`
- Android backup / data extraction 設定: `app/src/main/res/xml/full_backup_content.xml`、`app/src/main/res/xml/data_extraction_rules.xml`
- 設計・検証ドキュメント: `README.md`、`CONTRIBUTING.md`、`docs/`

Room の `@Database(version = ...)` を上げた場合は、対応する schema JSON と migration テストを同じ変更に含めてください。

## Git に入れないもの

- Codex ローカル設定: `.claude/`
- 画面レビュー資産: `review/`
- Android Studio / Gradle のローカル成果物: `.gradle/`、`build/`、`.kotlin/`、`.cxx/`
- 端末固有設定: `local.properties`、`*.local.properties`
- 署名鍵と認証情報: `*.jks`、`*.keystore`、`keystore.properties`、`service-account*.json`
- APK / AAB / mapping / logcat / hprof などの生成物

レビュー用キャプチャや ImageGen モックは `review/captures/`、`review/design/` に保存してよいですが、通常はコミットしません。PNG、XML、Markdown、SVG、ImageGen の source / output、`latest-run` symlink はすべてローカルレビュー資産として扱います。

## コミット前チェック

```bash
git status -sb
git diff --check
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleOssDebug
```

DB、SSH、認証、Compose UI、backup / restore に関わる変更では、次も実行してください。

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testOssDebugUnitTest :app:lintOssDebug
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew connectedOssDebugAndroidTest
```

## キャプチャ資産の扱い

Android の画面確認は、既存 AVD `OshiCue_Medium_Phone_API_36_1` と Android Studio の Running Devices を使います。保存先は次の形式に統一します。

```text
review/captures/android/<YYYYMMDD-HHMMSS>/
```

ライト / ダークの両方を撮る場合は、配下に `light/` と `dark/` を作ります。PNG と UI ツリー XML は同名で保存し、最終報告では保存先と主要画像リンクを示します。
