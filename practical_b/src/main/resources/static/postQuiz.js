document.addEventListener("DOMContentLoaded", () => {
  const draftSelect = document.getElementById("draftSelect");

  // LocalStorage に保存された下書き一覧を読み込むキーを統一
  const drafts = JSON.parse(localStorage.getItem("quizapp.questions") || "[]");

  // セレクトボックスをクリア
  draftSelect.innerHTML = "";

  // 下書きが 1 件もない場合のオプションを追加
  if (drafts.length === 0) {
      const option = document.createElement("option");
      option.textContent = "下書きはまだありません";
      draftSelect.appendChild(option);
      return; // 処理を終了
  }

  // 選択肢を追加
  drafts.forEach((item, index) => {
    const option = document.createElement("option");
    option.value = item.id; // 問題IDを下書きの識別子とする
    // 問題文の最初の数文字をオプションテキストに表示
    const previewText = item.text.substring(0, 20) + (item.text.length > 20 ? '...' : '');
    option.textContent = `下書き ${item.id}: ${previewText} (作成元: ${item.source})`;
    draftSelect.appendChild(option);
  });
});