import datetime, os, sys, unittest
sys.path.insert(0, os.path.dirname(__file__))
import post_support as ps


class SupportPost(unittest.TestCase):
    def test_all_points_and_links(self):
        rich, classic = ps.build(datetime.date(2026, 11, 10))
        for body in (rich, classic):
            for w in ("Устройство", "Какое приложение", "Откуда плейлист", "других плеерах", "скриншот", "Журнал",
                      "Экспорт журнала", "YAiPlayer?direct", "YAiPlayer_Chat", "Журнал ошибок"):
                self.assertIn(w, body)

    def test_titles_rotate_every_three_days(self):
        d = datetime.date(2026, 11, 10)
        seen = {ps.title_for(d + datetime.timedelta(days=3 * i)) for i in range(3)}
        self.assertEqual(len(seen), 3)
        self.assertEqual(ps.title_for(d), ps.title_for(d))

    def test_image_exists(self):
        self.assertTrue(os.path.exists(ps.IMAGE))


if __name__ == "__main__":
    unittest.main()
