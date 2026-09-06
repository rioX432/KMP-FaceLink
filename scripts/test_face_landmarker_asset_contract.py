import hashlib
import tempfile
import unittest
import zipfile
from pathlib import Path

from scripts.verify_face_landmarker_asset_contract import (
    BLENDSHAPE_SOURCE,
    MODEL_ENTRY,
    canonical_labels,
    validate_labels,
    validate_task,
)


class FaceLandmarkerAssetContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.canonical = canonical_labels(BLENDSHAPE_SOURCE)
        self.expected = ["_neutral", *(x for x in self.canonical if x != "tongueOut")]

    def test_verified_label_set_is_neutral_plus_51(self) -> None:
        validate_labels("\0".join(self.expected).encode("ascii"), self.canonical)
        self.assertEqual(52, len(self.expected))

    def test_missing_cheek_label_fails(self) -> None:
        payload = "\0".join(x for x in self.expected if x != "cheekPuff").encode("ascii")
        with self.assertRaisesRegex(AssertionError, "cheekPuff"):
            validate_labels(payload, self.canonical)

    def test_tongue_out_in_asset_fails(self) -> None:
        payload = "\0".join([*self.expected, "tongueOut"]).encode("ascii")
        with self.assertRaisesRegex(AssertionError, "tongueOut"):
            validate_labels(payload, self.canonical)

    def test_task_hash_is_bound(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            task = Path(directory) / "model.task"
            with zipfile.ZipFile(task, "w") as archive:
                archive.writestr(MODEL_ENTRY, "\0".join(self.expected).encode("ascii"))
            wrong_hash = "0" * 64
            with self.assertRaisesRegex(AssertionError, "SHA-256"):
                validate_task(task, BLENDSHAPE_SOURCE, wrong_hash)
            validate_task(task, BLENDSHAPE_SOURCE, hashlib.sha256(task.read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
