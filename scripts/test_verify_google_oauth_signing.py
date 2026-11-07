import unittest

from verify_google_oauth_signing import compact_sha1, registered_signers


class OAuthSignatureTest(unittest.TestCase):
    def setUp(self):
        self.sha = "ccddeeb6f784d00fab77f98bd3ea917512fe4fe3"
        self.config = {
            "client": [{
                "client_info": {
                    "android_client_info": {"package_name": "com.denemelab.preview"}
                },
                "oauth_client": [
                    {"client_type": 1, "android_info": {
                        "package_name": "com.denemelab.preview",
                        "certificate_hash": self.sha.upper()}},
                    {"client_type": 3, "client_id": "example.apps.googleusercontent.com"}
                ]
            }]
        }

    def test_android_sha_match(self):
        self.assertEqual(registered_signers(self.config, "com.denemelab.preview"), {self.sha})

    def test_wrong_package_does_not_authenticate(self):
        self.assertFalse(registered_signers(self.config, "com.other.app"))

    def test_missing_certificate_fails_closed(self):
        self.config["client"][0]["oauth_client"] = [{"client_type": 3}]
        self.assertEqual(registered_signers(self.config, "com.denemelab.preview"), set())

    def test_malformed_cert_is_not_accepted(self):
        self.config["client"][0]["oauth_client"][0]["android_info"]["certificate_hash"] = "abc"
        self.assertEqual(registered_signers(self.config, "com.denemelab.preview"), set())

    def test_colon_separated_fingerprint_matches(self):
        spaced = ":".join(self.sha[i:i+2] for i in range(0,40,2))
        self.assertEqual(compact_sha1(spaced), self.sha)


if __name__ == "__main__":
    unittest.main()
