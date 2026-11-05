# Автопост релизов в Telegram

Каждый релиз (`.github/workflows/release.yml`, шаг «Post to Telegram channel») публикуется в канал:
версия, «что нового» и прямые ссылки на APK (TV и телефон).

- Текст «что нового» берётся из `docs/WHATS_NEW.md` (по-русски, пункты `- ...`). Перед каждым изменением
  для пользователей дописывайте туда пункт; после релиза файл очищается автоматически.
- Секреты репозитория (Settings → Secrets and variables → Actions): `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`
  (для публичного канала — `@YAiPlayer`). Бот должен быть администратором канала с правом публикации.
  Без секретов шаг просто пропускается.
- Сами APK не отправляются файлом: Bot API принимает файлы до 50 МБ, а APK ~58 МБ. Вместо этого — прямые ссылки.
- Проверка текста без отправки: `python3 telegram-release/post_release.py --release-json release.json --dry-run`.
- Тесты: `python3 -m unittest telegram-release/test_post_release.py`.
