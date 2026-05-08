# ShellPilot Inventory

## 目的

既存コードの把握と改造箇所の特定。

---

## 調査項目

- セッション開始エントリポイント
- TerminalViewクラス
- SSH接続管理クラス
- known_hosts処理箇所
- 鍵管理ロジック

---

## 成果物

- クラス依存図
- セッションフロー図

---

## 1. SSHセッション開始のエントリポイント

### 主要ファイル

| ファイル | クラス | 役割 |
|----------|--------|------|
| `app/src/main/java/io/shellpilot/app/ui/MainActivity.kt` | `MainActivity` | アプリ起動・サービスバインド・Intent処理 |
| `app/src/main/java/io/shellpilot/app/service/TerminalManager.kt` | `TerminalManager` | 全接続を管理するForeground Service |
| `app/src/main/java/io/shellpilot/app/service/TerminalBridge.kt` | `TerminalBridge` | 1つのSSH接続を表すブリッジ |
| `app/src/main/java/io/shellpilot/app/transport/TransportFactory.kt` | `TransportFactory` | プロトコル別トランスポート生成ファクトリ |
| `app/src/main/java/io/shellpilot/app/transport/SSH.kt` | `SSH` | SSH接続・認証・セッション管理 |
| `app/src/main/java/io/shellpilot/app/data/entity/Host.kt` | `Host` | 接続先ホスト設定のRoomエンティティ |

### 接続開始フロー (ユーザ操作 → SSH確立)

