# Validation — 12 October 2026

Real official model, not mocked or substituted. Hardware: Apple M4 Pro (12 cores), 24 GB unified memory, macOS 27.0.1. Python 3.12.15; torch 2.7.1; transformers 5.17.0; peft 0.21.0. Service selected `mps`.

## Commands and outcomes

Run from `model-server/` unless stated otherwise:

1. `brew install uv` — installed uv 0.13.0.
2. `uv venv --python 3.12` — downloaded managed CPython 3.12.15 and created `.venv` (system Python was not used).
3. `uv pip install -r requirements.txt` — succeeded, 51 packages; built official Strands code at pinned Git revision.
4. `.venv/bin/python -m unittest -v test_server.py` — **4 tests passed** (final run: 0.019 s). Covers all native mappings, chosen-answer probabilities (including false and score), invalid options, HTTP health/decision shape, and 422 propagation. Upstream Starlette emits a non-failing `httpx` deprecation warning.
5. `HF_HOME="$PWD/.cache/huggingface" .venv/bin/python server.py > server.log 2>&1` — downloaded public v19/base weights and started localhost server, no HF credentials. First launch was intentionally terminated/restarted after a small adapter code adjustment. An initial premature smoke attempt failed with curl exit 7 while startup was still downloading; this was not an inference failure.
6. `curl --fail-with-body --silent --show-error --retry 30 --retry-connrefused --retry-delay 2 http://127.0.0.1:8100/health` — returned `{"status":"ok","model":"strands-decider-2b","device":"mps"}` after one connection-refused retry during startup.
7. `.venv/bin/python scripts/smoke.py` — **passed all six real curl calls**, two runs each of the three required cases:

| Case | Observed answer | Chosen probability | First request | Warm repeat |
|---|---|---|---|---|
| Same Artemis II launch, differently worded | `true` | 0.7797 | 7.050 s | 0.293 s |
| Artemis II launch vs Bank of Japan rate increase | `false` | 0.8229 | 0.444 s | 0.240 s |
| NASA Moon mission: science / sports / finance | `science` | 0.9960 | 1.584 s | 0.224 s |

Latency is curl's end-to-end `time_total`, not GPU-only timing. First requests include Metal compilation/warm-up; not a throughput benchmark.

8. `ps -p 75095 -o pid,etime,rss,%cpu,command` — after smoke, RSS **663,872 KiB (~648 MiB)**. RSS alone excludes much of Metal-owned memory.
9. `vmmap -summary 75095` — physical footprint **5.2 GiB**, peak **5.2 GiB**, including **4.2 GiB owned unmapped graphics memory**, after the required smoke examples. This is a snapshot, not a sampled worst-case memory benchmark.
10. Additional real four-question request:

```sh
curl --fail-with-body --silent --show-error --write-out '\nlatency=%{time_total}s\n' \
  http://127.0.0.1:8100/decide -H 'content-type: application/json' -d '{
  "state":"A: NASA launches Artemis II around the Moon. B: Artemis II astronauts lift off on a lunar flyby.",
  "questions":{
    "same_event":{"type":"yesno","instructions":"Are these headlines about the same specific event?"},
    "conflict":{"type":"yesno","instructions":"Do the headlines contradict each other?"},
    "category":{"type":"choice","options":["science","sports","finance"],"instructions":"Choose the best category."},
    "importance":{"type":"score","options":["minor","moderate","major"],"instructions":"Rate the importance of this news."}
  }}'
```

Returned same_event `true` / 0.7581; conflict **`true` / 0.5930 (incorrect)**; category `science` / 0.9958; importance `2` / 0.3898. Latency 6.462 s for the first multi-question batch. Validates shared-prefix batching and real score output, but exposes a real low-confidence contradiction-classification error. No prompt/model substitution or accuracy tuning was done.

11. Ran this extra curl-backed assertion check — all three returned **422**, including the native context overflow `prompt of 5073 tokens exceeds the context window of 4096 tokens`:

```sh
.venv/bin/python - <<'PY'
import json, subprocess

def post(body):
    raw = subprocess.check_output(['curl', '-sS', '-w', '\n%{http_code}',
        'http://127.0.0.1:8100/decide', '-H', 'content-type: application/json',
        '-d', json.dumps(body)], text=True)
    data, status = raw.rsplit('\n', 1)
    return json.loads(data), int(status)

for label, body in [
    ('missing options', {'state':'x','questions':{'q':{'type':'choice'}}}),
    ('duplicate options', {'state':'x','questions':{'q':{'type':'choice','options':['a','a']}}}),
    ('context overflow', {'state':'news ' * 5000,'questions':{'q':{'type':'yesno','instructions':'Is this news?'}}}),
]:
    data, status = post(body)
    assert status == 422, (label, status, data)
    print(label, status, str(data)[:200])
PY
```

12. `git status --short -- model-server` (repository root) — only untracked `model-server/`; no commit made. Virtualenv, weights, logs and bytecode are ignored by `model-server/.gitignore`.

## Limitations / handoff

- The server was left running on `127.0.0.1:8100`, PID **75095**, for coordinator testing. Stop this process with `kill -TERM 75095` when finished. Logs: `model-server/server.log`.
- CPU bf16 and CUDA execution were not exercised; this Mac validation targets the requested preferred MPS backend. No CPU/GPU fallback substitutions were used.
- No Supabase Docker-to-host integration or production load testing: another agent owns backend integration, and localhost-only binding needs explicit forwarding from a container. Concurrent inference is serialized, but no queue/load benchmark was performed.
- Three clear examples establish that the service runs, **not editorial accuracy or probability calibration on The Grapevine**. The observed contradiction error is a reason to validate thresholds with labeled examples before automatic organization relies on it.
- `score` deliberately returns the most probable ordinal level, not the upstream expected-value score; this is documented to keep `probability` the actual probability of the returned answer.
- Installation pins the official inference source and the three core ML dependencies; transitive dependencies are not fully locked. Checkpoint v19 is fetched from its public HF repository's main branch (currently `bb282d786bc251fd4e3068de3ada9ddbb38127cd`); the base revision is resolved from official provenance.
