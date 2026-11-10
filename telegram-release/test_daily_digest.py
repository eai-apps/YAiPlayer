import os, sys, unittest
from datetime import datetime
sys.path.insert(0, os.path.dirname(__file__))
import daily_digest as d

REL = [
    {"tag_name": "v3.0.1", "body": "- Архив: быстрый переход к вчерашним передачам.\n"},
    {"tag_name": "v3.0.2", "body": "- Архив: значок архива в списках.\n- Исправлено: сериалы больше не подменяют серию.\n- Внутренние улучшения и исправления\n"},
    {"tag_name": "v3.0.3", "body": "- «Ускорь телевизор»: мастер.\n"},
]


class DigestTest(unittest.TestCase):
    def test_one_line_per_theme_and_hidden_features_skipped(self):
        rich, classic = d.build(REL, datetime(2026, 11, 9), datetime(2026, 11, 10))
        self.assertEqual(rich.count("<b>Архив</b>"), 1)
        self.assertNotIn("Ускорь", rich + classic)
        self.assertIn("<table>", rich)
        self.assertIn("<details>", rich)
        self.assertIn("<blockquote expandable>", classic)
        self.assertIn("🛠", classic)

    def test_nothing_to_say(self):
        self.assertIsNone(d.build([{"tag_name": "v3.0.4", "body": ""}], datetime(2026, 11, 9), datetime(2026, 11, 10)))


if __name__ == "__main__":
    unittest.main()
