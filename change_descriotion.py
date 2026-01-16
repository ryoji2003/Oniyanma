import json
import os
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser

# .envファイルから環境変数を読み込む
load_dotenv()

# 設定gti
INPUT_FILE = "data.json"
OUTPUT_FILE = "data_updated.json"

def generate_descriptions():
    # 1. APIキーの確認
    if not os.getenv("OPENAI_API_KEY"):
        print("エラー: .envファイルに OPENAI_API_KEY が設定されていません。")
        return

    # 2. データの読み込み
    try:
        with open(INPUT_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
    except FileNotFoundError:
        print(f"エラー: {INPUT_FILE} が見つかりません。")
        return

    # 3. LangChainの設定
    # より詳細な知識が必要なため、可能であれば gpt-4o などの高性能モデル推奨
    llm = ChatOpenAI(model="gpt-3.5-turbo", temperature=0.3) # ハルシネーション抑制のためtemperatureを低めに設定

    # --- プロンプトの更新部分 ---
    prompt = ChatPromptTemplate.from_template(
        """
        あなたは知識豊富な博物館の主任学芸員です。
        以下の展示品データ（名前、時代、テーマ）に基づき、来館者が深い学びを得られるような、信頼性の高い詳細な解説文（description）を作成してください。

        [入力情報]
        - 名前: {name}
        - 時代: {era}
        - テーマ: {theme}

        [記述のガイドライン]
        1. **基本情報**: それが何であるか、主な用途は何かを明確に述べること。
        2. **コア情報**: 材質、製法の特徴、その時代における社会的・文化的意義、技術的な進化の文脈など、専門的な知見を含めること。
        3. **情報量**: 読者が十分に理解できるよう、簡潔すぎず、充実した内容（100文字〜300文字程度）にすること。

        [厳守事項]
        - **不確実な情報の排除**: 歴史的事実として断定できない推測や、架空の情報は絶対に入れないこと。確信が持てない詳細は記述しないこと。
        - 文体は「〜である。」「〜だ。」といった、学術的かつ客観的な常体を使用すること。

        [出力]
        解説文のみを出力してください。
        """
    )
    # ---------------------------

    # チェーンの作成
    chain = prompt | llm | StrOutputParser()

    print(f"処理開始: 全 {len(data)} 件のデータを更新します（詳細モード）...")

    # 4. ループ処理
    updated_count = 0
    for item in data:
        name = item.get("name")
        era = item.get("era")
        theme = item.get("theme")
        
        # 必要な情報が揃っている場合のみ実行
        if name and era and theme:
            try:
                print(f"[{updated_count + 1}/{len(data)}] 生成中: {name} ...", end="", flush=True)
                
                # AIによる生成実行
                new_description = chain.invoke({
                    "name": name,
                    "era": era,
                    "theme": theme
                })
                
                # データを更新
                item["description"] = new_description.strip()
                print(" 完了")
                updated_count += 1
                
            except Exception as e:
                print(f" エラー: {e}")
        else:
            print(f"スキップ: {name} (情報不足)")

    # 5. 結果の保存
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print(f"\nすべての処理が完了しました。")
    print(f"更新されたファイル: {OUTPUT_FILE}")

if __name__ == "__main__":
    generate_descriptions()