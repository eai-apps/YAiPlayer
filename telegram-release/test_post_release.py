#!/usr/bin/env python3
"""
Тесты для модуля автопоста релизов в Telegram (post_release.py).
Запуск: python -m unittest
"""

from __future__ import annotations

import io
import json
import os
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import MagicMock, patch

# Гарантируем корректный импорт post_release независимо от текущей директории запуска
sys.path.insert(0, str(Path(__file__).resolve().parent))

from post_release import (
    build_buttons,
    MAX_ITEMS_COUNT,
    MAX_POST_LENGTH,
    MORE_TEXT,
    extract_items,
    format_release_post,
    main,
    send_release_post,
    send_telegram_message,
    strip_markdown,
)


class TestPostReleaseFormatting(unittest.TestCase):
    """Тестирование форматирования текста поста и разметки."""

    def test_strip_markdown(self):
        """Проверка очистки ссылок, картинок, кода и стилей до текста."""
        sample = (
            "Текст с [ссылкой](https://github.com/org/repo) и "
            "![логотип](https://example.com/logo.png), а также `inline_code` "
            "и **жирным текстом**."
        )
        cleaned = strip_markdown(sample)
        self.assertEqual(
            cleaned,
            "Текст с ссылкой и логотип, а также inline_code и жирным текстом.",
        )

    def test_format_list_markers_and_headers(self):
        """
        Проверка:
        - маркеры -, * и + превращаются в «•»;
        - разметка заголовков (#) убирается до чистого текста;
        - ссылки превращаются в текст;
        - заголовок поста формируется как «<b>YAiPlayer <версия></b>».
        """
        release_data = {
            "tag_name": "v1.3.0",
            "body": (
                "## Что изменилось\n"
                "* Поддержка HLS плеера в [core](https://github.com/org/repo)\n"
                "- Добавлено переключение аудиодорожек\n"
                "+ Улучшена стабильность на слабых ТВ\n"
            ),
            "html_url": "https://github.com/org/repo/releases/tag/v1.3.0",
        }
        post = format_release_post(release_data)

        self.assertIn("<b>YAiPlayer v1.3.0</b>", post)
        self.assertIn("Что изменилось", post)
        self.assertNotIn("##", post)
        self.assertIn("Поддержка HLS плеера в core", post)
        self.assertNotIn("[core]", post)
        self.assertNotIn("https://github.com/org/repo)", post)
        self.assertIn("Добавлено переключение аудиодорожек", post)
        self.assertIn("Улучшена стабильность на слабых ТВ", post)
        self.assertNotIn("Все файлы", post)

    def test_format_list_max_12_items(self):
        """Проверка ограничения списка максимум 12 пунктами."""
        items_15 = [f"* Пункт изменений #{i}" for i in range(1, 16)]
        release_data = {
            "tag_name": "v1.4.0",
            "body": "\n".join(items_15),
            "html_url": "https://github.com/org/repo/releases/tag/v1.4.0",
        }
        post = format_release_post(release_data, max_items=12)

        # Первые 12 пунктов должны присутствовать
        for i in range(1, 13):
            self.assertIn(f"Пункт изменений #{i}", post)

        # Пункты 13, 14, 15 должны отсутствовать
        self.assertNotIn("Пункт изменений #13", post)
        self.assertNotIn("Пункт изменений #14", post)
        self.assertNotIn("Пункт изменений #15", post)

        # Должен быть добавлен маркер усечения
        self.assertIn(MORE_TEXT, post)

    def test_escaping_special_chars(self):
        """Проверка экранирования символов <, >, & в тексте релиза."""
        release_data = {
            "tag_name": "v1.2.1",
            "body": "* Исправлена обработка <video> & <audio> тегов в плейлистах",
            "html_url": "https://github.com/org/repo/releases/tag/v1.2.1?src=gh&ref=main",
        }
        post = format_release_post(release_data)

        self.assertIn("&lt;video&gt; &amp; &lt;audio&gt;", post)
        self.assertNotIn("<video>", post)
        self.assertNotIn("<audio>", post)

    def test_long_text_truncation_boundary(self):
        """
        Проверка обрезки длинного текста (>4000 символов):
        - Длина поста не должна превышать 4000 символов;
        - Обрезка происходит аккуратно по границе пункта;
        - Присутствует строка «… полный список по ссылке».
        """
        # Создаём 10 пунктов по 500 символов каждый (суммарно >5000)
        long_items = [f"* Пункт #{i}: " + ("A" * 480) for i in range(1, 11)]
        release_data = {
            "tag_name": "v2.0.0",
            "body": "\n".join(long_items),
            "html_url": "https://github.com/org/repo/releases/tag/v2.0.0",
        }
        post = format_release_post(release_data, max_length=MAX_POST_LENGTH)

        self.assertLessEqual(len(post), MAX_POST_LENGTH)
        self.assertIn(MORE_TEXT, post)
        # Проверяем, что первый пункт целиком попал
        self.assertIn("Пункт #1:", post)
        # А последние пункты были отброшены
        self.assertNotIn("Пункт #10:", post)
        # Обрезка завершается на корректной границе, а не посреди символов
        self.assertNotIn("https://github.com/org/repo/releases/tag", post)

    def test_asset_links(self):
        release_data = {
            "tag_name": "v2.0.9",
            "body": "* Релиз",
            "html_url": "https://github.com/org/repo/releases/tag/v2.0.9",
            "assets": [
                {"name": "YAiPlayer-TV.apk", "browser_download_url": "https://github.com/o/r/releases/download/v2.0.9/YAiPlayer-TV.apk"},
                {"name": "YAiPlayer-Mobile.apk", "browser_download_url": "https://github.com/o/r/releases/download/v2.0.9/YAiPlayer-Mobile.apk"},
                {"name": "other.txt", "browser_download_url": "https://x/o.txt"},
            ],
        }
        rows = build_buttons(release_data, "@YAiPlayer")
        urls = [b["url"] for r in rows for b in r]
        # прямых ссылок на скачивание нет: ссылку выдаёт бот после подписки
        self.assertIn("https://t.me/YAiPlayer_bot?start=dl", urls)
        self.assertFalse(any("github.com" in u for u in urls))
        self.assertIn("https://t.me/YAiPlayer", urls)
        self.assertIn("https://t.me/YAiPlayer_Chat", urls)
        self.assertFalse(any("v2.0.9/" in u or "other.txt" in u for u in urls))


