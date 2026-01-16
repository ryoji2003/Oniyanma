# Quiz Festival System - 変更内容

## 概要

`request.md`の仕様書に基づき、博物館向けリアルタイムクイズフェスティバルシステムを実装しました。

---

## 新規作成ファイル

### 1. バックエンド

#### `src/main/java/jp/ac/u_aizu/quizapp/FestivalServer.java`

WebSocketとREST APIを備えたクイズフェスティバルサーバーを新規作成しました。

**主な機能：**

| 機能 | 説明 |
|------|------|
| HTTPサーバー | ポート8080でREST APIと静的ファイルを提供 |
| WebSocketサーバー | ポート8081でリアルタイム通信を実現 |
| プレイヤー管理 | 参加者の登録・ID自動発行・スコア管理 |
| クイズセッション | 状態管理（IDLE→WAIT_JOIN→QUESTION_ACTIVE→QUESTION_CLOSED→RESULT→END） |
| タイマー | サーバー起点の60秒カウントダウン |
| ランキング | スコア順、同点時は参加順でソート |

**REST APIエンドポイント：**

| メソッド | エンドポイント | 説明 |
|----------|----------------|------|
| POST | `/api/join` | セッション参加、playerIdを返却 |
| POST | `/api/answer` | 回答送信 |
| GET | `/api/result` | ランキング・個人スコア取得 |
| GET | `/api/session/status` | セッション状態取得 |
| POST | `/api/session/reset` | セッションリセット |
| GET | `/api/questions` | 問題一覧取得 |

**WebSocketイベント：**

| イベント | 方向 | 説明 |
|----------|------|------|
| `question.start` | サーバー→全員 | 問題開始、問題文・選択肢を配信 |
| `question.end` | サーバー→全員 | 問題終了、正解を配信 |
| `quiz.finish` | サーバー→全員 | クイズ終了、結果表示へ |
| `player.joined` | サーバー→ホスト | 参加者数更新 |
| `answer.received` | サーバー→ホスト | 回答数更新 |

---

### 2. フロントエンド

#### `src/main/resources/static/festival.html`

フェスティバルのランディングページ

- ホスト・参加者への導線
- 参加用QRコード自動生成
- 操作手順の説明

#### `src/main/resources/static/host.html`

ホスト操作パネル

- クイズ開始/次の問題/終了ボタン
- リアルタイム参加者数表示
- タイマー表示（円形プログレス）
- 回答状況モニター
- 最終ランキング表示

#### `src/main/resources/static/play.html`

参加者用インターフェース

- ニックネーム入力・参加画面
- 待機画面
- 問題回答画面（タイマーバー付き）
- 正誤結果画面
- 最終結果・ランキング画面
- 上位3位入賞時の紙吹雪演出

---

## 技術仕様

### 使用技術

| 項目 | 技術 |
|------|------|
| バックエンド | Java (HttpServer + 独自WebSocket実装) |
| フロントエンド | HTML + Tailwind CSS + Vanilla JavaScript |
| 通信 | REST API + WebSocket |
| 外部依存 | なし（Pure Java） |

### システム構成

```
[参加者ブラウザ] ←──WebSocket──→ [FestivalServer:8081]
       ↓                              ↓
    REST API ←─────────────→ [FestivalServer:8080]
                                      ↓
                              [静的ファイル配信]
```

---

## 起動方法

```bash
# コンパイル
javac -d out src/main/java/jp/ac/u_aizu/quizapp/FestivalServer.java

# 実行
java -cp out jp.ac.u_aizu.quizapp.FestivalServer
```

**アクセスURL：**

| 画面 | URL |
|------|-----|
| ランディング | http://localhost:8080/festival.html |
| ホスト操作 | http://localhost:8080/host.html |
| 参加者 | http://localhost:8080/play.html |

---

## 使用フロー

1. **ホスト**が `host.html` を開く
2. **ホスト**が「Start Quiz」をクリック
3. **参加者**が `play.html` を開き、ニックネームを入力して参加
4. **ホスト**が「Begin First Question」をクリックして問題開始
5. **参加者**が60秒以内に回答
6. **ホスト**が「Next Question」で次の問題へ
7. 全問終了後、**ホスト**が「Show Final Results」でランキング表示

---

## デフォルト問題

システムには5問のサンプル問題が含まれています：

1. 縄文時代の土器の模様について
2. 打製石器の用途について
3. 弥生時代に伝わった技術について
4. 古墳時代の埴輪について
5. 会津地方の伝統工芸品について

---

## 既存ファイルとの関係

- `ThemeController.java` - 既存のクイズ作成機能（変更なし）
- `FestivalServer.java` - 新規追加（フェスティバル用サーバー）

両方のサーバーは独立して動作します。フェスティバル機能を使用する場合は `FestivalServer` を起動してください。
