# Test Plan

ShellPilot の変更は、影響範囲に応じて以下の順で検証します。

## 1. 基本検証

すべての Android 実装変更で実行します。

```bash
cd /Users/contras11/codes/SSH_App/connectbot
git diff --check
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleOssDebug
```

## 2. 広範囲変更の検証

DB、SSH、認証、backup / restore、Compose UI、Navigation、文字列リソースに関わる変更では、基本検証に加えて実行します。

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testOssDebugUnitTest :app:lintOssDebug
```

既存 AVD `OshiCue_Medium_Phone_API_36_1` が起動している場合は、計測テストも実行します。

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew connectedOssDebugAndroidTest
```

## 3. DB / migration 検証

Room schema を変更する場合は、次を同じ変更に含めます。

- `app/schemas/io.shellpilot.app.data.ShellPilotDatabase/<version>.json`
- `MigrationTestHelper` または実 SQLite DB を使った migration テスト
- legacy ConnectBot DB reader / importer の回帰テスト
- JSON import / export の参照補正テスト

重点確認項目:

- v1 以降の既存 migration が最新 schema まで通ること
- `hosts.profile_id`、`hosts.jump_host_id`、`known_hosts` の制約が既存データを壊さないこと
- sentinel 値を持つ `pubkey_id` を FK 化しない前提が維持されること
- `connectbot.db`、WAL/SHM、`secure_host_passwords` が OS 標準 backup / device transfer へ直接入らないこと

## 4. 手動 UI / 端末検証

Android Studio の Running Devices で既存 AVD を見える状態にして確認します。画面遷移やフォーム操作は Computer Use を主操作にし、`adb` は UI ツリー取得、ログ、キャプチャ保存の補助に限定します。

重点画面:

- ホスト一覧、検索、フィルタ、JSON import / export
- ホスト追加 / 編集、Local / SSH / Telnet の URI
- ホスト鍵確認、パスワード / 公開鍵 / 生体認証プロンプト
- セッション画面、制御キー列、Command Composer、切断 / リサイズ
- ショートカット設定、公式テンプレート更新、表示タブ管理
- 公開鍵、ポート転送、プロファイル、カラースキーム
- 設定、ヘルプ、ヒント、ログ、問い合わせ、EULA

キャプチャを残す場合は `review/captures/android/<YYYYMMDD-HHMMSS>/` に保存し、通常はコミットしません。

## 5. リリース前検証

リリース候補では、CI 相当の確認も候補にします。

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew build bundle
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew connectedCheck --continue
```
