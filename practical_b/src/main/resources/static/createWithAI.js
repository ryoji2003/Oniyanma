const THEME_KEY     = "quizapp.theme";
const QUESTIONS_KEY = "quizapp.questions";

// テーマを localStorage から読み込み (共通関数)
function loadThemeFromLocal() {
  return localStorage.getItem(THEME_KEY) || "（テーマ未設定）";
}

// 問題リストを読み込み (共通関数)
function loadQuestions() {
  try {
    const json = localStorage.getItem(QUESTIONS_KEY);
    return json ? JSON.parse(json) : [];
  } catch {
    return [];
  }
}

// 問題リストを保存 (共通関数)
function saveQuestions(list) {
  localStorage.setItem(QUESTIONS_KEY, JSON.stringify(list));
}

document.addEventListener("DOMContentLoaded", () => {
    const themeSpan         = document.getElementById("aiTheme");
    const msg               = document.getElementById("aiMessage");

    // DOM要素取得
    const chatLog           = document.getElementById("chatLog");
    const materialButtons   = document.querySelectorAll(".material-select");

    // 各ステップのコンテナ
    const step2UserContainer  = document.getElementById("step2UserSelection");
    const step3CutProposal    = document.getElementById("step3CutProposal");
    const step4QuizProposal   = document.getElementById("step4QuizProposal");
    const step5Choices        = document.getElementById("step5Choices");

    const btnAiAdoptQ       = document.getElementById("btnAIAdoptQuestion");
    const btnAiAdoptChoices = document.getElementById("btnAiAdoptChoices");

    // HTMLから直接取得する要素
    const userCutSelectionLog = document.getElementById("userCutSelectionLog");
    const quizProposalText    = document.getElementById("quizProposalText"); // AIの応答部分 (div class="chat-message ai") を想定

    // 状態管理
    let questions = loadQuestions();
    let selectedMaterial = ""; // 選択された資料名
    let selectedCut = "";      // 選択された切り口

    if (themeSpan) {
        themeSpan.textContent = loadThemeFromLocal();
    }

    // 初期化: 各ステップを非表示にする
    step2UserContainer.hidden = true;
    step3CutProposal.hidden = true;
    step4QuizProposal.hidden = true;
    step5Choices.hidden = true;

    function showMessage(text, type) {
        if (!msg) return;
        msg.textContent = text;
        msg.className = "message " + (type || "info");
    }

    // --- ステップ1: 資料選択の処理 ---
    materialButtons.forEach(button => {
        button.addEventListener("click", (e) => {
            selectedMaterial = e.target.getAttribute("data-material");

            // 1. ユーザーの選択ログを動的に挿入
            step2UserContainer.innerHTML = `<div class="chat-message user">参加者: **「${selectedMaterial}」**を選びます。</div>`;
            step2UserContainer.hidden = false;

            // 2. AIの切り口提案のテキストを更新 (選択した資料名を反映)
            const materialName = selectedMaterial.substring(0, selectedMaterial.indexOf('（') > 0 ? selectedMaterial.indexOf('（') : selectedMaterial.length);

            step3CutProposal.innerHTML = `
                <div class="chat-message ai" id="cutProposalText">
                  AI: 選択された**「${materialName}」**について問題を作成しましょう。<br />
                  どの切り口にしますか？<br />
                  <button id="selectCut1" class="btn-secondary btn-small">1. 資料の用途や機能を問う</button><br>
                  <button id="selectCut2" class="btn-secondary btn-small">2. 資料の特徴や定義を問う</button><br>
                  <button id="selectCut3" class="btn-secondary btn-small">3. 資料の時代背景や歴史を問う</button>
                </div>
            `;

            // 3. 切り口ボタンを有効化＆イベントを再設定
            step3CutProposal.hidden = false;

            // DOMが更新されたため、ボタンを再取得してイベントを設定する
            const btnCut1 = document.getElementById("selectCut1");
            const btnCut2 = document.getElementById("selectCut2");
            const btnCut3 = document.getElementById("selectCut3");

            if (btnCut1) btnCut1.addEventListener("click", () => handleCutSelection(1, btnCut1.textContent));
            if (btnCut2) btnCut2.addEventListener("click", () => handleCutSelection(2, btnCut2.textContent));
            if (btnCut3) btnCut3.addEventListener("click", () => handleCutSelection(3, btnCut3.textContent));

            // 4. 資料選択ボタンを全て無効化
            materialButtons.forEach(b => b.disabled = true);

            chatLog.scrollTop = chatLog.scrollHeight;
        });
    });

    // --- ステップ2: 切り口選択の処理 ---
    function handleCutSelection(cutNumber, cutText) {
        selectedCut = cutText;
        const rawCutText = cutText.replace(`${cutNumber}. `, ''); // 番号を削除

        // 1. ユーザーの選択ログを動的に挿入
        // #userCutSelectionLog は HTML 側で div class="chat-message user" のコンテナを想定
        userCutSelectionLog.innerHTML = `参加者: ${cutNumber}番の「${rawCutText}」を選びます。`;

        // 2. 問題案の提示コンテナのログを更新 (AI応答のinnerHTMLを更新)
        // #quizProposalText は HTML 側で div class="chat-message ai" のコンテナを想定
        quizProposalText.innerHTML = `
            AI: 「${rawCutText}」を問う切り口ですね。承知しました。問題文案を提示します。<br />
            Q1:
            福島県立博物館に収蔵されている農具の中には、購入した年月日や金額、店名などが墨で書かれているものがあります。これらは何と呼ばれていますか？<br />
            <button id="btnAIAdoptQuestion" class="btn-primary">採用</button>
            <button class="btn-secondary">修正依頼</button>
        `;

        // 3. 全ての切り口ボタンを無効化
        const currentCutButtons = step3CutProposal.querySelectorAll('button');
        currentCutButtons.forEach(b => b.disabled = true);

        // 4. ステップ4 (問題案) を表示
        step4QuizProposal.hidden = false;

        chatLog.scrollTop = chatLog.scrollHeight;
    }

    // --- ステップ3: 問題採用ボタンの処理 ---
    if (btnAiAdoptQ) {
        btnAiAdoptQ.addEventListener("click", () => {
            step5Choices.hidden = false;
            showMessage("この問題文案を採用しました。（デモ）", "success");
            chatLog.scrollTop = chatLog.scrollHeight;
        });
    }

    // --- ステップ4: 選択肢採用ボタンの処理 (保存) ---
    if (btnAiAdoptChoices) {
        btnAiAdoptChoices.addEventListener("click", () => {
            const theme = loadThemeFromLocal();

            // どの資料、どの切り口が選ばれても、紀年銘民具の問題を保存
            const newQuestion = {
                id: questions.length + 1,
                theme,
                text: "福島県立博物館に収蔵されている農具の中には、購入した年月日や金額、店名などが墨で書かれているものがあります。これらは何と呼ばれていますか？",
                supplement: `AIチャットで作成された問題（デモ）。選択資料: ${selectedMaterial}, 選択切り口: ${selectedCut}`,
                choices: [
                    { id: "a", text: "紀年銘民具" },
                    { id: "b", text: "墨書土器" },
                    { id: "c", text: "履歴付き農具" },
                    { id: "d", text: "覚書道具" }
                ],
                correctId: "a",
                source: "createWithAI"
            };

            questions.push(newQuestion);
            saveQuestions(questions);

            showMessage(
                `AI案の問題を ${questions.length} 問目として下書き保存しました。`,
                "success"
            );
        });
    }
});
