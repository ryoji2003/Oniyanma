package jp.ac.u_aizu.quizapp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThemeController {

    // .envからAPIキーを読み込む
    private static final String OPENAI_API_KEY = loadApiKey();

    private static final int PORT = 8080;
    private static String currentTheme = "未設定";

    // 展示品データリスト
    private static List<Exhibit> allExhibits = new ArrayList<>();

    // 投稿されたクイズを保存するリスト
    private static List<String> postedQuizzes = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        // ★変更点：CSVではなくJSONデータを読み込むメソッドを呼び出し
        loadJsonData();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        System.out.println("Server started: http://localhost:" + PORT);

        // APIキー読み込み確認
        if (OPENAI_API_KEY != null) {
            System.out.println("APIキー読み込み成功: " + OPENAI_API_KEY.substring(0, 5) + "...");
        } else {
            System.err.println("【警告】APIキーが読み込めませんでした。.envを確認してください。");
        }

        // 1. 静的ファイル
        server.createContext("/", new StaticFileHandler("index.html"));

        // 2. テーマ取得
        server.createContext("/api/theme/current", ex -> {
            if ("GET".equals(ex.getRequestMethod())) sendResponse(ex, 200, currentTheme);
            else sendResponse(ex, 405, "Method Not Allowed");
        });

        // 3. テーマ更新
        server.createContext("/api/theme/save", ex -> {
            if ("POST".equals(ex.getRequestMethod())) {
                String newTheme = readRequestBody(ex);
                if (newTheme != null && !newTheme.isEmpty()) {
                    currentTheme = newTheme;
                    sendResponse(ex, 200, "OK");
                } else sendResponse(ex, 400, "Bad Request");
            } else sendResponse(ex, 405, "Method Not Allowed");
        });

        // 4. 展示品検索
        server.createContext("/api/tools/search", ex -> {
            if ("GET".equals(ex.getRequestMethod())) {
                String query = ex.getRequestURI().getQuery();
                String era = query != null && query.contains("era=") ? query.split("era=")[1].split("&")[0] : "";
                era = java.net.URLDecoder.decode(era, StandardCharsets.UTF_8);
                List<Exhibit> filtered = searchExhibits(currentTheme, era);
                sendResponse(ex, 200, convertToJson(filtered));
            } else sendResponse(ex, 405, "Method Not Allowed");
        });

        // 5. AIクイズ生成
        server.createContext("/api/quiz/generate", ex -> {
            if ("POST".equals(ex.getRequestMethod())) {
                String toolName = readRequestBody(ex);
                try {
                    String aiResponse = callOpenAI(currentTheme, toolName);
                    sendResponse(ex, 200, aiResponse);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(ex, 500, "AI Error");
                }
            } else sendResponse(ex, 405, "Method Not Allowed");
        });

        // 6. クイズ投稿（保存）機能
        server.createContext("/api/quiz/post", ex -> {
            if ("POST".equals(ex.getRequestMethod())) {
                String quizJson = readRequestBody(ex);
                postedQuizzes.add(quizJson);
                System.out.println("クイズが投稿されました。現在件数: " + postedQuizzes.size());
                sendResponse(ex, 200, "OK");
            } else sendResponse(ex, 405, "Method Not Allowed");
        });

        // 7. 投稿一覧取得
        server.createContext("/api/quiz/list", ex -> {
            if ("GET".equals(ex.getRequestMethod())) {
                String jsonList = "[" + String.join(",", postedQuizzes) + "]";
                sendResponse(ex, 200, jsonList);
            } else sendResponse(ex, 405, "Method Not Allowed");
        });

        server.setExecutor(null);
        server.start();
    }

    // --- ロジック ---

    // ★追加：JSONデータを読み込むメソッド
    private static void loadJsonData() {
        // 画像にあるパスを指定: src/main/resources/data/data_updated.json
        Path path = Paths.get("src/main/resources/data/data_updated.json");

        if (!Files.exists(path)) {
            System.err.println("警告: データファイルが見つかりません: " + path.toAbsolutePath());
            return;
        }

        try {
            // ファイルの中身をすべて読み込む
            String jsonContent = Files.readString(path, StandardCharsets.UTF_8);

            // 簡易的なJSONパース処理（ライブラリなしで実装）
            // 配列の中のオブジェクト {...} を一つずつ探す
            Pattern pattern = Pattern.compile("\\{([^{}]*)\\}");
            Matcher matcher = pattern.matcher(jsonContent);

            while (matcher.find()) {
                String itemJson = matcher.group(1); // 1つの展示品データ

                // 各フィールドを取り出す
                String id = extractJsonValue(itemJson, "id");
                String name = extractJsonValue(itemJson, "name");
                String era = extractJsonValue(itemJson, "era");
                String theme = extractJsonValue(itemJson, "theme");
                String description = extractJsonValue(itemJson, "description");

                // リストに追加
                if (name != null && !name.isEmpty()) {
                    allExhibits.add(new Exhibit(
                            id == null ? "" : id,
                            name,
                            era == null ? "" : era,
                            theme == null ? "" : theme,
                            description == null ? "" : description
                    ));
                }
            }
            System.out.println("展示品データ(JSON)読み込み完了: " + allExhibits.size() + "件");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // JSONから特定キーの値を取り出すヘルパー関数
    private static String extractJsonValue(String json, String key) {
        // "key": "value" のパターンを探す（改行やエスケープにもある程度対応）
        String regex = "\"" + key + "\"\\s*:\\s*\"(.*?)(?<!\\\\)\"";
        Pattern p = Pattern.compile(regex, Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\\\", "\\");
        }
        return null;
    }

    private static List<Exhibit> searchExhibits(String theme, String era) {
        return allExhibits.stream()
                .filter(e -> e.era.equals(era))
                .filter(e -> e.theme.contains(theme) || theme.equals("未設定"))
                .collect(Collectors.toList());
    }

    private static String convertToJson(List<Exhibit> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Exhibit e = list.get(i);
            sb.append(String.format("{\"name\":\"%s\",\"description\":\"%s\"}",
                    e.name.replace("\"", "\\\""),
                    e.description.replace("\n", "").replace("\"", "\\\"")));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    // --- AI通信 ---
    private static String callOpenAI(String theme, String toolName) throws Exception {
        if (OPENAI_API_KEY == null) throw new IllegalStateException("API Key is not set");

        String prompt = String.format("テーマ:%s,題材:%s,ターゲット:一般観光客。クイズを1問作成しJSON(question,choices,answer,explanation)のみ返してください。", theme, toolName);
        String jsonBody = "{\"model\":\"gpt-3.5-turbo\",\"messages\":[{\"role\":\"system\",\"content\":\"Output JSON only.\"},{\"role\":\"user\",\"content\":\"" + escapeJson(prompt) + "\"}]}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + OPENAI_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return parseContentFromRawJson(response.body());
    }

    private static String escapeJson(String i) { return i.replace("\"", "\\\"").replace("\n", "\\n"); }
    private static String parseContentFromRawJson(String rawJson) {
        try {
            String marker = "\"content\": \"";
            int start = rawJson.indexOf(marker);
            if (start == -1) return "{}";
            start += marker.length();
            int end = start;
            while (true) {
                end = rawJson.indexOf("\"", end);
                if (end == -1) break;
                if (rawJson.charAt(end - 1) != '\\') break;
                end++;
            }
            return rawJson.substring(start, end).replace("\\n", "\n").replace("\\r", "").replace("\\t", " ").replace("\\\"", "\"").replace("\\\\", "\\");
        } catch (Exception e) { return "{}"; }
    }

    // .env読み込み用メソッド
    private static String loadApiKey() {
        Path path = Paths.get(".env");
        try {
            if (!Files.exists(path)) {
                System.err.println("エラー: .envファイルが見つかりません: " + path.toAbsolutePath());
                return null;
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.startsWith("OPENAI_API_KEY=")) {
                    String key = line.split("=", 2)[1].trim();
                    if (key.startsWith("\"") && key.endsWith("\"")) {
                        key = key.substring(1, key.length() - 1);
                    }
                    return key;
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        return null;
    }

    static class Exhibit {
        String id, name, era, theme, description;
        public Exhibit(String i, String n, String e, String t, String d) { id=i; name=n; era=e; theme=t; description=d; }
    }
    static class StaticFileHandler implements HttpHandler {
        private final String f; public StaticFileHandler(String f) { this.f = f; }
        public void handle(HttpExchange e) throws IOException {
            Path p = Paths.get("src/main/resources/static", f);
            if(Files.exists(p)){ byte[] b=Files.readAllBytes(p); e.sendResponseHeaders(200, b.length); try(OutputStream o=e.getResponseBody()){o.write(b);} } else { e.sendResponseHeaders(404,0); e.close(); }
        }
    }
    private static void sendResponse(HttpExchange e, int s, String r) throws IOException { byte[] b=r.getBytes(StandardCharsets.UTF_8); e.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8"); e.sendResponseHeaders(s, b.length); try(OutputStream o=e.getResponseBody()){o.write(b);} }
    private static String readRequestBody(HttpExchange e) throws IOException { InputStream i=e.getRequestBody(); Scanner s=new Scanner(i, StandardCharsets.UTF_8.name()); return s.useDelimiter("\\A").hasNext()?s.next():""; }
}