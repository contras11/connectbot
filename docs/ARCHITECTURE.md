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

### sshcore

- SessionController（Kotlinラッパ）
- 既存SSH接続ロジック

---

## 3. 依存ルール

- ui → domain
- domain → data, sshcore
- sshcoreは下位層のみ参照