class TestPostReleaseCLIAndLogic(unittest.TestCase):
    """Тестирование CLI-команд, логики пропуска релизов и отправки."""

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)

    def _write_release_json(self, data: dict) -> str:
        filepath = Path(self.temp_dir.name) / "release.json"
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
        return str(filepath)

    def test_skip_draft(self):
        """Пропуск черновиков (draft: true) с кодом выхода 0."""
        path = self._write_release_json({
            "tag_name": "v1.0.0",
            "draft": True,
            "prerelease": False,
            "body": "* Тестовый драфт",
            "html_url": "https://github.com/org/repo/releases/tag/v1.0.0",
        })

        out = io.StringIO()
        with patch("sys.stdout", out):
            code = main(["--release-json", path])

        self.assertEqual(code, 0)
        self.assertIn("Черновик или пре-релиз, публикация пропущена", out.getvalue())

    def test_skip_prerelease(self):
        """Пропуск пре-релизов (prerelease: true) с кодом выхода 0."""
        path = self._write_release_json({
            "tag_name": "v1.0.0-rc1",
            "draft": False,
            "prerelease": True,
            "body": "* Тестовый релиз-кандидат",
            "html_url": "https://github.com/org/repo/releases/tag/v1.0.0-rc1",
        })

        out = io.StringIO()
        with patch("sys.stdout", out):
            code = main(["--release-json", path])

        self.assertEqual(code, 0)
        self.assertIn("Черновик или пре-релиз, публикация пропущена", out.getvalue())

    def test_behavior_without_token_or_chat_id(self):
        """
        Если токена или chat id нет: напечатать «Telegram не настроен, пост пропущен»
        и выйти с кодом 0.
        """
        path = self._write_release_json({
            "tag_name": "v1.0.0",
            "draft": False,
            "prerelease": False,
            "body": "* Обычный релиз",
            "html_url": "https://github.com/org/repo/releases/tag/v1.0.0",
        })

        # Случай 1: Обе переменные отсутствуют
        with patch.dict(os.environ, {}, clear=True):
            out = io.StringIO()
            with patch("sys.stdout", out):
                code = main(["--release-json", path])
            self.assertEqual(code, 0)
            self.assertIn("Telegram не настроен, пост пропущен", out.getvalue())

        # Случай 2: Есть только токен
        with patch.dict(os.environ, {"TELEGRAM_BOT_TOKEN": "some_token"}, clear=True):
            out = io.StringIO()
            with patch("sys.stdout", out):
                code = main(["--release-json", path])
            self.assertEqual(code, 0)
            self.assertIn("Telegram не настроен, пост пропущен", out.getvalue())

        # Случай 3: Есть только chat id
        with patch.dict(os.environ, {"TELEGRAM_CHAT_ID": "@some_chat"}, clear=True):
            out = io.StringIO()
            with patch("sys.stdout", out):
                code = main(["--release-json", path])
            self.assertEqual(code, 0)
            self.assertIn("Telegram не настроен, пост пропущен", out.getvalue())

    def test_dry_run_mode(self):
        """
        Ключ --dry-run: только напечатать итоговый текст, ничего не отправляя.
        Работает даже при отсутствии токенов.
        """
        path = self._write_release_json({
            "tag_name": "v1.9.0",
            "draft": False,
            "prerelease": False,
            "body": "* Новая фича dry-run",
            "html_url": "https://github.com/org/repo/releases/tag/v1.9.0",
        })

        with patch.dict(os.environ, {}, clear=True):
            out = io.StringIO()
            with patch("sys.stdout", out), patch("urllib.request.urlopen") as mock_url:
                code = main(["--release-json", path, "--dry-run"])

            self.assertEqual(code, 0)
            mock_url.assert_not_called()
            output = out.getvalue()
            self.assertIn("<b>YAiPlayer v1.9.0</b>", output)
            self.assertIn("Новая фича dry-run", output)
            self.assertNotIn("Все файлы", output)

    def test_no_token_leak_on_http_error(self):
        """
        Токен нигде не печатать, в логи и ошибки не выводить.
        При ошибке HTTP печатать только код ответа и поле description.
        """
        secret_token = "123456789:ABCdefGHIjklMNOpqrSTUvwxYZ_SECRET"
        chat_id = "@yaiplayer_channel"
        post_text = "Тестовое сообщение"

        # Создаём фиктивный HTTPError с URL, содержащим секретный токен
        error_url = f"https://api.telegram.org/bot{secret_token}/sendMessage"
        error_json = json.dumps({
            "ok": False,
            "error_code": 400,
            "description": "Bad Request: chat not found",
        }).encode("utf-8")

        mock_fp = io.BytesIO(error_json)
        http_error = urllib.error.HTTPError(
            url=error_url,
            code=400,
            msg="Bad Request",
            hdrs={},
            fp=mock_fp,
        )

        stderr_capture = io.StringIO()
        stdout_capture = io.StringIO()

        with patch("urllib.request.urlopen", side_effect=http_error), \
             patch("sys.stderr", stderr_capture), \
             patch("sys.stdout", stdout_capture):
            success = send_telegram_message(secret_token, chat_id, post_text)

        self.assertFalse(success)
        err_output = stderr_capture.getvalue()
        std_output = stdout_capture.getvalue()

        # Токен не должен присутствовать ни в stderr, ни в stdout
        self.assertNotIn(secret_token, err_output)
        self.assertNotIn(secret_token, std_output)
        self.assertNotIn("bot123456789", err_output)

        # При этом код ответа и description должны быть выведены
        self.assertIn("Ошибка HTTP 400: Bad Request: chat not found", err_output)

    def test_successful_send(self):
        """Успешная отправка сообщения в Telegram при корректном ответе."""
        secret_token = "fake_token"
        chat_id = "@test_channel"
        post_text = "<b>YAiPlayer</b>\n\n• Обновление"

        mock_resp = MagicMock()
        mock_resp.read.return_value = json.dumps({"ok": True, "result": {"message_id": 42}}).encode("utf-8")
        mock_resp.__enter__.return_value = mock_resp

        stdout_capture = io.StringIO()
        with patch("urllib.request.urlopen", return_value=mock_resp), \
             patch("sys.stdout", stdout_capture):
            success = send_telegram_message(secret_token, chat_id, post_text)

        self.assertTrue(success)
        self.assertIn("Пост успешно опубликован в Telegram", stdout_capture.getvalue())


