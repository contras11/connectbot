# Data Model

ShellPilot の永続データは Room database `connectbot.db` に保存します。DB ファイル名は ConnectBot 由来の既存データ互換性を守るため変更しません。

現在の Room schema は `ShellPilotDatabase` version 8 です。schema export は `app/schemas/io.shellpilot.app.data.ShellPilotDatabase/` に保存します。

## 主要テーブル

### hosts

接続先ホスト設定です。SSH / Telnet / Local を同じテーブルで扱います。

主な列:

- `id`: 永続ホスト ID
- `protocol`: `ssh`、`telnet`、`local`
- `nickname`
- `username`
- `hostname`
- `port`
- `pubkey_id`: 公開鍵参照。`-1` / `-2` などの sentinel 値があるため FK 化しません。
- `profile_id`: `profiles(id)` への参照。欠損時は `1` の標準プロファイルへ補正します。
- `jump_host_id`: 同じ `hosts(id)` への任意参照。循環、自己参照、非 SSH 参照は migration / runtime guard で拒否します。

制約:

- `profile_id` は `ON DELETE SET DEFAULT`
- `jump_host_id` は `ON DELETE SET NULL`
- `protocol + username + hostname + port` の検索向け index を維持します。

### profiles

端末表示設定です。

主な列:

- `id`
- `name`
- `font_size`
- `emulation`
- `color_scheme_id`

`color_scheme_id` が存在しないカスタム配色を指す場合は、migration で `-1` に戻します。

### pubkeys

公開鍵 / 秘密鍵の管理テーブルです。

主な列:

- `id`
- `nickname`
- `storage_type`: `EXPORTABLE` または `ANDROID_KEYSTORE`
- `private_key`
- `public_key`
- `startup`
- `allow_backup`

不変条件:

- `ANDROID_KEYSTORE` は `startup=false`、`allow_backup=false`、`private_key=NULL`
- `EXPORTABLE` で `private_key=NULL` の鍵は起動時ロード対象にしません。
- 秘密鍵や保存パスワードは OS 標準 backup / device transfer に直接含めません。

### known_hosts

ホスト鍵検証のための known hosts テーブルです。

主な列:

- `host_id`
- `hostname`
- `port`
- `host_key_algo`
- `host_key`

v8 では重複行を削除し、`host_id, hostname, port, host_key_algo, host_key` の一意性を持たせます。ホスト鍵変更承認時は、同一 endpoint + algorithm の旧レコードを置換してから保存します。

### port_forwards

ポート転送設定です。

主な列:

- `host_id`
- `nickname`
- `type`: `local`、`remote`、`dynamic5`
- `source_port`
- `destination_host`
- `destination_port`

legacy 値 `dynamic4` は `dynamic5` に正規化します。未対応の type は migration / import 時に除外します。

## JSON import / export

JSON import / export は、DB 内部 ID をそのまま外部互換契約にしないように参照を補正します。

- 除外された pubkey を指す host は `PUBKEYID_NEVER` に落とします。
- import で既存 host を skip した場合、その host への `jump_host_id` は second pass で更新しません。
- unique index がない child table は、source ID 衝突だけで skip しません。

## Backup / restore

Android OS の full backup / device transfer からは次を除外します。

- `connectbot.db`
- `connectbot.db-wal`
- `connectbot.db-shm`
- `secure_host_passwords.xml`

ShellPilot の backup / restore は `BackupAgent` と `BackupFilter` のフィルタ済み経路を使います。temp DB は作成前に sidecar を削除し、`TRUNCATE` journal で単一 DB snapshot として扱います。
