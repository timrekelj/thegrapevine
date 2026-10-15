"""Local typed decisions using the official Strands pointer-head engine."""

from contextlib import asynccontextmanager
from threading import Lock
from typing import Literal

import torch
import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict, Field, model_validator
from strands_decider.infer import EngineConfig, SystemOneEngine
from strands_decider.modeling import StrandsDeciderModel
from strands_decider.schema import SystemOneRequest, SystemOneResponse

CHECKPOINT = "StrandsAgents/strands-decider-2B-hobson-v19"


class Question(BaseModel):
    model_config = ConfigDict(extra="forbid")
    type: Literal["yesno", "choice", "score"]
    options: list[str] | None = None
    instructions: str | None = None

    @model_validator(mode="after")
    def validate_options(self):
        if self.type == "yesno":
            if self.options is not None:
                raise ValueError("yesno does not accept options")
        else:
            limit = 10 if self.type == "score" else 255
            if not self.options or not 2 <= len(self.options) <= limit:
                raise ValueError(f"{self.type} requires 2–{limit} options")
            if any(not option.strip() for option in self.options):
                raise ValueError("options must not be blank")
            if len(set(self.options)) != len(self.options):
                raise ValueError("options must be unique")
        return self


class DecideRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    state: str = Field(max_length=100_000)
    questions: dict[str, Question] = Field(min_length=1, max_length=32)


class Decision(BaseModel):
    answer: str | bool | int
    probability: float = Field(ge=0, le=1)


def native_request(request: DecideRequest) -> SystemOneRequest:
    questions = {}
    for name, question in request.questions.items():
        native = {
            "type": "noul" if question.type == "yesno" else question.type,
            "instructions": question.instructions or name,
        }
        if question.type == "choice":
            native["criteria"] = dict.fromkeys(question.options)
        elif question.type == "score":
            native["criteria"] = question.options
        questions[name] = native
    return SystemOneRequest(state=request.state, questions=questions)


def decisions(response: SystemOneResponse) -> dict[str, Decision]:
    result = {}
    for name, answer in response.answers.items():
        if answer.type == "noul":
            chosen = answer.noul >= 0.5
            probability = answer.noul if chosen else 1 - answer.noul
        elif answer.type == "choice":
            chosen = answer.choice
            probability = answer.probabilities[chosen]
        else:
            # Select a real level: an expected score has no probability of its own.
            level = max(answer.probabilities, key=answer.probabilities.get)
            chosen = int(level)
            probability = answer.probabilities[level]
        result[name] = Decision(answer=chosen, probability=round(probability, 4))
    return result


def create_app() -> FastAPI:
    lock = Lock()

    @asynccontextmanager
    async def lifespan(app):
        device = "cuda" if torch.cuda.is_available() else (
            "mps" if torch.backends.mps.is_available() else "cpu"
        )
        model = StrandsDeciderModel.load(CHECKPOINT)
        engine = SystemOneEngine(model, EngineConfig(
            device=device, strict_window=True, max_batch=8,
            max_batch_tokens=8192, model_name="strands-decider-2b",
        ))
        # Preserve bf16 torso on CPU too; upstream defaults to faster but larger fp32.
        if device == "cpu":
            engine.model.torso.to(torch.bfloat16)
        app.state.engine = engine
        yield
        del app.state.engine

    app = FastAPI(title="The Grapevine decision model", lifespan=lifespan)

    @app.get("/health")
    def health():
        return {"status": "ok", "model": "strands-decider-2b",
                "device": app.state.engine.cfg.device}

    @app.post("/decide", response_model=dict[str, Decision])
    def decide(request: DecideRequest):
        try:
            # The official engine stores mutable request offsets; MPS is not concurrent.
            with lock:
                return decisions(app.state.engine.evaluate(native_request(request)))
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

    return app


if __name__ == "__main__":
    uvicorn.run(create_app(), host="127.0.0.1", port=8100, workers=1)
