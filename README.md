# NetTrace

[![Live Demo](https://img.shields.io/badge/live%20demo-nettrace.onrender.com-1d9e75?style=for-the-badge)](https://nettrace.onrender.com/)

Click the badge above for the running dashboard at
[nettrace.onrender.com](https://nettrace.onrender.com/) — it's the actual
Java backend on Render, not a static screenshot. First load may take a
few seconds if the free-tier instance was asleep.

A live network-routing & threat-detection simulator that runs the *same*
workload two ways — a plain **procedural** implementation and a
**Factory + Observer** pattern-driven implementation — and measures the
real, live cost of the abstraction on every request.

```
Model A (procedural)  ──┐
                         ├──►  BenchmarkRunner  ──►  /api/run  ──►  dashboard
Model B (Factory+Observer) ──┘
```

## Why

"Design patterns add overhead" is usually an opinion. NetTrace turns it
into a number: both models process an identical, seeded synthetic packet
stream, and the measured latency difference (the **"abstraction tax"**) is
shown live in the dashboard, not hardcoded from a one-time offline run.

## Features

- 5-hop packet routing simulation with per-node queue backpressure
- Threat detection at a simulated firewall node, toggleable "attack mode"
- Synthetic packet stream classified by a genuinely trained logistic
  regression (`AiThreatClassifier`) — not a coin flip or hardcoded rule;
  see `training/train_threat_classifier.py`
- Live Model A vs Model B latency benchmark, charted in real time
- In-browser JUnit test runner (`/api/tests`) that surfaces backend
  assertions in the UI
- CSV export of the synthetic packet stream

## Tech stack

- Java 21, built with Maven, packaged as a shaded/fat JAR
- `com.sun.net.httpserver.HttpServer` (JDK built-in, no framework)
- JUnit 5 for tests
- Vanilla HTML/CSS/JS dashboard (Chart.js for the latency graph)
- Docker multi-stage build for deployment

## Project layout

```
com.nettrace
├── NetTraceServer.java        # HTTP server + request handlers
├── Packet.java                # dashboard's packet/hop display model
├── PacketQueue.java           # dashboard's queue/backpressure simulation
├── TopologyEngine.java        # dashboard's 5-hop routing simulation
├── AiThreatClassifier.java    # trained logistic regression, packet threat scoring
├── benchmark/
│   └── BenchmarkRunner.java   # runs Model A vs Model B, computes the tax
├── modelA_baseline/
│   └── ProceduralSimulator.java   # Model A — no design patterns
└── modelB_patterns/
    ├── core/                  # Packet, PacketFactory, NetworkObserver (interfaces)
    ├── factory/                # Tcp/Udp/IcmpPacket + their factories
    ├── observer/                # MetricsCollector, TrafficLogger, NoOpObserver
    ├── channel/
    │   └── NetworkChannel.java # the Observer "Subject"
    └── PatternSimulator.java  # Model B — Factory + Observer wired together

training/
└── train_threat_classifier.py # offline training for AiThreatClassifier (scikit-learn)
```

> `com.nettrace.Packet` (dashboard display model) and
> `com.nettrace.modelB_patterns.core.Packet` (the pattern being
> benchmarked) are intentionally separate, unrelated classes in different
> packages — the dashboard's routing visualization doesn't use Factory or
> Observer at all.

## UML — class diagram

```mermaid
classDiagram
    %% ===================== Model B: pattern-driven core =====================
    class Packet {
        <<interface>>
        +getProtocol() String
        +getSourceIp() String
        +getDestIp() String
        +getPayloadSize() int
        +getFlag() String
    }

    class PacketFactory {
        <<interface>>
        +createPacket(sourceIp, destIp, payloadSize) Packet
    }

    class NetworkObserver {
        <<interface>>
        +onPacketTransmitted(packet)
        +onPacketDropped(sourceIp, destIp, reason)
    }

    class TcpPacket {
        <<record>>
        +getProtocol() "TCP"
    }
    class UdpPacket {
        <<record>>
        +getProtocol() "UDP"
    }
    class IcmpPacket {
        <<record>>
        +getProtocol() "ICMP"
    }

    class TcpPacketFactory
    class UdpPacketFactory
    class IcmpPacketFactory

    class NetworkChannel {
        -observers List~NetworkObserver~
        +registerObserver(o)
        +unregisterObserver(o)
        +dispatchPacket(packet)
    }

    class MetricsCollector {
        -tcpCount int
        -udpCount int
        -icmpCount int
        -droppedCount int
        +getTcpCount() int
        +getTotalProcessed() int
    }
    class TrafficLogger {
        -packetCounter int
    }
    class NoOpObserver {
        -transmittedCount int
        -droppedCount int
    }

    class PatternSimulator {
        <<Model B>>
        +runQuiet(seed) Result
    }

    Packet <|.. TcpPacket
    Packet <|.. UdpPacket
    Packet <|.. IcmpPacket

    PacketFactory <|.. TcpPacketFactory
    PacketFactory <|.. UdpPacketFactory
    PacketFactory <|.. IcmpPacketFactory
    TcpPacketFactory ..> TcpPacket : creates
    UdpPacketFactory ..> UdpPacket : creates
    IcmpPacketFactory ..> IcmpPacket : creates

    NetworkObserver <|.. MetricsCollector
    NetworkObserver <|.. TrafficLogger
    NetworkObserver <|.. NoOpObserver

    NetworkChannel o-- "many" NetworkObserver : notifies
    NetworkChannel ..> Packet : dispatches

    PatternSimulator --> NetworkChannel : uses
    PatternSimulator --> PacketFactory : uses

    %% ===================== Model A: procedural baseline =====================
    class ProceduralSimulator {
        <<Model A>>
        +runQuiet(seed) Result
    }

    %% ===================== Benchmark harness =====================
    class BenchmarkRunner {
        +run(warmupRuns, benchmarkRuns, seed) BenchmarkResult
    }
    BenchmarkRunner --> ProceduralSimulator : times
    BenchmarkRunner --> PatternSimulator : times

    %% ===================== Dashboard (unrelated to Factory/Observer) =====================
    class NetTraceServer {
        +main(args)
    }
    class TopologyEngine {
        -path List~RouterNode~
        +tracePacketPath(id, srcIp, destIp, simulateThreat) DashPacket
    }
    class PacketQueue {
        -capacity int
        -currentDepth int
        +processPacket(attackMode) QueueResult
    }
    class DashboardPacket {
        <<dashboard model, unrelated to core.Packet>>
        +packetId String
        +hops List~HopRecord~
    }

    NetTraceServer --> TopologyEngine : serves /api/run
    NetTraceServer --> BenchmarkRunner : serves /api/run
    TopologyEngine --> PacketQueue : per hop
    TopologyEngine --> DashboardPacket : produces
```

> Note: `DashboardPacket` above is `com.nettrace.Packet` — renamed only in
> this diagram to avoid visual confusion with `Packet` (the interface in
> `modelB_patterns.core`). They are unconnected classes in the real code.

## Running it

**Requirements:** Java 21, Maven.

```bash
# Run the dashboard server (defaults to port 5000, or $PORT)
mvn compile exec:java -Dexec.mainClass=com.nettrace.NetTraceServer
# or, after packaging:
mvn clean package
java -jar target/nettrace-1.0-SNAPSHOT.jar
```

Then open `http://localhost:5000`.

**Run the tests:**
```bash
mvn test
```

**Run either model's standalone CLI benchmark:**
```bash
mvn compile exec:java -Dexec.mainClass=com.nettrace.modelA_baseline.ProceduralSimulator
mvn compile exec:java -Dexec.mainClass=com.nettrace.modelB_patterns.PatternSimulator
```
> Note: `BenchmarkRunner` itself currently has no `main()` method, so there
> is no standalone CLI command for it yet — the only way to exercise it
> today is indirectly, via `GET /api/run` (live, 200 warmup / 15 timed
> passes) or via the JUnit tests in `PatternEngineTest` (a handful of
> passes, for correctness, not for a real measurement). Add a `main()`
> to `BenchmarkRunner.java` if a standalone offline benchmark command is
> needed later.

**Docker:**
```bash
docker build -t nettrace .
docker run -p 5000:5000 nettrace
```

## API

| Endpoint | Description |
|---|---|
| `GET /` | Dashboard UI |
| `GET /api/run?srcIp=&dstIp=&mode=&batchSize=` | Runs one live trace + live Model A/B benchmark, returns JSON |
| `GET /api/tests` | Runs backend assertions live, returns JSON results for the UI's test panel |

## Benchmark methodology

Both `ProceduralSimulator.runQuiet(seed)` and `PatternSimulator.runQuiet(seed)`:
- perform **zero console I/O** inside the timed region (timing isn't
  polluted by `System.out`)
- are **reseeded on every call** (`seed + passIndex`), so each paired pass
  is reproducible and directly comparable between the two models, even
  though the seed itself increments across passes within a run
- are preceded by untimed warm-up passes to let the JIT compiler settle
  before timing starts. The live dashboard (`NetTraceServer`) currently
  requests **200 warm-up passes** per `/api/run` call, and
  `BenchmarkRunner.run()` additionally enforces a floor of 25 warm-up
  passes even if a caller asks for fewer

`BenchmarkRunner` reports the average over **15 timed passes** per live
request (`LIVE_BENCHMARK_RUNS`), and before averaging it applies a
**trimmed mean**: for each model, the fastest 1 and slowest 3 of the
timed samples are discarded, so a single JIT hiccup or GC pause (more
likely to spike a run slow than fast on a shared-tenant deployment)
doesn't skew the reported average.

> The badge/benchmark-table figures referenced elsewhere for this project
> (4.313 ms / 4.412 ms / +2.30% over 50 runs) came from a specific past
> run. As of this codebase, there is no standalone entry point that
> reproduces exactly that 50-run configuration on demand — see the note
> under "Running it" above. Treat those figures as a real, one-time
> result rather than a number you can currently regenerate with a single
> documented command.
