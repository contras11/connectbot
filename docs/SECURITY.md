# Security Design

ShellPilot は ConnectBot 由来の SSH / terminal 実装を尊重しつつ、Android の保存領域、backup / restore、ホスト鍵検証を明示的に保護します。

## ホスト鍵検証

- 初回接続時はホスト鍵確認を必須にします。
- 既存ホスト鍵と異なる鍵が返った場合は、ユーザー承認なしに上書きしません。
- 承認後は同一 `hostId + hostname + port + algorithm` の旧レコードを置換してから保存します。
- `known_hosts` は v8 schema で重複を抑制します。

## 秘密鍵と保存パスワード

- 秘密鍵の外部共有を既定では許可しません。
- Android Keystore 管理鍵は `private_key=NULL`、`allow_backup=false`、`startup=false` として扱います。
- 保存パスワードは `secure_host_passwords.xml` に入り、OS 標準 backup / device transfer から除外します。
- `EXPORTABLE` 鍵でも `private_key=NULL` の行は起動時ロード対象にしません。

## Android backup / device transfer

以下は `app/src/main/res/xml/full_backup_content.xml` と `app/src/main/res/xml/data_extraction_rules.xml` で除外します。

- `connectbot.db`
- `connectbot.db-wal`
- `connectbot.db-shm`
- `secure_host_passwords.xml`

バックアップ用にユーザーへ渡すデータは、`BackupAgent` / `BackupFilter` のフィルタ済み経路で生成します。Room DB を OS 標準 backup に丸ごと入れないことが前提です。

## Jump Host

- Jump Host は SSH ホストのみ許可します。
- 自己参照、循環参照、非 SSH 参照は保存時と接続時の両方で拒否します。
- DB import や legacy migration で壊れた値が入っても、接続時に無限再帰や StackOverflow にしません。

## JSON import / export

- import された pubkey 参照が存在しない場合は、`PUBKEYID_NEVER` に落として安全側に倒します。
- host や port forward の壊れた参照は skip または null 化します。
- export では認証情報や秘密鍵の取り扱いを UI 上で明示し、レビュー用キャプチャに秘密情報を含めません。

## リリース時の注意

- `*.jks`、`*.keystore`、`keystore.properties`、service account JSON は Git に入れません。
- release signing はローカルまたは CI の secret store で注入します。
- 生成された APK / AAB、mapping、logcat、hprof は成果物管理先に置き、通常の Git commit には含めません。
