import io.github.cdimascio.dotenv.Dotenv;

public class Main {
    public static void main(String[] args) {
        // .envファイルを読み込む
        Dotenv dotenv = Dotenv.load();

        // 値を取得する
        String apiKey = dotenv.get("OPENAI_API_KEY");
        String dbUrl = dotenv.get("DB_URL");

        System.out.println("API Key loaded: " + apiKey);
    }
}
