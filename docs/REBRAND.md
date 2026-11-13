# Руководство по ребрендингу YAiPlayer (White-Label)

В данном документе зафиксирован полный реестр мест кодовой базы и ресурсов, где определены название приложения, идентификаторы пакетов (`applicationId`), цветовая палитра бренда, графические логотипы, ссылки на внешние сервисы (GitHub, Telegram, Web, API) и строки «О программе».

Инструменты автоматизации ребрендинга расположены в каталоге `tools/rebrand/`:
- `tools/rebrand/brand.example.json` — конфигурационный файл шаблона нового бренда.
- `tools/rebrand/apply_brand.py` — утилита поиска и пакетной замены параметров бренда (по умолчанию выполняется в режиме `--dry-run` без изменения файлов на диске).

---

## 1. Реестр расположения параметров бренда

### 1.1 Название приложения (`appName`)
| Путь | Назначение / Ключ |
|---|---|
| `clients/owntv-core/core/src/main/res/values/strings_app.xml` | `app_name` ("YAiPlayer") |
| `clients/owntv-core/core/src/main/res/values-ru/strings_app.xml` | `app_name` ("YAiPlayer") |
| `clients/owntv-core/core/src/main/res/values-de/strings_app.xml` | `app_name` ("YAiPlayer") |
| `clients/owntv-core/core/src/main/res/values/strings_about.xml` | Строки описания плеера в «О приложении» |
| `clients/owntv-core/core/src/main/res/values-ru/strings_about.xml` | Русские строки описания в «О приложении» |
| `clients/owntv-core/core/src/main/res/values-de/strings_about.xml` | Немецкие строки описания в «О приложении» |
| `tools/brand/yai_brand.py` | Константа `BRAND_NAME = "YAiPlayer"` для генератора логотипов |
| `.github/workflows/release.yml` | Названия артефактов `YAiPlayer-TV.apk` и `YAiPlayer-Mobile.apk` |
| `.github/workflows/build.yml` | Названия артефактов сборки в Actions |

### 1.2 Идентификаторы пакетов (`applicationId`)
| Путь | Текущее значение |
|---|---|
| `clients/android-tv/app/build.gradle.kts` | `applicationId = "com.yai.iptv.player.tv"` |
| `clients/android-mobile/app/build.gradle.kts` | `applicationId = "com.yai.iptv.player.mobile"` |

### 1.3 Фирменные цвета (`primaryColor`, `accentColor`)
| Путь | Цвета / Элементы |
|---|---|
| `tools/brand/yai_brand.py` | `BRAND_NAME = "YAiPlayer"`, `DOT = "#22D3EE"`, фон по умолчанию `"#5B3FD6"` |
| `clients/owntv-core/core/src/main/res/values/colors.xml` | Базовые системные цвета |
| `clients/android-tv/app/src/main/java/tv/own/owntv/ui/theme/StageColors.kt` | Палитра интерфейса Android TV |
| `clients/android-mobile/app/src/main/java/tv/own/owntv/mobile/ui/stage/StageTokens.kt` | Палитра интерфейса Android Mobile |

### 1.4 Логотипы и графические ресурсы
| Путь | Описание |
|---|---|
| `tools/brand/yai_brand.py` | Скрипт генерации векторных и растровых иконок, меток и баннеров |
| `clients/owntv-core/core/src/main/res/drawable/` | `ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_banner.xml` |
| `clients/owntv-core/core/src/main/res/drawable-nodpi/` | Растровые фоны и плашки |
| `clients/android-tv/app/src/main/res/drawable*/` | Иконка запуска ТВ и баннер Android TV Leanback Launcher |
| `clients/android-mobile/app/src/main/res/mipmap*/` | Адаптивные иконки мобильного лаунчера |

### 1.5 Внешние адреса (GitHub, Telegram, Web, API)
| Сервис | Путь в проекте | Текущее значение |
|---|---|---|
| GitHub Репозиторий | `clients/owntv-core/core/src/main/java/tv/own/owntv/core/update/UpdateManager.kt` | `"eai-apps/YAiPlayer"` |
| Telegram Канал | `telegram-release/post_release.py`, `telegram-bot/wrangler.jsonc` | `@YAiPlayer`, `https://t.me/YAiPlayer` |
| Telegram Чат | `telegram-release/post_release.py`, `telegram-bot/wrangler.jsonc` | `@YAiPlayer_Chat`, `https://t.me/YAiPlayer_Chat` |
| Telegram Бот скачивания | `telegram-release/post_release.py` | `@YAiPlayer_App_bot`, `https://t.me/YAiPlayer_App_bot?start=dl` |
| Web Сайт | `docs/ROADMAP.md`, `HANDOFF.md` | `https://yai-iptv-player.pages.dev` |
| Cloudflare Sync Worker | `clients/owntv-core/core/src/main/java/tv/own/owntv/core/account/` | `https://yai-iptv-sync-worker.yai-media-player.workers.dev` |

---

## 2. Порядок применения нового бренда

1. Скопировать `tools/rebrand/brand.example.json` в `brand.json` и заполнить параметры нового продукта.
2. Запустить dry-run для предварительной проверки мест замены:
   ```bash
   python tools/rebrand/apply_brand.py --config brand.json
   ```
3. Проверить вывод консоли: скрипт отобразит список затрагиваемых файлов и планируемые замены без модификации файлов.
4. Выполнить фактическую замену (только при необходимости создания отдельной ветки бренда):
   ```bash
   python tools/rebrand/apply_brand.py --config brand.json --apply
   ```
5. Перегенерировать иконки и ресурсы:
   ```bash
   python tools/brand/yai_brand.py
   ```


## Аккаунт и сервер (13.11)
Адрес сервера аккаунтов, сайта-кабинета и tenant — один файл `clients/owntv-core/core/src/main/java/tv/own/owntv/core/account/AccountConfig.kt` (`API_BASE`, `SITE_URL`, `TENANT`, `ENABLED`). У оператора — адрес его сервера (yai-cloud `worker/deploy/install-operator.sh`).