```
Phase 1: アプリ起動・サービスバインド
──────────────────────────────────────
MainActivity.onCreate()                          [MainActivity.kt:120]
  +-- TerminalManager サービスにバインド            [MainActivity.kt:138-139]
  |     +-- ServiceConnection.onServiceConnected()  [MainActivity.kt:105]
  |           +-- AppViewModelにmanager参照を設定
  +-- Compose UI + Navigation をセットアップ

Phase 2: ユーザがホスト一覧からホストを選択
──────────────────────────────────────
HostListScreen                                   [HostListScreen.kt]
  +-- LazyColumn内のホスト項目をタップ              [HostListScreen.kt:433]
      +-- MainActivity.onNavigateToConsole { host -> }  [MainActivity.kt:269]
          +-- NavController.navigate("console/${host.id}")
              +-- ConsoleScreen が hostId をルートパラメータとして受信
                  +-- ConsoleViewModel.setTerminalManager()  [ConsoleViewModel.kt:59]

Phase 3: Bridge(接続インスタンス)の生成
──────────────────────────────────────
ConsoleViewModel.ensureBridgeExists()            [ConsoleViewModel.kt:130]
  +-- hostId に対応する bridge が未作成の場合:
      +-- TerminalManager.openConnectionForHostId(hostId)  [TerminalManager.kt:393]
          +-- hostRepository.findHostById(hostId)           [TerminalManager.kt:394]
          +-- TerminalManager.openConnection(host)          [TerminalManager.kt:315-352]

Phase 4: トランスポート解決・接続開始
──────────────────────────────────────
TerminalManager.openConnection(host)             [TerminalManager.kt:315]
  +-- TerminalBridge(manager, host, dispatchers) 生成  [TerminalManager.kt:321]
  +-- bridge.startConnection()                         [TerminalBridge.kt:401]
      +-- TransportFactory.getTransport(host.protocol)  [TerminalBridge.kt:402]
      |     +-- SSH / Telnet / Local インスタンスを返却
      +-- transport に bridge, manager, host を設定     [TerminalBridge.kt:409-411]
      +-- scope.launch(dispatchers.io) {               [TerminalBridge.kt:422]
            transport.connect()                         [TerminalBridge.kt:440]
          }

Phase 5: SSH接続確立
──────────────────────────────────────
SSH.connect()                                    [SSH.kt:827]
  +-- JumpHost 設定があればプロキシ接続              [SSH.kt:831-846]
  +-- Connection インスタンス生成                    [SSH.kt:848]
  +-- connection.connect(HostKeyVerifier, ipVersion) [SSH.kt:863]
  |     +-- HostKeyVerifier がホスト鍵を検証
  |           +-- known_hostsデータベースを照合
  |           +-- 新規/変更時はユーザに確認プロンプト  [SSH.kt:161-299]
  +-- 認証ループ (最大AUTH_TRIES回)                  [SSH.kt:921-927]
      +-- authenticate()                             [SSH.kt:923]

Phase 6: SSH認証
──────────────────────────────────────
SSH.authenticate()                               [SSH.kt:318]
  1. None認証を試行                                 [SSH.kt:335-338]
  2. 公開鍵認証を試行                               [SSH.kt:349-381]
     +-- pubkeyId == PUBKEYID_ANY: 全読込済み鍵で試行
     +-- 特定鍵IDが指定されている場合はその鍵で試行
     +-- Biometric鍵: Android KeyStore + 生体認証   [SSH.kt:464-529]
     +-- 暗号化鍵: パスフレーズ入力を要求            [SSH.kt:531-579]
  3. Keyboard-Interactive認証                       [SSH.kt:384-395]
  4. パスワード認証                                 [SSH.kt:396-419]
     +-- 保存済みパスワードを先に試行
     +-- 失敗時はユーザにプロンプト

Phase 7: セッションオープン
──────────────────────────────────────
SSH.finishConnection()                           [SSH.kt:615]
  +-- authenticated = true                         [SSH.kt:616]
  +-- ポートフォワーディング有効化                  [SSH.kt:618-625]
  +-- wantSession が true の場合:                   [SSH.kt:628]
  |     +-- session = connection.openSession()      [SSH.kt:635]
  |     +-- session.requestPTY(...)                 [SSH.kt:641]
  |     +-- session.startShell()                    [SSH.kt:642]
  |     +-- stdin/stdout/stderr 取得                [SSH.kt:644-646]
  |     +-- sessionOpen = true                      [SSH.kt:648]
  +-- bridge.onConnected()                         [SSH.kt:650]

Phase 8: データリレー開始・画面表示
──────────────────────────────────────
TerminalBridge.onConnected()                     [TerminalBridge.kt:526]
  +-- Relay(bridge, transport, dispatchers, encoding) 生成  [TerminalBridge.kt:546]
  +-- Relay.start() -- コルーチンで起動              [TerminalBridge.kt:548]
  |     +-- transport.read() でSSHデータ読取
  |     +-- charset デコード
  |     +-- bridge.terminalEmulator.writeInput() で端末バッファに書込
  +-- PTYサイズをフォントに合わせてリサイズ          [TerminalBridge.kt:554]
  +-- postLogin コマンド注入 (設定時)               [TerminalBridge.kt:557]
```

---

## 2. ターミナル描画クラス

### アーキテクチャ概要

本アプリは **Jetpack Compose** ベースのUI。XMLレイアウトは使用していない。
ターミナル描画は外部ライブラリ `org.connectbot:termlib:0.0.18` の `Terminal` Composable に委譲。

### 主要クラス

| ファイル | クラス/Composable | 役割 |
|----------|-------------------|------|
| `app/.../ui/screens/console/ConsoleScreen.kt` | `ConsoleScreen` | ターミナル画面全体のComposable |
| `app/.../ui/screens/console/ConsoleViewModel.kt` | `ConsoleViewModel` | コンソール画面の状態管理 |
| `app/.../ui/components/TerminalKeyboard.kt` | `TerminalKeyboard` | 仮想キーボードオーバーレイ |
| `app/.../service/TerminalBridge.kt` | `TerminalBridge` | SSH接続とUI層を繋ぐブリッジ |
| `app/.../service/Relay.kt` | `Relay` | コルーチンベースのデータリレー |
| `app/.../service/TerminalKeyListener.kt` | `TerminalKeyListener` | ハードウェア/ソフトキーボード入力処理 |
| (termlib外部ライブラリ) | `Terminal` | 端末内容を描画するComposable |
| (termlib外部ライブラリ) | `TerminalEmulator` | VT100/xterm端末エミュレーション |
| (termlib外部ライブラリ) | `TerminalEmulatorFactory` | エミュレータ生成ファクトリ |

