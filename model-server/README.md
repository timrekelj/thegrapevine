# Local Strands Decider 2B

Private, non-generative inference using the official pointer-head engine. No remote inference, auth, training, or request persistence. The service binds only to `127.0.0.1:8100`; run a single worker. Requests are serialized because the engine's offsets and MPS execution are not thread-safe.

## Official artifacts

- Code: https://github.com/strands-labs/strands-decider
- Installed revision: `b9fc83626bcca822d00f822d6c2e151b8d244ee9` (includes MPS kernels and base-revision loading).
- Launch/reference v19 weights: https://huggingface.co/StrandsAgents/strands-decider-2B-hobson-v19
- Base: `Qwen/Qwen3.5-2B-Base`, downloaded at the checkpoint provenance revision by the official loader.
- Official instructions: https://github.com/strands-labs/strands-decider/blob/main/docs/inference.md

These are a LoRA adapter plus pointer head, not a vanilla Transformers language model. The newer upstream reference `StrandsAgents/strands-decider-2B-qwen3.5-v1-2610` exists; this service deliberately uses the launch v19, not a silent replacement.

## Setup and run (Apple Silicon)

From the repository root:

```sh
brew install uv
cd model-server
uv venv --python 3.12
uv pip install -r requirements.txt
.venv/bin/python server.py
```

First start downloads the public adapter and approximately 4.5 GB of base weights from Hugging Face. No Hugging Face login is required. For weights confined to this directory, set `HF_HOME="$PWD/.cache/huggingface"` before running. Later starts use the local cache. Download failure aborts startup, rather than claiming healthy or substituting another model.

Device selection: CUDA, then MPS, then CPU. Torso uses bf16; the tiny official head stays fp32 for calibrated probabilities. CPU is functional but bf16 is slower than upstream's fp32 CPU default. No CUDA-specific optional kernels are installed here.

The unwrapped official server can also run with the same environment:

```sh
.venv/bin/strands-decider serve StrandsAgents/strands-decider-2B-hobson-v19 --device mps --port 8099
```

That exposes `/v1/systemone`, not this application's `/decide` contract.

## API

```sh
curl --fail-with-body http://127.0.0.1:8100/health
curl --fail-with-body http://127.0.0.1:8100/decide \
  -H 'content-type: application/json' -d '{
    "state": "A: NASA launches Artemis II around the Moon. B: Artemis II astronauts lift off on a lunar flyby.",
    "questions": {
      "same_event": {"type": "yesno", "instructions": "Are these headlines about the same specific event?"},
      "category": {"type": "choice", "options": ["science", "sports", "finance"], "instructions": "Choose the best newspaper category."}
    }
  }'
```

- Health: `{ "status": "ok", "model": "strands-decider-2b", "device": "mps" }`.
- Decisions: `{ "same_event": { "answer": true, "probability": 0.9 }, ... }` (illustrative probability only).
- `yesno` maps to native `noul`; threshold is 0.5. Probability is `P(true)` for true and `1-P(true)` for false.
- `choice` requires 2–255 distinct nonblank options; returns the winning string and its probability, **not** native normalized confidence.
- `score` requires 2–10 ordered labels, lowest first. Returns the **winning zero-based level index** and its probability. It intentionally does not return native expected-value score, since that fractional value has no individual outcome probability.
- Missing instructions default to the question name. Use explicit instructions for editorial judgments.
- At most 32 questions and 100,000 state characters; question names up to 64 characters, instructions up to 2,000, options up to 200 each. Native context overflow is HTTP 422, never silent truncation. Invalid input also returns 422; unexpected inference errors return 500. The service does not log request bodies.

## Validation

With the server running, in another terminal:

```sh
cd model-server
.venv/bin/python -m unittest -v test_server.py
.venv/bin/python scripts/smoke.py
```

The smoke script actually invokes curl, asserts same/different-event and a three-option choice, and prints first/warm end-to-end latency. Test headlines are synthetic, not verified news. See `VALIDATION.md` for this machine's measured results and limitations.

With the native Supabase runtime, Edge Functions run on the host and can reach this service over localhost. With Docker, their localhost is the container instead; use an explicit local forwarding arrangement if needed. Do not expose this unauthenticated endpoint to the internet.
