package ro.xnetwork.llm;
import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import java.util.function.Function;

public class InferenceEngine implements AutoCloseable {
    private final LlamaModel model;
    private final Object lock = new Object();

    public InferenceEngine(Config config) {
        System.out.println("[Engine] Incarcare GGUF: " + config.modelPath);
        ModelParameters params = new ModelParameters()
                .setModelFilePath(config.modelPath)
                .setNBatch(config.batchSize)
                .setNCtx(config.contextSize)
                .setNGpuLayers(config.gpuLayers)
                .setNThreads(config.threads);
        this.model = new LlamaModel(params);
        System.out.println("[Engine] Gata.");
    }

    public void generateStream(String prompt, Function<String, Boolean> onToken) {
        synchronized (lock) {
            InferenceParameters params = new InferenceParameters(prompt)
                    .setTemperature(0.6f)
                    .setTopK(40)
                    .setTopP(0.9f)
                    .setRepeatPenalty(1.1f)
                    .setNPredict(1024); // Protectie maxima anti-bucla

            for (Object output : model.generate(params)) {
                String token = output.toString();
                
                // Oprire IMEDIATA hardcodata la tag-ul specific Llama 3
                if (token.contains("<|eot_id|>") || token.contains("<|end_of_text|>")) {
                    break;
                }
                
                boolean continueGen = onToken.apply(token);
                if (!continueGen) {
                    System.out.println("[Engine] Oprit de retea/API.");
                    break;
                }
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (model != null) model.close();
        }
    }
}
