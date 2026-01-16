package jp.ac.u_aizu.quizapp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Quiz Festival Server - Museum Event Version
 * Supports real-time quiz with WebSocket for host control and participant synchronization
 */
public class FestivalServer {

    private static final int HTTP_PORT = 8080;
    private static final int WS_PORT = 8081;

    // Quiz Session State
    private static QuizSession currentSession = new QuizSession();

    // Connected WebSocket clients
    private static final Map<String, WebSocketClient> clients = new ConcurrentHashMap<>();
    private static final Map<String, WebSocketClient> hostClients = new ConcurrentHashMap<>();

    // Question bank (loaded from posted quizzes)
    private static List<Question> questionBank = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        // Load existing quiz data
        loadQuestionBank();

        // Start HTTP Server
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        setupHttpEndpoints(httpServer);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        System.out.println("HTTP Server started: http://localhost:" + HTTP_PORT);

        // Start WebSocket Server
        new Thread(() -> startWebSocketServer()).start();
        System.out.println("WebSocket Server started: ws://localhost:" + WS_PORT);

        System.out.println("\n=== Quiz Festival Server Ready ===");
        System.out.println("Host URL: http://localhost:" + HTTP_PORT + "/host.html");
        System.out.println("Participant URL: http://localhost:" + HTTP_PORT + "/play.html");
    }

    // ========================
    // HTTP Endpoints
    // ========================

    private static void setupHttpEndpoints(HttpServer server) {
        // Static files
        server.createContext("/", new StaticFileHandler());

        // Join session
        server.createContext("/api/join", ex -> {
            if ("POST".equals(ex.getRequestMethod())) {
                String body = readBody(ex);
                String nickname = extractJsonField(body, "nickname");
                if (nickname == null || nickname.isEmpty()) {
                    nickname = "Player" + (currentSession.players.size() + 1);
                }

                Player player = new Player(generatePlayerId(), nickname);
                currentSession.players.put(player.id, player);

                String response = String.format("{\"playerId\":\"%s\",\"nickname\":\"%s\",\"status\":\"%s\"}",
                        player.id, player.nickname, currentSession.state.name());
                sendJson(ex, 200, response);

                // Notify hosts about new player
                broadcastToHosts("{\"type\":\"player.joined\",\"count\":" + currentSession.players.size() +
                        ",\"nickname\":\"" + escapeJson(player.nickname) + "\"}");
            } else {
                sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        });

        // Submit answer
        server.createContext("/api/answer", ex -> {
            if ("POST".equals(ex.getRequestMethod())) {
                String body = readBody(ex);
                String playerId = extractJsonField(body, "playerId");
                String choiceStr = extractJsonField(body, "choice");
                String questionIdStr = extractJsonField(body, "questionId");

                if (currentSession.state != QuizState.QUESTION_ACTIVE) {
                    sendJson(ex, 400, "{\"error\":\"No active question\"}");
                    return;
                }

                Player player = currentSession.players.get(playerId);
                if (player == null) {
                    sendJson(ex, 404, "{\"error\":\"Player not found\"}");
                    return;
                }

                int questionId = Integer.parseInt(questionIdStr);
                int choice = Integer.parseInt(choiceStr);

                // Record answer
                Answer answer = new Answer(playerId, questionId, choice, System.currentTimeMillis());
                currentSession.answers.computeIfAbsent(questionId, k -> new ConcurrentHashMap<>())
                        .put(playerId, answer);

                // Check if correct
                Question currentQ = currentSession.getCurrentQuestion();
                boolean correct = currentQ != null && currentQ.correctIndex == choice;
                if (correct) {
                    player.score++;
                }

                sendJson(ex, 200, "{\"received\":true}");

                // Update hosts with answer count
                int answeredCount = currentSession.answers.getOrDefault(questionId, new ConcurrentHashMap<>()).size();
                broadcastToHosts("{\"type\":\"answer.received\",\"count\":" + answeredCount +
                        ",\"total\":" + currentSession.players.size() + "}");
            } else {
                sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        });

        // Get results
        server.createContext("/api/result", ex -> {
            if ("GET".equals(ex.getRequestMethod())) {
                String query = ex.getRequestURI().getQuery();
                String playerId = null;
                if (query != null && query.contains("playerId=")) {
                    playerId = query.split("playerId=")[1].split("&")[0];
                }

                List<Player> ranking = new ArrayList<>(currentSession.players.values());
                ranking.sort((a, b) -> {
                    if (b.score != a.score) return b.score - a.score;
                    return Long.compare(a.joinedAt, b.joinedAt);
                });

                StringBuilder sb = new StringBuilder("{");

                // Personal score
                if (playerId != null) {
                    Player p = currentSession.players.get(playerId);
                    if (p != null) {
                        int rank = ranking.indexOf(p) + 1;
                        sb.append("\"personal\":{\"nickname\":\"").append(escapeJson(p.nickname))
                          .append("\",\"score\":").append(p.score)
                          .append(",\"rank\":").append(rank).append("},");
                    }
                }

                // Top 3
                sb.append("\"top3\":[");
                for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                    Player p = ranking.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{\"rank\":").append(i + 1)
                      .append(",\"nickname\":\"").append(escapeJson(p.nickname))
                      .append("\",\"score\":").append(p.score).append("}");
                }
                sb.append("],");

                // Full ranking
                sb.append("\"ranking\":[");
                for (int i = 0; i < ranking.size(); i++) {
                    Player p = ranking.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{\"rank\":").append(i + 1)
                      .append(",\"nickname\":\"").append(escapeJson(p.nickname))
                      .append("\",\"score\":").append(p.score).append("}");
                }
                sb.append("]}");

                sendJson(ex, 200, sb.toString());
            } else {
                sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        });

        // Host: Get session status
        server.createContext("/api/session/status", ex -> {
            if ("GET".equals(ex.getRequestMethod())) {
                StringBuilder sb = new StringBuilder("{");
                sb.append("\"state\":\"").append(currentSession.state.name()).append("\",");
                sb.append("\"playerCount\":").append(currentSession.players.size()).append(",");
                sb.append("\"currentQuestionIndex\":").append(currentSession.currentQuestionIndex).append(",");
                sb.append("\"totalQuestions\":").append(currentSession.questions.size()).append(",");
                if (currentSession.getCurrentQuestion() != null) {
                    Question q = currentSession.getCurrentQuestion();
                    sb.append("\"currentQuestion\":{");
                    sb.append("\"id\":").append(q.id).append(",");
                    sb.append("\"text\":\"").append(escapeJson(q.text)).append("\",");
                    sb.append("\"choices\":[");
                    for (int i = 0; i < q.choices.length; i++) {
                        if (i > 0) sb.append(",");
                        sb.append("\"").append(escapeJson(q.choices[i])).append("\"");
                    }
                    sb.append("]},");
                }
                int answered = 0;
                if (currentSession.state == QuizState.QUESTION_ACTIVE && currentSession.currentQuestionIndex >= 0) {
                    answered = currentSession.answers.getOrDefault(
                            currentSession.currentQuestionIndex, new ConcurrentHashMap<>()).size();
                }
                sb.append("\"answeredCount\":").append(answered);
                sb.append("}");
                sendJson(ex, 200, sb.toString());
            } else {
                sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        });

        // Host: Reset session
        server.createContext("/api/session/reset", ex -> {
            if ("POST".equals(ex.getRequestMethod())) {
                currentSession = new QuizSession();
                loadQuestionBank();
                broadcastToAll("{\"type\":\"session.reset\"}");
                sendJson(ex, 200, "{\"success\":true}");
            } else {
                sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        });

        // Get question bank
        server.createContext("/api/questions", ex -> {
            if ("GET".equals(ex.getRequestMethod())) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < questionBank.size(); i++) {
                    Question q = questionBank.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{\"id\":").append(q.id)
                      .append(",\"text\":\"").append(escapeJson(q.text))
                      .append("\",\"choices\":[");
                    for (int j = 0; j < q.choices.length; j++) {
                        if (j > 0) sb.append(",");
                        sb.append("\"").append(escapeJson(q.choices[j])).append("\"");
                    }
                    sb.append("],\"correctIndex\":").append(q.correctIndex).append("}");
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
            } else {
                sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        });

        // Host: Set questions for session
        server.createContext("/api/session/questions", ex -> {
            if ("POST".equals(ex.getRequestMethod())) {
                String body = readBody(ex);
                // Parse question IDs from body
                List<Integer> ids = parseIntArray(body);
                currentSession.questions.clear();
                for (int id : ids) {
                    for (Question q : questionBank) {
                        if (q.id == id) {
                            currentSession.questions.add(q);
                            break;
                        }
                    }
                }
                sendJson(ex, 200, "{\"success\":true,\"count\":" + currentSession.questions.size() + "}");
            } else {
                sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        });
    }

    // ========================
    // WebSocket Server
    // ========================

    private static void startWebSocketServer() {
        try (ServerSocket serverSocket = new ServerSocket(WS_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleWebSocketConnection(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleWebSocketConnection(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream out = socket.getOutputStream();

            // Read HTTP upgrade request
            StringBuilder requestBuilder = new StringBuilder();
            String line;
            String wsKey = null;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                requestBuilder.append(line).append("\r\n");
                if (line.startsWith("Sec-WebSocket-Key:")) {
                    wsKey = line.substring(19).trim();
                }
            }

            if (wsKey == null) {
                socket.close();
                return;
            }

            // Calculate accept key
            String acceptKey = calculateWebSocketAccept(wsKey);

            // Send handshake response
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();

            String clientId = UUID.randomUUID().toString();
            WebSocketClient client = new WebSocketClient(clientId, socket, out);
            clients.put(clientId, client);

            System.out.println("WebSocket client connected: " + clientId);

            // Send welcome message
            sendWebSocketMessage(client, "{\"type\":\"connected\",\"clientId\":\"" + clientId +
                    "\",\"state\":\"" + currentSession.state.name() + "\"}");

            // Read messages
            InputStream in = socket.getInputStream();
            while (!socket.isClosed()) {
                String message = readWebSocketMessage(in);
                if (message == null) break;

                handleWebSocketMessage(client, message);
            }

            clients.remove(clientId);
            hostClients.remove(clientId);
            System.out.println("WebSocket client disconnected: " + clientId);

        } catch (Exception e) {
            // Client disconnected
        }
    }

    private static void handleWebSocketMessage(WebSocketClient client, String message) {
        try {
            String type = extractJsonField(message, "type");
            if (type == null) return;

            switch (type) {
                case "host.register":
                    hostClients.put(client.id, client);
                    sendWebSocketMessage(client, "{\"type\":\"host.registered\",\"playerCount\":" +
                            currentSession.players.size() + "}");
                    break;

                case "host.startQuiz":
                    if (currentSession.questions.isEmpty()) {
                        // Use all questions from bank if none selected
                        currentSession.questions.addAll(questionBank);
                    }
                    if (currentSession.questions.isEmpty()) {
                        sendWebSocketMessage(client, "{\"type\":\"error\",\"message\":\"No questions available\"}");
                        return;
                    }
                    currentSession.state = QuizState.WAIT_JOIN;
                    broadcastToAll("{\"type\":\"quiz.starting\"}");
                    break;

                case "host.openJoin":
                    currentSession.state = QuizState.WAIT_JOIN;
                    broadcastToAll("{\"type\":\"session.waitingForPlayers\"}");
                    break;

                case "host.nextQuestion":
                    advanceQuestion();
                    break;

                case "host.endQuestion":
                    endCurrentQuestion();
                    break;

                case "host.showResult":
                    currentSession.state = QuizState.RESULT;
                    broadcastToAll("{\"type\":\"quiz.finish\"}");
                    break;

                case "host.endQuiz":
                    currentSession.state = QuizState.END;
                    broadcastToAll("{\"type\":\"quiz.ended\"}");
                    break;

                case "ping":
                    sendWebSocketMessage(client, "{\"type\":\"pong\"}");
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void advanceQuestion() {
        currentSession.currentQuestionIndex++;
        if (currentSession.currentQuestionIndex >= currentSession.questions.size()) {
            currentSession.state = QuizState.RESULT;
            broadcastToAll("{\"type\":\"quiz.finish\"}");
            return;
        }

        currentSession.state = QuizState.QUESTION_ACTIVE;
        currentSession.questionStartTime = System.currentTimeMillis();

        Question q = currentSession.getCurrentQuestion();
        StringBuilder sb = new StringBuilder("{\"type\":\"question.start\",");
        sb.append("\"questionId\":").append(currentSession.currentQuestionIndex).append(",");
        sb.append("\"questionNumber\":").append(currentSession.currentQuestionIndex + 1).append(",");
        sb.append("\"totalQuestions\":").append(currentSession.questions.size()).append(",");
        sb.append("\"text\":\"").append(escapeJson(q.text)).append("\",");
        sb.append("\"choices\":[");
        for (int i = 0; i < q.choices.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(q.choices[i])).append("\"");
        }
        sb.append("],\"timeLimit\":60}");

        broadcastToAll(sb.toString());

        // Schedule auto-end after 60 seconds
        new Thread(() -> {
            try {
                Thread.sleep(60000);
                if (currentSession.state == QuizState.QUESTION_ACTIVE &&
                    currentSession.getCurrentQuestion() == q) {
                    endCurrentQuestion();
                }
            } catch (InterruptedException e) {
                // Timer cancelled
            }
        }).start();
    }

    private static void endCurrentQuestion() {
        if (currentSession.state != QuizState.QUESTION_ACTIVE) return;

        currentSession.state = QuizState.QUESTION_CLOSED;
        Question q = currentSession.getCurrentQuestion();

        StringBuilder sb = new StringBuilder("{\"type\":\"question.end\",");
        sb.append("\"questionId\":").append(currentSession.currentQuestionIndex).append(",");
        sb.append("\"correctIndex\":").append(q.correctIndex).append(",");
        sb.append("\"correctAnswer\":\"").append(escapeJson(q.choices[q.correctIndex])).append("\"");
        if (q.explanation != null) {
            sb.append(",\"explanation\":\"").append(escapeJson(q.explanation)).append("\"");
        }
        sb.append("}");

        broadcastToAll(sb.toString());
    }

    // ========================
    // WebSocket Utilities
    // ========================

    private static String calculateWebSocketAccept(String key) throws Exception {
        String magic = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(magic.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    private static String readWebSocketMessage(InputStream in) throws IOException {
        int firstByte = in.read();
        if (firstByte == -1) return null;

        int secondByte = in.read();
        if (secondByte == -1) return null;

        boolean masked = (secondByte & 0x80) != 0;
        int payloadLength = secondByte & 0x7F;

        if (payloadLength == 126) {
            payloadLength = (in.read() << 8) | in.read();
        } else if (payloadLength == 127) {
            // Skip for simplicity - we won't have messages this large
            return null;
        }

        byte[] maskKey = null;
        if (masked) {
            maskKey = new byte[4];
            in.read(maskKey);
        }

        byte[] payload = new byte[payloadLength];
        int read = 0;
        while (read < payloadLength) {
            int r = in.read(payload, read, payloadLength - read);
            if (r == -1) return null;
            read += r;
        }

        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }

        return new String(payload, StandardCharsets.UTF_8);
    }

    private static void sendWebSocketMessage(WebSocketClient client, String message) {
        try {
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream frame = new ByteArrayOutputStream();

            frame.write(0x81); // Text frame, FIN bit set

            if (payload.length < 126) {
                frame.write(payload.length);
            } else if (payload.length < 65536) {
                frame.write(126);
                frame.write((payload.length >> 8) & 0xFF);
                frame.write(payload.length & 0xFF);
            } else {
                frame.write(127);
                for (int i = 7; i >= 0; i--) {
                    frame.write((payload.length >> (8 * i)) & 0xFF);
                }
            }

            frame.write(payload);

            synchronized (client.out) {
                client.out.write(frame.toByteArray());
                client.out.flush();
            }
        } catch (IOException e) {
            clients.remove(client.id);
            hostClients.remove(client.id);
        }
    }

    private static void broadcastToAll(String message) {
        for (WebSocketClient client : clients.values()) {
            sendWebSocketMessage(client, message);
        }
    }

    private static void broadcastToHosts(String message) {
        for (WebSocketClient client : hostClients.values()) {
            sendWebSocketMessage(client, message);
        }
    }

    // ========================
    // Data Loading
    // ========================

    private static void loadQuestionBank() {
        questionBank.clear();

        // Load from posted quizzes file if exists
        Path quizPath = Paths.get("src/main/resources/data/quizzes.json");
        if (Files.exists(quizPath)) {
            try {
                String content = Files.readString(quizPath);
                // Parse quizzes
                List<Question> loaded = parseQuestions(content);
                questionBank.addAll(loaded);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Add some default questions if empty
        if (questionBank.isEmpty()) {
            questionBank.add(new Question(1,
                    "縄文時代の特徴的な土器の模様は何と呼ばれますか？",
                    new String[]{"縄目模様", "弥生模様", "古墳模様", "飛鳥模様"},
                    0, "縄文土器の名前の由来は、縄を転がして付けた模様です。"));

            questionBank.add(new Question(2,
                    "打製石器の主な用途は何ですか？",
                    new String[]{"狩猟", "農耕", "儀式", "装飾"},
                    0, "打製石器は主に動物を狩るために使われました。"));

            questionBank.add(new Question(3,
                    "弥生時代に大陸から伝わった重要な技術は何ですか？",
                    new String[]{"稲作", "製鉄", "製紙", "印刷"},
                    0, "弥生時代には稲作技術が朝鮮半島から伝わりました。"));

            questionBank.add(new Question(4,
                    "古墳時代の埴輪の主な役割は何ですか？",
                    new String[]{"副葬品", "日用品", "貨幣", "武器"},
                    0, "埴輪は古墳に置かれた副葬品でした。"));

            questionBank.add(new Question(5,
                    "会津地方で有名な伝統工芸品は何ですか？",
                    new String[]{"会津漆器", "有田焼", "南部鉄器", "西陣織"},
                    0, "会津は漆器の産地として有名です。"));
        }

        System.out.println("Question bank loaded: " + questionBank.size() + " questions");
    }

    private static List<Question> parseQuestions(String json) {
        List<Question> questions = new ArrayList<>();
        // Simple JSON array parsing
        int id = 100;
        int start = 0;
        while ((start = json.indexOf("{", start)) != -1) {
            int end = json.indexOf("}", start);
            if (end == -1) break;

            String obj = json.substring(start, end + 1);
            String text = extractJsonField(obj, "question");
            if (text == null) text = extractJsonField(obj, "text");

            String choicesStr = extractJsonArray(obj, "choices");
            String answer = extractJsonField(obj, "answer");
            String explanation = extractJsonField(obj, "explanation");

            if (text != null && choicesStr != null) {
                String[] choices = parseStringArray(choicesStr);
                int correctIndex = 0;
                if (answer != null) {
                    for (int i = 0; i < choices.length; i++) {
                        if (choices[i].equals(answer)) {
                            correctIndex = i;
                            break;
                        }
                    }
                }
                questions.add(new Question(id++, text, choices, correctIndex, explanation));
            }

            start = end + 1;
        }
        return questions;
    }

    // ========================
    // Utilities
    // ========================

    private static String generatePlayerId() {
        return "p" + System.currentTimeMillis() % 100000 + (int)(Math.random() * 1000);
    }

    private static String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\"\\s*:\\s*\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length() - 1;

        int end = start + 1;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
            end++;
        }

        if (end >= json.length()) return null;
        return json.substring(start + 1, end)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\\\", "\\");
    }

    private static String extractJsonArray(String json, String field) {
        String pattern = "\"" + field + "\"\\s*:\\s*\\[";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start = json.indexOf("[", start);
        int end = json.indexOf("]", start);
        if (end == -1) return null;
        return json.substring(start, end + 1);
    }

    private static String[] parseStringArray(String arrayJson) {
        List<String> items = new ArrayList<>();
        int start = 0;
        while ((start = arrayJson.indexOf("\"", start)) != -1) {
            int end = start + 1;
            while (end < arrayJson.length()) {
                if (arrayJson.charAt(end) == '"' && arrayJson.charAt(end - 1) != '\\') break;
                end++;
            }
            if (end < arrayJson.length()) {
                items.add(arrayJson.substring(start + 1, end));
            }
            start = end + 1;
        }
        return items.toArray(new String[0]);
    }

    private static List<Integer> parseIntArray(String json) {
        List<Integer> ids = new ArrayList<>();
        String nums = json.replaceAll("[^0-9,]", "");
        for (String s : nums.split(",")) {
            if (!s.isEmpty()) {
                ids.add(Integer.parseInt(s));
            }
        }
        return ids;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String readBody(HttpExchange ex) throws IOException {
        InputStream in = ex.getRequestBody();
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ========================
    // Models
    // ========================

    enum QuizState {
        IDLE, WAIT_JOIN, QUESTION_ACTIVE, QUESTION_CLOSED, RESULT, END
    }

    static class QuizSession {
        QuizState state = QuizState.IDLE;
        Map<String, Player> players = new ConcurrentHashMap<>();
        List<Question> questions = new ArrayList<>();
        Map<Integer, Map<String, Answer>> answers = new ConcurrentHashMap<>();
        int currentQuestionIndex = -1;
        long questionStartTime = 0;

        Question getCurrentQuestion() {
            if (currentQuestionIndex >= 0 && currentQuestionIndex < questions.size()) {
                return questions.get(currentQuestionIndex);
            }
            return null;
        }
    }

    static class Player {
        String id;
        String nickname;
        int score = 0;
        long joinedAt;

        Player(String id, String nickname) {
            this.id = id;
            this.nickname = nickname;
            this.joinedAt = System.currentTimeMillis();
        }
    }

    static class Question {
        int id;
        String text;
        String[] choices;
        int correctIndex;
        String explanation;

        Question(int id, String text, String[] choices, int correctIndex, String explanation) {
            this.id = id;
            this.text = text;
            this.choices = choices;
            this.correctIndex = correctIndex;
            this.explanation = explanation;
        }
    }

    static class Answer {
        String playerId;
        int questionId;
        int choice;
        long answeredAt;

        Answer(String playerId, int questionId, int choice, long answeredAt) {
            this.playerId = playerId;
            this.questionId = questionId;
            this.choice = choice;
            this.answeredAt = answeredAt;
        }
    }

    static class WebSocketClient {
        String id;
        Socket socket;
        OutputStream out;

        WebSocketClient(String id, Socket socket, OutputStream out) {
            this.id = id;
            this.socket = socket;
            this.out = out;
        }
    }

    static class StaticFileHandler implements HttpHandler {
        private static final Map<String, String> MIME_TYPES = Map.of(
                "html", "text/html",
                "css", "text/css",
                "js", "application/javascript",
                "json", "application/json",
                "png", "image/png",
                "jpg", "image/jpeg",
                "svg", "image/svg+xml"
        );

        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            Path filePath = Paths.get("src/main/resources/static" + path);

            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                byte[] content = Files.readAllBytes(filePath);
                String ext = path.substring(path.lastIndexOf('.') + 1);
                String mime = MIME_TYPES.getOrDefault(ext, "application/octet-stream");

                ex.getResponseHeaders().set("Content-Type", mime + ";charset=UTF-8");
                ex.sendResponseHeaders(200, content.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(content);
                }
            } else {
                ex.sendResponseHeaders(404, 0);
                ex.close();
            }
        }
    }
}
