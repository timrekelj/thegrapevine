"""Run against an already-running service; uses curl, no test dependencies."""
import json
import subprocess

BASE = "http://127.0.0.1:8100"


def curl(path, payload=None):
    command = ["curl", "--fail-with-body", "--silent", "--show-error", "--max-time", "120",
               "--write-out", "\n%{time_total}", BASE + path]
    if payload is not None:
        command += ["-H", "content-type: application/json", "-d", json.dumps(payload)]
    output = subprocess.check_output(command, text=True)
    body, seconds = output.rsplit("\n", 1)
    return json.loads(body), float(seconds)


health, _ = curl("/health")
assert health["status"] == "ok" and health["model"] == "strands-decider-2b", health
print("health", health)
cases = [
    ("same", "A: NASA launches Artemis II crewed mission around the Moon on April 1, 2026.\n"
     "B: Four astronauts lift off on NASA's Artemis II lunar flyby on April 1, 2026.",
     {"same_event": {"type": "yesno", "instructions": "Do both headlines describe the same specific event, not merely the same topic?"}},
     "same_event", True),
    ("different", "A: NASA launches Artemis II crewed mission around the Moon on April 1, 2026.\n"
     "B: Japan's central bank raises interest rates in Tokyo on July 15, 2026.",
     {"same_event": {"type": "yesno", "instructions": "Do both headlines describe the same specific event, not merely the same topic?"}},
     "same_event", False),
    ("choice", "NASA launches four astronauts on a mission around the Moon.",
     {"category": {"type": "choice", "options": ["science", "sports", "finance"],
                   "instructions": "Which newspaper category best fits this headline?"}},
     "category", "science"),
]
for label, state, questions, name, expected in cases:
    for run in ("first", "warm"):
        result, seconds = curl("/decide", {"state": state, "questions": questions})
        print(label, run, json.dumps(result), f"latency={seconds:.3f}s", flush=True)
        assert result[name]["answer"] == expected, result
        assert 0 <= result[name]["probability"] <= 1, result
print("PASS: same event, different event, and three-option choice")
