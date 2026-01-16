// テーマをブラウザにも残しておくキー
const THEME_KEY = "quizapp.theme";   // 以前使っていたキー名に合わせています

document.addEventListener("DOMContentLoaded", () => {
  const form          = document.getElementById("themeForm");
  const input         = document.getElementById("themeInput");
  const lastThemeSpan = document.getElementById("lastTheme");
  const resultP       = document.getElementById("result");
  const nextBtn       = document.getElementById("nextBtn");

  // 画面表示時に、まずサーバ → ダメなら localStorage からテーマを読み込む
  loadThemeFromServer();

  // 「保存」ボタン（フォーム送信）
  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const theme = input.value.trim();
    if (!theme) {
      resultP.textContent = "テーマを入力してください。";
      resultP.className = "message error";
      return;
    }

    // まずブラウザの localStorage にも保存
    localStorage.setItem(THEME_KEY, theme);

    // サーバへ保存
    try {
      const res = await fetch("http://localhost:8080/api/theme/save", {
        method: "POST",
        headers: {
          "Content-Type": "text/plain; charset=utf-8",
        },
        body: theme,
      });

      if (!res.ok) {
        throw new Error("HTTP " + res.status);
      }

      resultP.textContent = "テーマを保存しました。";
      resultP.className = "message success";
      lastThemeSpan.textContent = theme;
    } catch (err) {
      console.error(err);
      resultP.textContent =
        "ローカルには保存しましたが、サーバへの保存に失敗しました。";
      resultP.className = "message error";
    }
  });

  // 「次へ（問題作成へ）」 → 今は画面遷移しないで説明だけ出す
  if (nextBtn) {
    nextBtn.addEventListener("click", () => {
      alert("テーマは保存済みです。この画面を閉じてかまいません。");
    });
  }

  // ---------- ヘルパー関数 ----------

  async function loadThemeFromServer() {
    // 1. まずサーバから取ってくる（多端末共有用）
    try {
      const res = await fetch("http://localhost:8080/api/theme/current");
      if (res.ok) {
        const theme = (await res.text()).trim();
        if (theme) {
          lastThemeSpan.textContent = theme;
          input.value = theme;
          localStorage.setItem(THEME_KEY, theme);
          return; // 取れたらそこで終了
        }
      }
    } catch (e) {
      console.warn("サーバからテーマを取得できませんでした。localStorage を使用します。");
    }

    // 2. サーバから取れなかったときは、localStorage を見る
    const local = localStorage.getItem(THEME_KEY);
    if (local) {
      lastThemeSpan.textContent = local;
      input.value = local;
    }
  }
});
