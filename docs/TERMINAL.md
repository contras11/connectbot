# Terminal Design

## 1. 表示崩れ対策

1. 等幅フォント固定
2. 列数再計算
3. PTYサイズ更新

---

## 2. PTYサイズ計算

columns = floor(widthPx / charWidthPx)
rows = floor(heightPx / charHeightPx)

---

## 3. 画面回転対応

- onConfigurationChangedで再計算
- セッションへwindow size通知

---

## 4. IME対応

- 最低限入力可能であることを受入基準とする
