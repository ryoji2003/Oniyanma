import json
import os
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser

# .envファイルから環境変数を読み込む
load_dotenv()

# 設定
INPUT_FILE = "data.json"
OUTPUT_FILE = "data_updated.json"  # 上書きを防ぐため別名で保存します

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

    # 3. LangChainの設定 (モデルは必要に応じて gpt-4o などに変更してください)
    llm = ChatOpenAI(model="gpt-3.5-turbo", temperature=0.7)

    # プロンプトの定義
    # name, era, theme を元に description を生成する指示
    prompt = ChatPromptTemplate.from_template(
        """
        あなたは博物館の学芸員です。以下の情報に基づいて、展示品のための魅力的で教育的な説明文（description）を作成してください。
        
        情報:
        - 名前: {name}
        - 時代: {era}
        - テーマ: {theme}
        
        制約事項:
        - 日本語で記述すること。
        - 1文〜2文程度の簡潔な文章にすること。
        - その時代の背景や用途が伝わるようにすること。
        
        出力（説明文のみ）:
        """
    )

    # チェーンの作成
    chain = prompt | llm | StrOutputParser()

    print(f"処理開始: 全 {len(data)} 件のデータを更新します...")

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