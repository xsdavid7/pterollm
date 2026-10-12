package ro.xnetwork.llm;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class OllamaApiServer {
    private final HttpServer server;
    private final InferenceEngine engine;
    private final Config config;
    private final Gson gson = new Gson();

    public OllamaApiServer(Config config, InferenceEngine engine) throws IOException {
        this.config = config;
        this.engine = engine;
        this.server = HttpServer.create(new InetSocketAddress(config.host, config.port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        registerRoutes();
    }

    private void registerRoutes() {
        server.createContext("/api/version", exchange -> sendJson(exchange, "{\"version\": \"0.1.0\"}"));
        server.createContext("/api/tags", exchange -> {
            JsonObject m = new JsonObject();
            m.addProperty("name", config.modelPath);
            JsonArray arr = new JsonArray();
            arr.add(m);
            JsonObject res = new JsonObject();
            res.add("models", arr);
            sendJson(exchange, res.toString());
        });
        server.createContext("/api/generate", new StreamGenerateHandler());
        server.createContext("/api/chat", new StreamChatHandler());
    }

    private class StreamGenerateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }
            JsonObject req = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), JsonObject.class);
            
            String rawPrompt = req.has("prompt") ? req.get("prompt").getAsString() : "";
            boolean stream = !req.has("stream") || req.get("stream").getAsBoolean();

            // AICI ERA PROBLEMA PENTRU HALUCINATII LA /api/generate!
            // Raw prompt trebuie formatat cu tag-urile Llama 3 inainte de a fi trimis motorului C++
            String systemPrompt = config.disableThinking ? "<|start_header_id|>system<|end_header_id|>\n\nRespond directly and concisely. No inner thoughts.<|eot_id|>" : "";
            String formattedPrompt = systemPrompt + "<|start_header_id|>user<|end_header_id|>\n\n" + rawPrompt + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n";

            exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, 0);

            System.out.println("[API] Request generare primit.");
            try (OutputStream os = exchange.getResponseBody()) {
                if (stream) {
                    engine.generateStream(formattedPrompt, token -> {
                        try {
                            JsonObject chunk = new JsonObject();
                            chunk.addProperty("model", config.modelPath);
                            chunk.addProperty("response", token);
                            chunk.addProperty("done", false);
                            os.write((chunk.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                            os.flush();
                            return true;
                        } catch (IOException e) {
                            return false; // Stop engine
                        }
                    });
                    
                    try {
                        JsonObject end = new JsonObject();
                        end.addProperty("model", config.modelPath);
                        end.addProperty("response", "");
                        end.addProperty("done", true);
                        os.write((end.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    } catch (IOException ignored) {}
                    
                } else {
                    StringBuilder full = new StringBuilder();
                    engine.generateStream(formattedPrompt, token -> {
                        full.append(token);
                        return true;
                    });
                    
                    JsonObject end = new JsonObject();
                    end.addProperty("model", config.modelPath);
                    end.addProperty("response", full.toString());
                    end.addProperty("done", true);
                    os.write((end.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            } catch (Exception ignored) {}
            System.out.println("[API] Request generare terminat.");
        }
    }

    private class StreamChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }
            JsonObject req = gson.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), JsonObject.class);
            JsonArray messages = req.has("messages") ? req.getAsJsonArray("messages") : new JsonArray();
            boolean stream = !req.has("stream") || req.get("stream").getAsBoolean();

            StringBuilder promptBuilder = new StringBuilder("<|begin_of_text|>");
            if (config.disableThinking) {
                promptBuilder.append("<|start_header_id|>system<|end_header_id|>\n\n")
                             .append("Respond directly and concisely. No inner thoughts.")
                             .append("<|eot_id|>");
            }
            for (JsonElement el : messages) {
                JsonObject msg = el.getAsJsonObject();
                promptBuilder.append("<|start_header_id|>").append(msg.get("role").getAsString()).append("<|end_header_id|>\n\n")
                             .append(msg.get("content").getAsString()).append("<|eot_id|>");
            }
            promptBuilder.append("<|start_header_id|>assistant<|end_header_id|>\n\n");

            exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, 0);

            System.out.println("[API] Request chat primit.");
            try (OutputStream os = exchange.getResponseBody()) {
                if (stream) {
                    engine.generateStream(promptBuilder.toString(), token -> {
                        try {
                            JsonObject chunk = new JsonObject();
                            chunk.addProperty("model", config.modelPath);
                            JsonObject msg = new JsonObject();
                            msg.addProperty("role", "assistant");
                            msg.addProperty("content", token);
                            chunk.add("message", msg);
                            chunk.addProperty("done", false);
                            os.write((chunk.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                            os.flush();
                            return true;
                        } catch (IOException e) {
                            return false;
                        }
                    });
                    
                    try {
                        JsonObject end = new JsonObject();
                        end.addProperty("model", config.modelPath);
                        JsonObject msg = new JsonObject();
                        msg.addProperty("role", "assistant");
                        msg.addProperty("content", "");
                        end.add("message", msg);
                        end.addProperty("done", true);
                        os.write((end.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    } catch (IOException ignored) {}
                    
                } else {
                    StringBuilder full = new StringBuilder();
                    engine.generateStream(promptBuilder.toString(), token -> {
                        full.append(token);
                        return true;
                    });
                    
                    JsonObject end = new JsonObject();
                    end.addProperty("model", config.modelPath);
                    JsonObject msg = new JsonObject();
                    msg.addProperty("role", "assistant");
                    msg.addProperty("content", full.toString());
                    end.add("message", msg);
                    end.addProperty("done", true);
                    os.write((end.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            } catch (Exception ignored) {}
            System.out.println("[API] Request chat terminat.");
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }

    private void sendJson(HttpExchange exchange, String body) throws IOException {
        addCorsHeaders(exchange);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    public void start() {
        server.start();
        System.out.println("[API] Asculta pe http://" + config.host + ":" + config.port);
    }
    public void stop() { server.stop(0); }
}
