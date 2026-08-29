import importlib.util
import pathlib
import unittest


SCRIPT = pathlib.Path(__file__).with_name("verify-sdk-public-safety.py")
SPEC = importlib.util.spec_from_file_location("verify_sdk_public_safety", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class PublicSafetyPathTest(unittest.TestCase):
    def test_rejects_private_capture_directories(self):
        self.assertEqual(
            "private biometric directory",
            MODULE.rejection_reason("research/private-data/session-a/meta.json"),
        )
        self.assertEqual(
            "private biometric directory",
            MODULE.rejection_reason("workspace/raw-captures/take-1/frame.json"),
        )

    def test_rejects_known_payloads_and_credentials(self):
        self.assertEqual(
            "private biometric/signing artifact",
            MODULE.rejection_reason("results/landmarks.bin.gz"),
        )
        self.assertEqual(
            "capture media or private credential suffix",
            MODULE.rejection_reason("docs/demo.mov"),
        )
        self.assertEqual(
            "capture media or private credential suffix",
            MODULE.rejection_reason("keys/development.p12"),
        )

    def test_allows_source_and_aggregate_fixtures(self):
        self.assertIsNone(MODULE.rejection_reason("kmp-facelink/src/commonTest/aggregate.json"))
        self.assertIsNone(MODULE.rejection_reason("docs/architecture.svg"))
        self.assertIsNone(MODULE.rejection_reason("androidApp/src/main/res/drawable/logo.png"))


if __name__ == "__main__":
    unittest.main()
