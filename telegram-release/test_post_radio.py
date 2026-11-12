import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import post_radio  # noqa: E402


class RadioPostTest(unittest.TestCase):
    def test_text_mentions_site_app_and_player(self):
        rich, classic = post_radio.build()
        self.assertIn("<tg-slideshow>", rich)
        self.assertEqual(rich.count("tg://photo"), len(post_radio.IMAGES))
        self.assertIn("в главном меню", classic.lower().replace("в главном меню плеера", "в главном меню"))
        self.assertIn("выбора профиля", classic)
        self.assertIn(post_radio.SITE_URL, classic)

    def test_two_url_buttons_and_no_store_names(self):
        flat = [b for row in post_radio.BUTTONS for b in row]
        self.assertTrue(all(b["url"].startswith("https://") for b in flat))
        self.assertEqual(post_radio.BUTTONS[0][0]["url"], post_radio.SITE_URL)
        self.assertEqual(post_radio.BUTTONS[0][1]["url"], post_radio.APK_URL)
        rich, classic = post_radio.build()
        for word in ("rustore", "play market", "google play"):
            self.assertNotIn(word, (rich + classic).lower())

    def test_images_exist(self):
        root = os.path.join(os.path.dirname(__file__), "..")
        for p in post_radio.IMAGES:
            self.assertTrue(os.path.exists(os.path.join(root, p)), p)


if __name__ == "__main__":
    unittest.main()
