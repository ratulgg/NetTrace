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

## AI model — threat classifier

`AiThreatClassifier` is a **logistic regression** trained offline with
scikit-learn (`training/train_threat_classifier.py`) on 30,000 synthetic,
labeled packets. The learned weights (5 of them, plus a bias) are hardcoded
into the Java class, so the running server has zero ML library dependency —
inference at request time is just a dot product and a sigmoid.

**Features (5):**

| Feature | Description |
|---|---|
| `payload / 1500.0` | Normalized payload size |
| `is_syn` | 1 if TCP flag is `SYN` |
| `is_suspicious_port` | 1 if destination port isn't 80 or 443 |
| `is_high_src_port` | 1 if source port > 60000 |
| `is_icmp` | 1 if protocol is `ICMP` |

**Decision rule:** `sigmoid(w·x + b) > 0.5` ⇒ classified as a threat
(`AiThreatClassifier.THREAT_THRESHOLD`).

**Accuracy:** ~83% on the training set (30,000 synthetic samples, printed
by the training script as `model.score(X, y)`). Note this is *training*
accuracy, not held-out/test accuracy — the script doesn't do a train/test
split, so treat 83% as an upper-bound sanity check rather than a
generalization guarantee.

**Honesty notes (also in the class Javadoc):**
- Trained on **synthetic**, seeded data — not real network traffic.
- Intentionally simple: 5 hand-picked features, no hidden layers, no
  regularization tuning, no cross-validation.
- Built for a course project / demo, not a production intrusion detection
  system.
- The synthetic data generator overlaps the two classes on purpose (e.g.
  payload size is drawn from overlapping normal distributions for benign
  vs. attack) so no single feature perfectly separates threat from
  non-threat — this is meant to make the ~83% figure meaningful rather
  than trivially 100%.

To retrain (e.g. with different features or a larger dataset):
```bash
pip install numpy scikit-learn
python3 training/train_threat_classifier.py
```
This prints the new accuracy and the 6 constants (`W_PAYLOAD`, `W_SYN`,
`W_SUSPICIOUS_PORT`, `W_HIGH_SRC_PORT`, `W_ICMP`, `BIAS`) to paste back
into `AiThreatClassifier.java` — there's no runtime model-loading step.

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

## Ideas for UI/UX enhancements

The dashboard (`src/main/resources/index.html`) already has a glass-panel
theme, light/dark switcher, and a live topology view. Some ideas for
taking it further, roughly in order of effort:

**Low effort**
- Animate the metric cards (`tax-val`, `threat-val`, `pps-val`, `loss-val`)
  with a count-up/count-down transition instead of snapping to the new
  number, so live updates feel less jumpy.
- Add a tooltip or small "?" info icon next to "abstraction tax" and the
  threat count explaining what they mean and how they're computed — new
  visitors currently have to read this README to know.
- Add a visible loading/skeleton state on `#run-btn` while `/api/run` is
  in flight, and disable the button to prevent double-submits.
- Surface toast notifications for errors (e.g. a failed `/api/run` call)
  instead of failing silently.

**Medium effort**
- Show *why* a packet was flagged as a threat — e.g. a small breakdown of
  which of the 5 features fired (SYN flag, suspicious port, etc.) next to
  each row in the packet log table, so the AI classifier's output is
  explainable rather than just a yes/no badge.
- Add a run history / sparkline so users can see how the abstraction tax
  has trended over their last N runs, not just the latest one.
- Make the 5-hop topology view interactive — clicking a hop node could
  show that node's queue depth over time, or its drop reason, instead of
  only the current-state meter.
- Improve keyboard/screen-reader accessibility: `aria-live` regions for
  the metric cards so updates are announced, focus states on the model
  toggle pills, and `aria-label`s on icon-only buttons.

**Higher effort**
- Replace the polling-style "click to run" flow with a streaming view
  (Server-Sent Events or WebSocket) so packets animate through the
  topology in real time instead of the dashboard jumping straight to a
  finished result.
- Add a side-by-side "diff" view for Model A vs Model B that highlights
  where in the pipeline the latency actually diverges, rather than only
  showing the final aggregate tax.
- Persist user preferences (theme, last-used `srcIp`/`dstIp`, model
  selection) so the dashboard remembers state across reloads.
- A guided "first run" walkthrough/tour for first-time visitors, since
  the dashboard currently assumes the user already understands what
  Model A/B and the abstraction tax are.