### ConsoleScreen の View構造

```
ConsoleScreen
  +-- Scaffold
      +-- SnackbarHost
      +-- Box (fillMaxSize, windowInsets padding)
          +-- Terminal (termlib Composable)           [ConsoleScreen.kt:463-490]
          |     +-- terminalEmulator: bridge から取得
          |     +-- typeface: フォント設定
          |     +-- initialFontSize: フォントサイズ
          |     +-- modifierManager: 修飾キー管理
          +-- TerminalKeyboard (画面下部オーバーレイ)  [ConsoleScreen.kt:509-527]
          +-- InlinePrompt (認証ダイアログ等)          [ConsoleScreen.kt:533-546]
          +-- TopAppBar (メニューオーバーレイ)         [ConsoleScreen.kt:613-819]
```

### データフロー (SSH出力 → 画面表示)

```
SSH Transport (stdout)
       |
       v
  Relay.start() -- transport.read() でバイト列読取
       |
       v
  CharsetDecoder -- 文字コードデコード (UTF-8, CP437等)
       |
       v
  TerminalEmulator.writeInput() -- VT100制御シーケンス解析・バッファ更新
       |
       v
  Terminal Composable -- StateFlowの変更を検知して再描画
       |
       v
  Display (画面表示)
```

### フォントと描画

- `TerminalBridge` でフォントメトリクスを管理 (`charWidth`, `charHeight`, `charTop`)
- `Paint` を使用してモノスペースフォントの寸法を計算 [TerminalBridge.kt:195-199, 676-704]
- フォントサイズは `fontSizeFlow: StateFlow<Float>` でリアクティブに管理
- ボリュームキーでフォントサイズ変更可能 [TerminalKeyListener.kt:128-139]

### カラースキーム

- 16色ANSIカラーを `ColorSchemeRepository` から読込 [TerminalBridge.kt:221-268]
- `terminalEmulator.applyColorScheme()` で適用
- 前景色/背景色のデフォルトインデックス管理

### ターミナル機能

- VT100/xterm エミュレーション (termlib)
- OSC 52 クリップボード (リモートコピー) [TerminalBridge.kt:252-256]
- OSC 9;4 プログレスバー [TerminalBridge.kt:258-262]
- OSC 8 ハイパーリンク [ConsoleScreen.kt:482-489]
- ベル通知 [TerminalBridge.kt:241-245]
- テキスト選択 (SelectionController)

---

## 3. 主要クラスの責務一覧

### UI層

| クラス | 責務 |
|--------|------|
| `MainActivity` | アプリのエントリポイント。TerminalManager サービスのバインド、Intent (`ssh://`) 処理、Compose Navigation のセットアップ |
| `HostListScreen` | ホスト一覧の表示。LazyColumn でホスト一覧を描画、タップでコンソール画面へ遷移 |
| `ConsoleScreen` | ターミナル画面。Terminal Composable の表示、キーボード、メニュー、認証プロンプトを統合 |
| `ConsoleViewModel` | コンソール画面の状態管理。bridge一覧の監視、ベル/プログレスイベント処理 |
| `TerminalKeyboard` | 仮想キーボードUI。TerminalBridge を受け取りキー入力を中継 |

### サービス層

