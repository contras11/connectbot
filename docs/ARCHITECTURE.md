# Architecture Design

## 1. 層構造

ui (Compose)
↓
domain (UseCase)
↓
data (Repository)
↓
sshcore (ConnectBot由来)

UIはsshcoreへ直接依存しない。

---

## 2. モジュール責務

### ui

- Compose画面
- Navigation
- ViewModel

### domain

- StartSessionUseCase
- SendShortcutUseCase
- ExportConfigUseCase

### data

- HostRepository
- ShortcutRepository
- SettingsRepository
- Room database `ShellPilotDatabase`
- BackupAgent / BackupFilter

### sshcore

- SessionController（Kotlinラッパ）
- 既存SSH接続ロジック

---

## 3. 依存ルール

- ui → domain
- domain → data, sshcore
- sshcoreは下位層のみ参照

---

## 4. DB / backup 境界

- 永続DBは `connectbot.db` を維持する。これは ConnectBot 由来データとの互換性を守るためである。
- Room schema export は `app/schemas/io.shellpilot.app.data.ShellPilotDatabase/` にコミットする。
- DB version を上げる場合は、migration、schema JSON、migration test、`docs/DATA_MODEL.md` を同時に更新する。
- Android OS の full backup / device transfer には DB 本体、WAL/SHM、保存パスワードを直接含めない。
- backup / restore は `BackupAgent` と `BackupFilter` のフィルタ済み経路を使う。

---

## 5. 作業ツリー境界

- `.claude/` と `review/` はローカル作業・レビュー資産であり、通常は Git に含めない。
- キャプチャ、ImageGen モック、logcat、hprof、APK/AAB は `docs/WORKTREE_HYGIENE.md` のルールに従って管理する。
