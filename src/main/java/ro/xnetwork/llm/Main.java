package ro.xnetwork.llm;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Scanner;

public class Main {
    static {
        System.setProperty("java.io.tmpdir", new File(".").getAbsolutePath());
        bootstrapNativeLibraries();
    }

    private static void bootstrapNativeLibraries() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("linux")) return;
        try {
            File target = new File("libgomp.so.1");
            if (!target.exists()) {
                try (InputStream in = Main.class.getResourceAsStream("/native/linux-x86_64/libgomp.so.1")) {
                    if (in != null) {
                        try (FileOutputStream out = new FileOutputStream(target)) { in.transferTo(out); }
                        target.setExecutable(true, false);
                    }
                }
            }
            if (target.exists()) System.load(target.getAbsolutePath());
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        Config config = Config.load(args);
        try { ModelDownloader.ensureModelExists(config); } catch (Exception e) { System.exit(1); }
        InferenceEngine engine = new InferenceEngine(config);
        OllamaApiServer apiServer;
        try {
            apiServer = new OllamaApiServer(config, engine);
            apiServer.start();
        } catch (Exception e) { System.exit(1); return; }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            apiServer.stop();
            engine.close();
        }));

        if (config.enableCliChat) runCliLoop(engine, apiServer);
    }

    private static void runCliLoop(InferenceEngine engine, OllamaApiServer apiServer) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nUser > ");
            System.out.flush();
            if (!scanner.hasNextLine()) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                continue;
            }
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("/exit") || input.equalsIgnoreCase("/stop")) {
                apiServer.stop();
                engine.close();
                System.exit(0);
                break;
            }
            if (input.isEmpty()) continue;

            String prompt = "<|start_header_id|>user<|end_header_id|>\n\n" + input + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n";
            System.out.print("LLM > ");
            System.out.flush();

            engine.generateStream(prompt, token -> {
                System.out.print(token);
                System.out.flush();
                return true;
            });
            System.out.println();
        }
    }
}