| クラス | 責務 |
|--------|------|
| `TerminalManager` | Foreground Service。全TerminalBridgeのライフサイクル管理、SSH鍵のインメモリキャッシュ (`KeyHolder`)、接続/切断の統括 |
| `TerminalBridge` | 1つの接続セッションを表現。Transport と TerminalEmulator を繋ぐ中核。フォント/カラー管理、接続ライフサイクル制御 |
| `Relay` | コルーチンベースのデータリレー。Transport の read() → charset変換 → TerminalEmulator の writeInput() を連続実行 |
| `TerminalKeyListener` | キーイベント処理。OnKeyListener + ModifierManager 実装。ハードウェアキー/IME入力をターミナル入力に変換 |

### トランスポート層

| クラス | 責務 |
|--------|------|
| `Transport` (sealed class) | プロトコル種別定義。`Transport.Ssh`, `Transport.Telnet`, `Transport.Local` の3つのシングルトン |
| `TransportFactory` (object) | トランスポート生成ファクトリ。プロトコル名からインスタンス生成、URI解析 |
| `AbsTransport` (abstract) | トランスポート共通インターフェース。`connect()`, `read()`, `write()`, `flush()`, `close()`, `setDimensions()` |
| `SSH` | SSH接続実装。trilead-ssh2 ライブラリを使用。接続/認証/セッション/ポートフォワード管理。`ConnectionMonitor`, `InteractiveCallback`, `AuthAgentCallback` を実装 |
| `Telnet` | Telnet接続実装。`TelnetProtocolHandler` (de.mud.telnet) を使用。ソケットベース接続、ウィンドウサイズネゴシエーション |
| `Local` | ローカルシェル実装。`/system/bin/sh` のサブプロセス生成。ネットワーク不使用 |

### データ層

| クラス | 責務 |
|--------|------|
| `Host` (Room Entity) | 接続先ホスト設定。プロトコル/ユーザ名/ホスト名/ポート/認証設定/ターミナルプロファイル等を保持 |
| `HostDao` (Room DAO) | Hostテーブルへのデータアクセス。CRUD操作、Flow による監視 |
| `HostRepository` | Hostデータの一元管理。DAO経由のCRUD、ホスト検索クエリ |

### DB / backup 関連

| ファイル | 役割 |
|----------|------|
| `app/src/main/java/io/shellpilot/app/data/ShellPilotDatabase.kt` | Room database 定義、schema version、migration 登録 |
| `app/schemas/io.shellpilot.app.data.ShellPilotDatabase/` | Room schema export。DB version 更新時は必ず追跡対象にする |
| `app/src/main/res/xml/full_backup_content.xml` | Android full backup から DB / 保存パスワードを除外する設定 |
| `app/src/main/res/xml/data_extraction_rules.xml` | Android 12+ の cloud backup / device transfer 除外設定 |
| `app/src/main/java/io/shellpilot/app/service/BackupAgent.kt` | Android BackupAgent。通常DBと同じ migration 設定を使う |
| `app/src/main/java/io/shellpilot/app/service/BackupFilter.kt` | backup / restore 用のフィルタ済み temp DB 作成 |
| `app/src/main/java/io/shellpilot/app/data/migration/DatabaseMigrator.kt` | 旧 ConnectBot DB から ShellPilot DB への移行 |
| `app/src/main/java/io/shellpilot/app/data/migration/LegacyHostDatabaseReader.kt` | legacy hosts / known_hosts / port forwards の読み取り |

---

## 4. 設計上の注意点

### アーキテクチャ特性

1. **Jetpack Compose 全面採用**: XMLレイアウトは不使用。全画面がComposableで定義されている
2. **Kotlin Coroutines**: SSH I/Oは `dispatchers.io` で非同期実行。Relay もコルーチンベース
3. **StateFlow によるリアクティブUI**: bridge一覧、フォントサイズ、修飾キー状態等すべてFlowで監視
4. **WeakReference**: TerminalManager の hostBridgeMap / nicknameBridgeMap は WeakReference でメモリリーク防止
5. **Foreground Service**: TerminalManager はフォアグラウンドサービスとして動作し、通知を表示

### 改修時の留意事項

