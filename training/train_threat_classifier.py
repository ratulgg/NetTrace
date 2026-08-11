"""
Trains the logistic regression used by AiThreatClassifier.java.

This is an OFFLINE, one-time training step -- the output is just 6 numbers
(5 weights + 1 bias) that get hardcoded into the Java class. The running
NetTrace server has no dependency on Python, numpy, or scikit-learn; this
script only needs to be re-run if you want to retrain on a different
synthetic dataset or add/change features.

Requires: numpy, scikit-learn (pip install numpy scikit-learn)
Run:      python3 train_threat_classifier.py
Output:   prints training accuracy + the 6 constants to paste into
          AiThreatClassifier.java
"""
import numpy as np
from sklearn.linear_model import LogisticRegression

rng = np.random.default_rng(42)

N = 30000
labels = rng.integers(0, 2, size=N)

# Overlapping payload ranges (not perfectly separable) so no single feature dominates
payload = np.where(
    labels == 1,
    rng.normal(900, 300, size=N),
    rng.normal(500, 300, size=N)
)
payload = np.clip(payload, 64, 1464)

is_syn = np.where(labels == 1, rng.random(N) < 0.55, rng.random(N) < 0.25).astype(int)
is_suspicious_port = np.where(labels == 1, rng.random(N) < 0.50, rng.random(N) < 0.10).astype(int)
is_high_src_port = np.where(labels == 1, rng.random(N) < 0.55, rng.random(N) < 0.25).astype(int)
is_icmp = np.where(labels == 1, rng.random(N) < 0.40, rng.random(N) < 0.20).astype(int)

X = np.column_stack([
    payload / 1500.0,
    is_syn,
    is_suspicious_port,
    is_high_src_port,
    is_icmp
])
y = labels

model = LogisticRegression()
model.fit(X, y)

acc = model.score(X, y)
print("train accuracy:", acc)

w = model.coef_[0]
b = model.intercept_[0]
print()
print("Java weights:")
print(f"W_PAYLOAD = {w[0]:.6f};")
print(f"W_SYN = {w[1]:.6f};")
print(f"W_SUSPICIOUS_PORT = {w[2]:.6f};")
print(f"W_HIGH_SRC_PORT = {w[3]:.6f};")
print(f"W_ICMP = {w[4]:.6f};")
print(f"BIAS = {b:.6f};")

# Sanity-check a few hand-built cases
def sigmoid(z): return 1/(1+np.exp(-z))
def score(payload_norm, syn, susp, high_src, icmp):
    z = w[0]*payload_norm + w[1]*syn + w[2]*susp + w[3]*high_src + w[4]*icmp + b
    return sigmoid(z)

print()
print("benign-ish (small pkt, ACK, std port, low src port, TCP):", score(64/1500, 0, 0, 0, 0))
print("attack-ish (big pkt, SYN, weird port, high src port, ICMP):", score(1400/1500, 1, 1, 1, 1))
print("mixed case:", score(800/1500, 1, 0, 1, 0))
