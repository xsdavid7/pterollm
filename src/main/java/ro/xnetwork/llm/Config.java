package ro.xnetwork.llm;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private static final String CONFIG_FILE = "wrapper.properties";
    public int port = 11434;
    public String host = "0.0.0.0";
    public String modelPath = "llm.gguf";
    public boolean autoDownloadDefault = true;
    public String defaultModelUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf";
    public int threads = 4;
    public int contextSize = 1024;
    public int batchSize = 1024;
    public int gpuLayers = 0;
    public boolean enableCliChat = true;
    public boolean disableThinking = true;

    public static Config load(String[] args) {
        Config config = new Config();
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            config.saveDefault(file);
        } else {
            Properties prop = new Properties();
            try (FileInputStream fis = new FileInputStream(file)) {
                prop.load(fis);
                config.port = Integer.parseInt(prop.getProperty("port", String.valueOf(config.port)));
                config.host = prop.getProperty("host", config.host);
                config.modelPath = prop.getProperty("model_path", config.modelPath);
                config.autoDownloadDefault = Boolean.parseBoolean(prop.getProperty("auto_download_default", String.valueOf(config.autoDownloadDefault)));
                config.defaultModelUrl = prop.getProperty("default_model_url", config.defaultModelUrl);
                config.threads = Integer.parseInt(prop.getProperty("threads", String.valueOf(config.threads)));
                config.contextSize = Integer.parseInt(prop.getProperty("context_size", String.valueOf(config.contextSize)));
                config.batchSize = Integer.parseInt(prop.getProperty("batch_size", String.valueOf(config.batchSize)));
                config.gpuLayers = Integer.parseInt(prop.getProperty("gpu_layers", String.valueOf(config.gpuLayers)));
                config.enableCliChat = Boolean.parseBoolean(prop.getProperty("enable_cli_chat", String.valueOf(config.enableCliChat)));
                config.disableThinking = Boolean.parseBoolean(prop.getProperty("disable_thinking", String.valueOf(config.disableThinking)));
            } catch (IOException e) {}
        }
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                try { config.port = Integer.parseInt(arg.substring(7).trim()); } catch (NumberFormatException ignored) {}
            }
        }
        return config;
    }

    private void saveDefault(File file) {
        Properties prop = new Properties();
        prop.setProperty("port", String.valueOf(this.port));
        prop.setProperty("host", this.host);
        prop.setProperty("model_path", this.modelPath);
        prop.setProperty("auto_download_default", String.valueOf(this.autoDownloadDefault));
        prop.setProperty("default_model_url", this.defaultModelUrl);
        prop.setProperty("threads", String.valueOf(this.threads));
        prop.setProperty("context_size", String.valueOf(this.contextSize));
        prop.setProperty("batch_size", String.valueOf(this.batchSize));
        prop.setProperty("gpu_layers", String.valueOf(this.gpuLayers));
        prop.setProperty("enable_cli_chat", String.valueOf(this.enableCliChat));
        prop.setProperty("disable_thinking", String.valueOf(this.disableThinking));
        try (FileOutputStream fos = new FileOutputStream(file)) {
            prop.store(fos, "Configuratie LLM Wrapper Pterodactyl");
        } catch (IOException ignored) {}
    }
}