1. **termlib 外部ライブラリ依存**: ターミナル描画の中核 (`Terminal`, `TerminalEmulator`) は `org.connectbot:termlib:0.0.18` に存在。描画カスタマイズにはライブラリ側の変更が必要になる可能性がある
2. **SSH コアの分離**: SSH プロトコル実装は `com.trilead:ssh2` ライブラリに依存。CLAUDE.md の指示通り SSH コアは極力改変しないこと
3. **sealed class パターン**: `Transport` は sealed class で型安全にプロトコルを定義。新プロトコル追加時は sealed class への追加が必要
4. **Bridge パターンの中心性**: `TerminalBridge` がトランスポート・エミュレータ・UI すべてを仲介する中核。変更影響が大きいため慎重に扱うこと
5. **認証フローの複雑さ**: SSH 認証は None → 公開鍵 → Biometric → Keyboard-Interactive → パスワード の優先順で試行。各段階でユーザプロンプトが発生し得る
6. **ProxyJump 対応**: SSH はジャンプホストチェーンをサポート。接続処理の再帰的構造に注意
7. **Null安全の徹底**: Kotlin コードは `Host?`, `Connection?`, `Session?` 等すべて Null安全。CLAUDE.md の指示に準拠

---

## 5. クラス依存図 (テキスト)

```
                         +------------------+
                         |   MainActivity   |
                         +--------+---------+
                                  | binds
                                  v
                         +------------------+
                         | TerminalManager  |  (Foreground Service)
                         +--------+---------+
                                  | creates/manages
                                  v
                         +------------------+
                         | TerminalBridge   |  (1接続 = 1インスタンス)
                         +--+----+------+---+
                            |    |      |
               creates      |    |      |  references
          +--------+        |    |      +-------+
          v                 v    v              v
    +---------+    +-------+  +-------------+  +------------------+
    |  Relay  |    | SSH   |  | Terminal    |  | TerminalKey      |
    |         |    |Telnet |  | Emulator    |  | Listener         |
    |         |    |Local  |  | (termlib)   |  |                  |
    +---------+    +---+---+  +------+------+  +------------------+
         |             |             |
         | reads       | uses       | renders
         v             v             v
    +---------+  +-----------+  +-------------+
    |Transport|  | trilead   |  |  Terminal   |
    | .read() |  | ssh2 lib  |  | Composable  |
    +---------+  +-----------+  | (termlib)   |
                                +-------------+
                                      |
                                      v
                              +---------------+
                              | ConsoleScreen |
                              | (Compose UI)  |
                              +---------------+
```

---

## 6. セッションフロー図 (テキスト)

```
[ユーザ]
   |
   | ホストをタップ
   v
[HostListScreen] --navigate--> [ConsoleScreen]
                                    |
                                    | ensureBridgeExists()
                                    v
                              [ConsoleViewModel]
                                    |
                                    | openConnectionForHostId()
                                    v
                              [TerminalManager]
                                    |
                                    | new TerminalBridge(host)
                                    | bridge.startConnection()
                                    v
                              [TerminalBridge]
                                    |
                                    | TransportFactory.getTransport()
                                    | transport.connect()
                                    v
                                 [SSH]
                                    |
                       +------------+------------+
                       |                         |
                  TCP接続確立              ホスト鍵検証
                       |                    (known_hosts)
                       v                         |
                  認証ループ  <------------------+
                       |
          +------+-----+------+------+
          |      |            |      |
        None  公開鍵   Keyboard  パスワード
                |     Interactive
                |
      +----+----+----+
      |         |    |
   通常鍵  Biometric 暗号化鍵
                |
                v
         finishConnection()
                |
         +------+------+
         |             |
     PTYオープン   ポートFW有効化
     Shell開始
         |
         v
   bridge.onConnected()
         |
         v
   Relay.start()
         |
         +----> transport.read() --> decode --> emulator.writeInput()
         |                                          |
         |                              Terminal Composable 再描画
         |                                          |
         +<---- キー入力 <---- TerminalKeyListener <-+
```