class TestPostWithImage(unittest.TestCase):
    """Картинка идёт в начале поста; без неё или при её отказе уходит обычный текст."""

    def _ok(self):
        resp = MagicMock()
        resp.read.return_value = json.dumps({"ok": True}).encode("utf-8")
        resp.__enter__.return_value = resp
        return resp

    def _image(self):
        f = tempfile.NamedTemporaryFile(suffix=".png", delete=False)
        f.write(b"\x89PNG fake")
        f.close()
        self.addCleanup(os.unlink, f.name)
        return f.name

    def test_short_post_is_one_photo_with_caption(self):
        calls = []
        def fake(req, timeout=0):
            calls.append(req.full_url.rsplit("/", 1)[-1])
            return self._ok()
        with patch("urllib.request.urlopen", side_effect=fake), patch("sys.stdout", io.StringIO()):
            self.assertTrue(send_release_post("tok", "@c", "<b>YAiPlayer v1</b>\n\n• a", self._image()))
        self.assertEqual(calls, ["sendPhoto"])

    def test_long_post_photo_then_text(self):
        calls = []
        def fake(req, timeout=0):
            calls.append(req.full_url.rsplit("/", 1)[-1])
            return self._ok()
        text = "<b>YAiPlayer v1</b>\n\n" + "• строка\n" * 200
        with patch("urllib.request.urlopen", side_effect=fake), patch("sys.stdout", io.StringIO()):
            self.assertTrue(send_release_post("tok", "@c", text, self._image()))
        self.assertEqual(calls, ["sendPhoto", "sendMessage"])

    def test_no_image_sends_text_only(self):
        calls = []
        def fake(req, timeout=0):
            calls.append(req.full_url.rsplit("/", 1)[-1])
            return self._ok()
        with patch("urllib.request.urlopen", side_effect=fake), patch("sys.stdout", io.StringIO()):
            self.assertTrue(send_release_post("tok", "@c", "<b>x</b>", None))
        self.assertEqual(calls, ["sendMessage"])

    def test_photo_failure_falls_back_to_text_without_token_leak(self):
        calls = []
        def fake(req, timeout=0):
            name = req.full_url.rsplit("/", 1)[-1]
            calls.append(name)
            if name == "sendPhoto":
                raise urllib.error.URLError("boom")
            return self._ok()
        err = io.StringIO()
        with patch("urllib.request.urlopen", side_effect=fake), patch("sys.stdout", io.StringIO()), patch("sys.stderr", err):
            self.assertTrue(send_release_post("secret_tok", "@c", "<b>x</b>", self._image()))
        self.assertEqual(calls, ["sendPhoto", "sendMessage"])
        self.assertNotIn("secret_tok", err.getvalue())


if __name__ == "__main__":
    unittest.main()
