# NetTrace 📡

> **Phase 1: Performance Analysis of Software Design Patterns in Network Simulation**

[![Live Demo](https://img.shields.io/badge/Live_Demo-Render-0088ff?style=for-the-badge&logo=render&logoColor=white)](https://nettrace.onrender.com/)
[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

NetTrace benchmarks the real-world execution cost ("abstraction tax") of clean Software Design Patterns versus a raw Procedural Monolith under high-throughput network packet processing.

🔗 **Live Interactive Dashboard:** [https://nettrace.onrender.com/](https://nettrace.onrender.com/)

---

## 📌 Architectural Comparison

| Feature | Model A (Procedural) | Model B (Pattern-Driven) |
| :--- | :--- | :--- |
| **Architecture** | Monolithic `main()` loop | Decoupled Object-Oriented |
| **Data Types** | Primitive `String` & `int` | Java 21 `record` types (`Packet`) |
| **Creation Logic** | Direct instantiation inside loop | **Factory Method Pattern** (`PacketFactory`) |
| **Event Routing** | Hardcoded `if/else` & direct logging | **Observer Pattern** (`NetworkChannel`) |
| **Coupling (CBO)** | High (Hard to extend/test) | Low (Plug-and-play components) |

---

## 📊 Benchmark Results (50 Runs @ 10,000 Packets)

Ran across **10 warmup iterations** (to trigger HotSpot JIT compilation) followed by **50 measured benchmark runs** using a deterministic seed (`Random(42)`).

```text
==================================================
  FINAL BENCHMARK SUMMARY (50 RUNS)
==================================================
Model A (Procedural) Avg Latency : 4.313 ms
Model B (Patterns)   Avg Latency : 4.412 ms
Pattern Overhead                 : +0.099 ms (2.30%)
==================================================
