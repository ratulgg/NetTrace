# NetTrace 📡

> **Phase 1: Performance Analysis of Software Design Patterns in Network Simulation**

[![Live Demo](https://img.shields.io/badge/Live_Demo-Render-0088ff?style=for-the-badge&logo=render&logoColor=white)](https://nettrace.onrender.com/)
[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

**NetTrace** is a high-performance synthetic traffic routing engine and network telemetry visualizer built to quantify the **"abstraction tax"** of clean Software Design Patterns versus a raw Procedural Monolith during high-throughput packet inspection.

🔗 **Live Interactive Dashboard:** [https://nettrace.onrender.com/](https://nettrace.onrender.com/)

---

## 📌 Architectural Comparison

| Feature | Model A (Procedural Baseline) | Model B (Pattern-Driven AI Engine) |
| :--- | :--- | :--- |
| **Architecture** | Monolithic procedural execution loop | Decoupled Object-Oriented design |
| **Data Types** | Primitive `String` & `int` representations | Java 21 `record` types (`Packet`) |
| **Creation Logic** | Direct inline instantiation inside loop | **Factory Method Pattern** (`PacketFactory`) |
| **Event Routing** | Hardcoded `if/else` & direct logging | **Observer Pattern** (`NetworkChannel`) |
| **Coupling (CBO)** | High (Rigid, difficult to extend) | Low (Plug-and-play observable components) |

---

## 📊 Performance Telemetry Benchmark

Benchmarked across **10 HotSpot JIT warmup iterations** (triggering bytecode C2 compilation) followed by **50 measured benchmark runs** executing 10,000 synthetic packet streams under a deterministic seed (`Random(42)`):

```text
==================================================
  FINAL BENCHMARK SUMMARY (50 MEASURED RUNS)
==================================================
Model A (Procedural Baseline) Avg Latency : 4.313 ms
Model B (Pattern-Driven Engine) Avg Latency : 4.412 ms
Pattern Abstraction Tax                   : +0.099 ms (+2.30%)
==================================================
