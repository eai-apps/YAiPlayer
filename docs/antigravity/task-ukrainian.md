# Задание для Antigravity: украинский язык (uk) в YAiPlayer

Владелец 21.11 (п.93). Перевод делает Antigravity, Claude потом проверяет и заливает в бету.

## Где работать
- Ветка: `test/account-login` (бета) репозитория `eai-apps/YAiPlayer`.
- Папка на ПК: `G:\evvant\Projects\Projects\TempCode\yaiplayer-uk` (клон ветки беты).
- **Ничего не пушить в GitHub.** Только локальный коммит в новой ветке `feat/ukrainian` — Claude сам проверит и зальёт.

## Источник переводов
1. Сначала взять готовый украинский перевод апстрима OwnTV с Weblate: https://hosted.weblate.org/projects/owntv/
   (у каждого компонента: «Files» → «Download translation» → Ukrainian, формат Android XML).
   Брать ТОЛЬКО строки, у которых ключ (`name`) есть в нашем `values/` и смысл английского оригинала совпадает.
2. Всё, чего нет на Weblate (наши строки: аккаунт, пульт, «Ускорить ТВ», спорт, профили, бета, радио и т.д.),
   перевести самому с английского `values/`, сверяясь с русским `values-ru/` (по смыслу и тону — как в русском).

## Какие файлы создать
| Откуда (оригинал) | Куда (украинский) |
|---|---|
| `clients/owntv-core/core/src/main/res/values/*.xml` (31 файл strings*.xml) | `clients/owntv-core/core/src/main/res/values-uk/` — те же имена файлов |
| `clients/android-tv/app/src/main/res/values/strings.xml` | `clients/android-tv/app/src/main/res/values-uk/strings.xml` |
| `clients/android-tv/app/src/radio/res/values/strings_radio.xml` | `clients/android-tv/app/src/radio/res/values-uk/strings_radio.xml` |
| `clients/android-mobile/app/src/radio/res/values/strings_radio.xml` | `clients/android-mobile/app/src/radio/res/values-uk/strings_radio.xml` |

`clients/tv-launcher` не трогать (там украинский уже есть).

## Правила перевода (строго)
- Переводить только `<string>`, `<plurals>`, `<string-array>`. Строки с `translatable="false"` НЕ копировать.
- Ключи (`name`) — точь-в-точь как в оригинале, ничего не добавлять и не удалять.
- Плейсхолдеры не трогать: `%1$s`, `%2$d`, `%s`, `%d`, `%%`, `\n`, `{...}`, HTML-теги `<b>`, `<xliff:g>` — те же и в том же количестве.
- Апостроф в украинском (`м'ясо`, `пам'ять`, `з'єднання`) — в XML только `\'` или типографский `ʼ`. Голый `'` ломает сборку.
- Кавычки — «ёлочки», как в русском.
- `<plurals>`: для uk нужны формы `one`, `few`, `many`, `other`.
- Название приложения **YAiPlayer**, а также Stalker, M3U, Xtream, EPG, MAC, Chromecast, Android TV — не переводить.
- Термины: «канал» — канал, «телепрограма» — телепрограма, «пульт» — пульт, «приставка» — приставка, «плейлист» — плейлист, «обране» — обране, «архів» — архів.
- Не придумывать новый смысл, не сокращать, не добавлять эмодзи.

## Включить язык в приложении
1. В трёх одинаковых файлах `clients/owntv-core/tools/i18n/locales.json`, `clients/android-tv/tools/i18n/locales.json`,
   `clients/android-mobile/tools/i18n/locales.json` у записи `"id": "uk"` поставить `"packaged": true` и `"pickerVisible": true`.
   Файлы должны остаться побайтно одинаковыми.
2. Перегенерировать список языков: в папке `clients/owntv-core` выполнить
   `python tools/i18n/gen_supported_locales.py generate`.

## Проверка (обязательно, до отчёта)
В папке `clients/owntv-core`:
```
python tools/i18n/check_weblate_format.py check
python tools/i18n/validate_strings.py --report text
python tools/i18n/gen_supported_locales.py check
```
Все три — без ошибок. В отчёте показать строку покрытия `uk` из `validate_strings` (должно быть ≥ 95 %).

## Правило против зацикливания
Если проверка падает и 1–2 попытки исправить не помогли — остановиться и написать «не справился, проверь» с текстом ошибки.

## Отчёт
- Сколько строк взято с Weblate, сколько переведено самостоятельно.
- Покрытие uk.
- Список созданных/изменённых файлов.
- Хэш локального коммита в ветке `feat/ukrainian`.
