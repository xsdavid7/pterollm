package ro.xnetwork.llm;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ModelDownloader {
    public static void ensureModelExists(Config config) throws Exception {
        File modelFile = new File(config.modelPath);
        if (modelFile.exists()) return;
        if (!config.autoDownloadDefault) throw new RuntimeException("Model lipsa.");
        System.out.println("[Downloader] Se descarca modelul...");
        File tempFile = new File(config.modelPath + ".part");
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(config.defaultModelUrl)).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        try (InputStream in = response.body(); FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[65536];
            long current = 0, lastPrint = 0;
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                current += bytesRead;
                long now = System.currentTimeMillis();
                if (now - lastPrint > 500) {
                    System.out.printf("\r[Downloader] [%.2f%%] %.2f MB", (current * 100.0) / total, current / 1048576.0);
                    lastPrint = now;
                }
            }
            System.out.println("\n[Downloader] Descarcare finalizata!");
        }
        tempFile.renameTo(modelFile);
    }
}
