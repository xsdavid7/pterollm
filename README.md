# Java Standalone LLM Server (Ollama Compatible)

A lightweight, zero-dependency Java wrapper for running GGUF Large Language Models locally on Linux systems, Google Cloud VMs, and restricted hosting environments like Pterodactyl containers.

Powered by [`java-llama.cpp`](https://github.com/kherud/java-llama.cpp) and packaged into a single, self-contained fat-JAR (`llm.jar`).

---

## Features

- **Rootless & Container-Ready:** Works in non-root Docker environments (like Pterodactyl) with pre-bundled native dependencies (`libgomp.so.1`).
- **Ollama API Compatible:** Implements standard Ollama endpoints (`/api/generate`, `/api/chat`, `/api/tags`, `/api/version`) supporting both standard and NDJSON streaming responses.
- **Low Memory Overhead:** Runs off-heap in C++ via `llama.cpp` — needs only minimal JVM heap allocations (`-Xmx2G` or less).
- **Auto-Downloader:** Automatically pulls down the default GGUF model on first startup if no model is found on disk.
- **Interactive CLI:** Built-in terminal interface for direct chatting alongside API requests.
- **Safe Request Cancellation:** Gracefully terminates CPU inference if a client disconnects (`Ctrl+C` / socket abort).

---

## Requirements

- **Java Runtime:** OpenJDK 21 or higher
- **Build Tool:** Apache Maven 3.8+ (for compiling from source)
- **OS:** Linux x86_64 / Windows 10+

---

## Quick Start

### 1. Build the JAR
Clone the repository and package the standalone fat-JAR:

```bash
git clone https://github.com/your-username/your-repo-name.git
cd your-repo-name/llm
mvn clean package
```

The compiled binary will be placed at `target/llm.jar`.

### 2. Run Locally

```bash
java -Xmx2G -Djava.io.tmpdir=. -jar target/llm.jar
```

On first boot, the server generates a default `wrapper.properties` configuration file and automatically downloads a starter GGUF model if one isn't present.

### 3. Run on Pterodactyl

Rename `llm.jar` to `server.jar` and use the following startup command:

```bash
java -Xms128M -XX:MaxRAMPercentage=12.0 -Djava.io.tmpdir=. -Dterminal.jline=false -Dterminal.ansi=true -jar server.jar --port={{SERVER_PORT}}
```

---

## Configuration (`wrapper.properties`)

The server configuration can be tuned in `wrapper.properties`:

```properties
# Network settings
port=11434
host=0.0.0.0

# Model settings
model_path=llm.gguf
auto_download_default=true
default_model_url=https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf

# Performance & Inference
threads=4
context_size=1024
batch_size=1024
gpu_layers=0

# Behavior
enable_cli_chat=true
disable_thinking=true
```

> **Tip for better response quality:** Replace `default_model_url` and `model_path` with higher parameter models (e.g., Llama 3.2 3B or Qwen 2.5 7B GGUF) if your host has enough RAM/vCPU.

---

## API Usage Examples

### Check Server Version
```bash
curl http://localhost:11434/api/version
```

### Text Completion (`/api/generate`)
```bash
curl -X POST http://localhost:11434/api/generate \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "What is Retrieval-Augmented Generation (RAG)?",
    "stream": false
  }'
```

### Streaming Chat Completion (`/api/chat`)
```bash
curl -N -X POST http://localhost:11434/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "Tell me a short joke."}
    ],
    "stream": true
  }'
```

---

## License

This project is licensed under the [MIT License](LICENSE).
