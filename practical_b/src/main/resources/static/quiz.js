// quiz.js

// --- 設定 ---
const API_BASE = "http://localhost:8080/api";
const TOTAL_QUESTIONS = 3; // 3問設定

// デモ用問題データ（本来はDBから取得しますが、デモ用に固定）
// テーマはサーバーから取得したものをヘッダー等に反映させます
const DEMO_QUESTIONS = [
  {
      text: "江戸時代、会津地方の農村で指導的立場にあった佐瀬与次右衛門が記した、当時の農業技術や生活の知恵をまとめた重要な書物は？",
      choices: ["会津農書", "農業全書", "百姓伝記", "会津風土記"],
      correct: 0 // 正解：会津農書（福島県立博物館の代表的な展示資料の一つ）
    },
    {
      text: "福島県立博物館に収蔵されている農具の中には、購入した年月日や金額、店名などが墨で書かれているものがあります。これらは何と呼ばれていますか？",
      choices: ["紀年銘民具", "墨書土器", "履歴付き農具", "覚書道具"],
      correct: 0 // 正解：紀年銘（きねんめい）民具（いつ作られ使われたかが分かる貴重な資料）
    },
    {
      text: "博物館の「稲とくらし」コーナーなどで見ることができる、江戸時代中期以降に普及した、たくさんの鉄の歯で稲を脱穀（だっこく）するための農具は？",
      choices: ["千歯扱き（せんばこき）", "唐箕（とうみ）", "踏車（ふみぐるま）", "備中鍬（びっちゅうぐわ）"],
      correct: 0 // 正解：千歯扱き（脱穀の作業効率を劇的に向上させた展示品）
    }
  ];


// 状態管理
let currentQuestionIndex = 0;
let score = 0;
let userName = "";

