// 共通キー
const THEME_KEY     = "quizapp.theme";
const QUESTIONS_KEY = "quizapp.questions";

// テーマを localStorage から読み込み
function loadThemeFromLocal() {
  return localStorage.getItem(THEME_KEY) || "（テーマ未設定）";
}

// 問題リストを読み込み
function loadQuestions() {
  try {
    const json = localStorage.getItem(QUESTIONS_KEY);
    return json ? JSON.parse(json) : [];
  } catch (e) {
    return [];
  }
}

// 問題リストを保存
function saveQuestions(list) {
  localStorage.setItem(QUESTIONS_KEY, JSON.stringify(list));
}

document.addEventListener("DOMContentLoaded", () => {
  const themeSpan         = document.getElementById("createTheme");
  const msg               = document.getElementById("createMessage");
  const btnCreateQuestion = document.getElementById("btnCreateQuestion");
  const btnAdoptQuestion  = document.getElementById("btnAdoptQuestion");
  const btnAdoptChoices   = document.getElementById("btnAdoptChoices");
  const btnSaveDraft      = document.getElementById("btnSaveDraft");

  // テーマ表示
  if (themeSpan) {
    themeSpan.textContent = loadThemeFromLocal();
  }

  let questions = loadQuestions();

  function showMessage(text, type) {
    if (!msg) return;
    msg.textContent = text;
    msg.className = "message " + (type || "info");
  }

  // 「問題作成」ボタン（AI が案を出したという体のデモ）
  if (btnCreateQuestion) {
    btnCreateQuestion.addEventListener("click", () => {
      showMessage("AIが問題文案を生成しました。（デモ）", "info");
    });
  }

  // 「問題文案（Q1） → 採用」ボタン
  if (btnAdoptQuestion) {
    btnAdoptQuestion.addEventListener("click", () => {
      showMessage("この問題文案を採用しました。（デモ）", "success");
    });
  }

  // 「選択肢案 → 採用」ボタン
  if (btnAdoptChoices) {
    btnAdoptChoices.addEventListener("click", () => {
      showMessage("この選択肢案を採用しました。（デモ）", "success");
    });
  }

  // 「下書き保存」ボタン → 実際に 1 問を localStorage に保存
  if (btnSaveDraft) {
    btnSaveDraft.addEventListener("click", () => {
      const theme = loadThemeFromLocal();

      const newQuestion = {
        id: questions.length + 1,
        theme,
        text: "この道具は主にどのような目的で使われていましたか？",
        supplement: "デモ用サンプル問題（手動作成）です。",
        choices: [
          { id: "a", text: "田畑を耕すため" },
          { id: "b", text: "貨幣として使うため" },
          { id: "c", text: "文字を書くため" },
          { id: "d", text: "絵を飾るため" }
        ],
        correctId: "a",
        source: "createQuiz"  // どの画面から作られたかのメモ（デモ用）
      };

      questions.push(newQuestion);
      saveQuestions(questions);

      showMessage(
        `下書きとして ${questions.length} 問目を保存しました。（デモ）`,
        "success"
      );
    });
  }
});