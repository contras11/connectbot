# Claude Code Task Templates

## 調査タスク

目的:
ShellPilotの実装箇所を、READMEやコメントだけでなく実コード・テスト・設定から特定する。

やること:

1. 対象機能の entry point、Repository、ViewModel、テストを列挙
2. DB / SSH / 認証 / backup へ影響するかを分類
3. 既存テストと不足テストを確認
4. 必要に応じて `docs/INVENTORY.md`、`docs/DATA_MODEL.md`、`docs/SECURITY.md` へ反映候補を記録

---

## 実装タスク

目的:
ShellPilotの既存構成に合わせて、最小限かつ検証可能な実装を行う。

制約:

- SSHコアは改変最小限
- DB schema を変更する場合は migration、schema export、migration test を同時に追加
- `.claude/`、`review/`、署名鍵、ローカル成果物はコミットしない

成果物:

- Kotlinコード全文
- 動作確認手順
- 次ステップ提案

確認コマンド:

```bash
git diff --check
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleOssDebug
```
