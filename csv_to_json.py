import csv
import json

# ---------------------------------------------------------
# 設定項目
# ---------------------------------------------------------
INPUT_CSV_FILE = "/Users/mirei/IdeaProjects/practical_b/src/main/resources/data/exhibits.csv"   # 変換元のCSVファイル名
OUTPUT_JSON_FILE = "data.json" # 出力先のJSONファイル名

def csv_to_json(csv_path, json_path):
    # データを格納するリスト
    data = []

    try:
        # 1. CSVファイルを読み込む
        with open(csv_path, mode='r', encoding='utf-8') as csv_f:
            # DictReaderを使うと、ヘッダー行をキーとした辞書として読み込めます
            csv_reader = csv.DictReader(csv_f)
            
            for row in csv_reader:
                data.append(row)

        # 2. JSONファイルに書き込む
        with open(json_path, mode='w', encoding='utf-8') as json_f:
            # ensure_ascii=False で日本語の文字化けを防ぎます
            # indent=2 で人間が見やすいように改行とインデントを入れます
            json.dump(data, json_f, ensure_ascii=False, indent=2)

        print(f"変換成功: '{csv_path}' から '{json_path}' を作成しました。")

    except FileNotFoundError:
        print(f"エラー: ファイル '{csv_path}' が見つかりません。")
    except Exception as e:
        print(f"エラーが発生しました: {e}")

if __name__ == "__main__":
    csv_to_json(INPUT_CSV_FILE, OUTPUT_JSON_FILE)
