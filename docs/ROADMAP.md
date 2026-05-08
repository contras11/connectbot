# Development Roadmap

ShellPilot は ConnectBot フォークとして、既存 SSH / terminal 互換性を保ちながら Kotlin / Compose / Room ベースへ移行しています。

## 完了済みの基盤

- ShellPilot 名称、`io.shellpilot.app` namespace、debug applicationId `io.shellpilot.app.debug`
- Compose ベースの主要画面
- SSH / Telnet / Local 接続導線
- Claude Code / Codex などのショートカット管理
- Room schema export と migration テスト運用
- Android backup / device transfer から DB / 保存パスワードを除外する設定
- モノクロ基調の compact terminal cockpit UI

## 次に重点確認する領域

### 1. DB / migration

- schema version 更新時の migration と schema JSON の同時更新
- legacy ConnectBot DB 移行の回帰確認
- JSON import / export の参照補正

### 2. 接続安全性

- ホスト鍵変更時の確認と置換
- Jump Host の循環 / 非 SSH 参照拒否
- Telnet / Local の失敗時 UI とログ

### 3. ショートカット

- 公式テンプレートの明示更新
- ユーザー編集済み shortcut の保持
- Command Composer への保存済み shortcut 反映

### 4. UI / QA

- Android Studio Running Devices での実画面確認
- ライト / ダーク両方のキャプチャ
- `review/captures/android/<YYYYMMDD-HHMMSS>/` のローカルレビュー資産管理

### 5. リリース準備

- release signing の secret 管理
- APK / AAB の署名確認
- `docs/RELEASE.md` の手順に沿ったリリース検証
