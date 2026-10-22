import unittest
from types import SimpleNamespace

from fastapi.testclient import TestClient
from pydantic import ValidationError
from strands_decider.schema import SystemOneResponse

from server import DecideRequest, create_app, decisions, native_request


class AdapterTests(unittest.TestCase):
    def test_all_question_types(self):
        request = DecideRequest(state="News", questions={
            "same_event": {"type": "yesno"},
            "category": {"type": "choice", "options": ["science", "sport", "politics"]},
            "severity": {"type": "score", "options": ["low", "medium", "high"]},
        })
        native = native_request(request)
        self.assertEqual(native.questions["same_event"].type, "noul")
        self.assertEqual(native.questions["same_event"].instructions, "same_event")
        self.assertEqual(list(native.questions["category"].criteria), ["science", "sport", "politics"])
        self.assertEqual(native.questions["severity"].criteria, ["low", "medium", "high"])

    def test_selected_probability_not_rescaled_confidence(self):
        response = SystemOneResponse(model="test", usage={}, answers={
            "yes": {"type": "noul", "noul": 0.8},
            "no": {"type": "noul", "noul": 0.1},
            "choice": {"type": "choice", "choice": "science",
                       "probabilities": {"science": 0.7, "sport": 0.2, "politics": 0.1},
                       "confidence": 0.55},
            "score": {"type": "score", "score": 1.1, "legend": {"0": "low", "1": "medium", "2": "high"},
                      "probabilities": {"0": 0.1, "1": 0.7, "2": 0.2}, "confidence": 0.4},
        })
        result = decisions(response)
        self.assertIs(result["yes"].answer, True)
        self.assertIs(result["no"].answer, False)
        self.assertEqual(result["no"].probability, 0.9)
        self.assertEqual(result["choice"].probability, 0.7)
        self.assertEqual(result["score"].answer, 1)
        self.assertEqual(result["score"].probability, 0.7)

    def test_reject_invalid_requests(self):
        for question in [
            {"type": "choice"}, {"type": "choice", "options": ["a"]},
            {"type": "choice", "options": ["a", "a"]},
            {"type": "score", "options": ["a"]},
            {"type": "score", "options": list(map(str, range(11)))},
            {"type": "choice", "options": ["a", " "]},
            {"type": "yesno", "options": ["a", "b"]}, {"type": "noul"},
        ]:
            with self.subTest(question=question), self.assertRaises(ValidationError):
                DecideRequest(state="x", questions={"q": question})
        with self.assertRaises(ValidationError):
            DecideRequest(state="x", questions={})
        for oversized in [
            {"q" * 65: {"type": "yesno"}},
            {"q": {"type": "yesno", "instructions": "x" * 2001}},
            {"q": {"type": "choice", "options": ["a", "b" * 201]}},
        ]:
            with self.subTest(oversized=list(oversized)), self.assertRaises(ValidationError):
                DecideRequest(state="x", questions=oversized)

    def test_http_contract_without_loading_weights(self):
        app = create_app()
        response = SystemOneResponse(model="test", usage={}, answers={
            "same_event": {"type": "noul", "noul": 0.1},
        })
        app.state.engine = SimpleNamespace(
            cfg=SimpleNamespace(device="mps"), evaluate=lambda request: response,
        )
        client = TestClient(app)
        self.assertEqual(client.get("/health").json(), {
            "status": "ok", "model": "strands-decider-2b", "device": "mps",
        })
        self.assertEqual(client.post("/decide", json={
            "state": "x", "questions": {"same_event": {"type": "yesno"}},
        }).json(), {"same_event": {"answer": False, "probability": 0.9}})
        self.assertEqual(client.post("/decide", json={"state": "x", "questions": {}}).status_code, 422)

        def overflow(request):
            raise ValueError("prompt exceeds the context window")

        app.state.engine.evaluate = overflow
        self.assertEqual(client.post("/decide", json={
            "state": "x", "questions": {"same_event": {"type": "yesno"}},
        }).status_code, 422)


if __name__ == "__main__":
    unittest.main()