document.addEventListener("DOMContentLoaded", () => {
  // DOM要素取得
  const startScreen = document.getElementById("startScreen");
  const quizScreen = document.getElementById("quizScreen");
  const resultScreen = document.getElementById("resultScreen");
  const rankingScreen = document.getElementById("rankingScreen");

  const themeNameStart = document.getElementById("themeNameStart");
  const themeNameRanking = document.getElementById("themeNameRanking");

  const usernameInput = document.getElementById("usernameInput");
  const startBtn = document.getElementById("startBtn");
  const resetDemoBtn = document.getElementById("resetDemoBtn");

  const questionText = document.getElementById("questionText");
  const choicesDiv = document.getElementById("choices");
  const currentIndexSpan = document.getElementById("currentIndex");
  const totalCountSpan = document.getElementById("totalCount");
  const currentScoreSpan = document.getElementById("currentScore");

  const finalScoreSpan = document.getElementById("finalScore");
  const showRankingBtn = document.getElementById("showRankingBtn");
  const rankingBody = document.getElementById("rankingBody");
  const winnerDisplay = document.getElementById("winnerDisplay");
  const winnerName = document.getElementById("winnerName");
  const resetRankingBtn = document.getElementById("resetRankingBtn");

  // 1. テーマ取得・表示
  fetch(`${API_BASE}/theme/current`)
    .then(res => res.text())
    .then(theme => {
      const displayTheme = theme || "テーマ未設定";
      themeNameStart.textContent = displayTheme;
      themeNameRanking.textContent = displayTheme;
    });

  // 2. スタートボタン
  startBtn.addEventListener("click", () => {
    const name = usernameInput.value.trim();
    if (!name) {
      alert("ニックネームを入力してください");
      return;
    }
    userName = name;

    // 画面切り替え
    startScreen.hidden = true;
    quizScreen.hidden = false;

    // クイズ初期化
    currentQuestionIndex = 0;
    score = 0;
    loadQuestion();
  });

  // デモ用リセットボタン
  resetDemoBtn.addEventListener("click", () => {
    if(confirm("ランキングデータをリセットしますか？")) {
      fetch(`${API_BASE}/quiz/reset`, { method: "POST" })
        .then(() => alert("リセットしました"));
    }
  });

  // 3. 問題読み込み処理
  function loadQuestion() {
    // 終了判定
    if (currentQuestionIndex >= TOTAL_QUESTIONS) {
      finishQuiz();
      return;
    }

    const q = DEMO_QUESTIONS[currentQuestionIndex];

    // 表示更新
    currentIndexSpan.textContent = currentQuestionIndex + 1;
    totalCountSpan.textContent = TOTAL_QUESTIONS;
    currentScoreSpan.textContent = score;
    questionText.textContent = q.text;

    // 選択肢生成
    choicesDiv.innerHTML = "";
    q.choices.forEach((choice, index) => {
      const btn = document.createElement("button");
      btn.className = "btn-choice"; // CSSでスタイリングする
      btn.textContent = choice;
      btn.onclick = () => handleAnswer(index === q.correct);
      choicesDiv.appendChild(btn);
    });
  }

  // 4. 回答処理
  function handleAnswer(isCorrect) {
    if (isCorrect) {
      score++;
      alert("正解！"); // デモ用なのでシンプルにalertか、演出を入れても良い
    } else {
      alert("不正解...");
    }
    currentQuestionIndex++;
    loadQuestion();
  }

  // 5. クイズ終了処理 -> サーバーへ送信
  async function finishQuiz() {
    quizScreen.hidden = true;
    resultScreen.hidden = false;
    finalScoreSpan.textContent = score;

    // サーバーにスコア送信 (POST /api/quiz/submit)
    // Body: "名前,点数"
    try {
      await fetch(`${API_BASE}/quiz/submit`, {
        method: "POST",
        headers: { "Content-Type": "text/plain; charset=utf-8" },
        body: `${userName},${score}`
      });
      console.log("スコア送信完了");
    } catch (e) {
      console.error("送信エラー", e);
      alert("スコア送信に失敗しました");
    }
  }

  // 6. ランキング表示処理
  showRankingBtn.addEventListener("click", async () => {
    resultScreen.hidden = true;
    rankingScreen.hidden = false;

    try {
      const res = await fetch(`${API_BASE}/quiz/ranking`);
      const rankingData = await res.json(); // [{"name":"A", "score":3}, ...]

      // テーブル描画
      rankingBody.innerHTML = "";
      rankingData.forEach((user, index) => {
        const tr = document.createElement("tr");

        // 1位には王冠マークなどをつける
        let rankStr = (index + 1) + "位";
        if (index === 0) rankStr = "🥇 " + rankStr;
        else if (index === 1) rankStr = "🥈 " + rankStr;
        else if (index === 2) rankStr = "🥉 " + rankStr;

        tr.innerHTML = `
          <td>${rankStr}</td>
          <td>${user.name}</td>
          <td>${user.score}点</td>
        `;

        // 自分の行を強調
        if (user.name === userName && user.score === score) {
          tr.style.fontWeight = "bold";
          tr.style.backgroundColor = "#fffbe6";
        }

        rankingBody.appendChild(tr);
      });

      // 優勝者表示（1位がいる場合）
      if (rankingData.length > 0) {
        winnerName.textContent = rankingData[0].name;
        winnerDisplay.hidden = false;
      }

    } catch (e) {
      console.error("ランキング取得エラー", e);
      rankingBody.innerHTML = "<tr><td colspan='3'>読み込み失敗</td></tr>";
    }
  });
  if (resetRankingBtn) {
      resetRankingBtn.addEventListener("click", async () => {
        // 誤操作防止の確認（不要ならコメントアウト可）
        if (!confirm("ランキングをリセットして、次の回を始めますか？")) return;

        try {
          await fetch(`${API_BASE}/quiz/reset`, { method: "POST" });
          alert("リセットしました。スタート画面に戻ります。");
          location.reload();
        } catch (e) {
          alert("エラー：サーバーが動いていないようです");
        }
      });
  }

});