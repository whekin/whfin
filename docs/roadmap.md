# WHFIN roadmap

Актуальный порядок следующих продуктовых этапов. Этап считается завершённым только после тестов,
проверки реального рендера/поведения на disposable-эмуляторе и отдельного коммита.

## 1. Data Safety

Это следующий этап и обязательная основа перед дальнейшим изменением модели данных.

Статус: этап завершён. Destructive fallback удалён; ручная v1→v2 migration и полный
earliest→current schema-test проходят на disposable-эмуляторе. Текущая DB остаётся v2, поэтому
следующий schema change обязан начинаться с migration v2→N. Версионированный JSON export/restore
доступен в Settings через Storage Access Framework.

- [x] Убрать `fallbackToDestructiveMigration(true)`. Начиная с текущей Room DB v2, каждое изменение
  схемы получает явную миграцию и migration-test.
- [x] Никогда не запускать destructive migration, `pm clear`, uninstall или instrumentation на физическом
  телефоне пользователя. Установка на него — только сохраняющая данные (`install -r` / `android run`).
- [x] Добавить версионированный JSON backup через Storage Access Framework:
  `schemaVersion`, `exportedAt`, версия приложения и основная валюта, затем счета и контейнеры,
  платёжные инструменты, категории, merchants/rules, транзакции, transfer groups, people/allocations,
  debt cases/events и метаданные выписок.
- [x] Не экспортировать сырые SMS, OTP, app-lock secrets, банковские пароли, consent/access/refresh tokens
  и ключи Android Keystore.
- [x] Обычный `.json` является читаемым чувствительным файлом: перед сохранением нужен явный warning.
  Позже поверх того же формата можно добавить зашифрованный `.whfin-backup`.
- [x] Формат сразу проектировать для round-trip restore. Restore полностью заменяет локальные данные
  только после отдельного destructive confirmation; serializer имеет deterministic/round-trip,
  malformed input и version-compatibility tests.
- [x] Android Auto Backup используется как отдельная системная страховка: только Room database и
  non-secret UI/widget preferences, cloud backup только при encryption capability, плюс тот же
  allowlist для device-to-device transfer. Это не заменяет переносимый JSON backup.

## 2. App Lock

Статус: этап завершён. После проверки первого системного device-credential прототипа выбрана отдельная
продуктовая модель: WHFIN никогда не показывает поле системного PIN телефона.

- [x] Собственный четырёхзначный код: две фазы создания, четыре точки и доступная цифровая клавиатура.
  Открытый код не сохраняется; соль и HMAC находятся в private preferences, HMAC-ключ неэкспортируемый
  и создаётся Android Keystore. После пяти ошибок вход блокируется на 30 секунд. Recovery пока нет,
  поэтому setup явно предупреждает, что код нужно запомнить.
- [x] Strong biometric — опциональный быстрый вход через системный `BiometricPrompt`; его negative
  action возвращает на код WHFIN, а не на device credential. Биометрию можно отключить отдельно.
- [x] Настройки: выключено, сразу, через 30 секунд, 1 минуту или 5 минут после ухода в фон.
- [x] Cold start и возврат после timeout не создают financial composition до успешного ввода;
  unavailable/not-enrolled/lockout/cancelled biometric states оставляют доступным WHFIN-код.
- [x] `FLAG_SECURE` скрывает содержимое WHFIN в recent-apps snapshot, пока приложение заблокировано.
- [x] Виджет не показывает баланс. По явному продуктовому решению quick-entry всегда открывает ввод
  расхода напрямую и не требует App Lock: это capture-only поверхность без чтения финансовой истории.
- [x] Код/соль/Keystore key исключены из JSON и Android backup. Если timeout восстановится без локального
  ключа и кода, приложение безопасно сбрасывает блокировку, а не запирает пользователя.
- Будущие банковские токены хранить зашифрованными отдельным ключом Android Keystore. App lock сам по себе
  не является шифрованием Room DB; возможное DB-at-rest encryption — отдельное решение с учётом
  фоновой синхронизации и Glance.

## 3. SMS reliability and diagnostics

Статус: основная локальная надёжность реализована в Room DB v3. Receiver больше не теряет parsing и
account-resolution failures; Settings → SMS diagnostics показывает исход каждого Credo-кандидата.
История за 90 дней имеет отдельное разрешение, prominent disclosure и dry-run до записи.

- [x] Заменить nullable/silent результат importer на явный outcome: imported, duplicate,
  ignored (OTP/rejected/unrelated), unrecognized, needs card mapping и ambiguous account.
- [x] Добавить Settings → SMS diagnostics: новые сообщения показывать с локальным результатом обработки;
  историю телефона сканировать только по отдельному действию пользователя и после prominent disclosure.
- [x] Исторический scan требует `READ_SMS`. Запрашивать его не на старте, ограничивать Credo/разумным периодом,
  не хранить и не загружать полный inbox. Перед release пройти Play restricted-permissions declaration;
  money-management exception вероятно применим, но это должен подтвердить review.
- [x] Для `needs mapping` дать выбрать счёт/карту и сохранить mapping, затем повторить import. Batch import
  выполняется только по явному действию и до записи показывает dry-run summary.
- [ ] Нераспознанное сообщение можно отправить разработчику только через Android Sharesheet. По умолчанию
  payload редактируемый/редактированный; raw body добавляется лишь после отдельного подтверждения.
  Никакой фоновой telemetry или автоматической отправки SMS.
- [~] Проверка: golden/unit tests и injected Credo SMS на disposable emulator выполнены; dry-run существующих SMS на
  OnePlus, затем одна новая реальная операция. На физическом телефоне по-прежнему без instrumentation.

Детальный контракт и текущий диагноз: `docs/sms-import.md`.

## 4. Credo Open Banking feasibility gate

Цель — read-only автоматическая синхронизация счетов, балансов и транзакций. Payment initiation
не входит в этот этап.

- Использовать официальный Open Banking API Credo (Account Information Service), а не scraping
  MyCredo, приватные mobile endpoints или хранение банковского логина/пароля.
- До production-кода подтвердить у Credo/NBG условия onboarding: может ли личный WHFIN получить
  production-доступ, или требуется регистрация/партнёрство с AISP/TPP. Credo публикует sandbox и
  certificate request, но production требует отдельного допуска.
- Первый прототип ограничить sandbox: consent/SCA, список счетов, balances и transactions.
- Если production-доступ возможен, добавить источник `OPEN_BANKING`, состояние consent и его срок,
  sync cursor, last successful sync, retryable/final errors и стабильные external IDs для дедупликации.
- SMS и XLSX остаются независимыми источниками и fallback. API-операции должны проходить через
  существующую реконсиляцию; XLSX остаётся проверяемым источником правды, пока полнота API не доказана.
- Предпочитать redirect/decoupled SCA, где Credo сам показывает банковскую авторизацию. OTP никогда
  не сохранять, не логировать, не экспортировать и не отправлять молча.
- При embedded flow и явном разрешении SMS WHFIN может предложить найденный Credo OTP для вставки
  одним действием пользователя. WHFIN не должен пытаться внедрять код во внешний браузер или
  приложение Credo.

Официальные отправные точки:

- [NBG Open Banking overview](https://nbg.gov.ge/en/page/open-banking)
- [NBG Open Banking Registry](https://nbg.gov.ge/en/page/open-banking-registry)
- [NBG Open Banking API documentation index](https://nbg.gov.ge/en/page/open-banking-detailed-data-and-documentation)
- [Credo Open Banking developer portal](https://openbanking.credo.ge/ob-home/app/api-doc)

## 5. Product work while onboarding is unresolved

Open Banking не должен блокировать локальное развитие приложения. После Data Safety и App Lock
продолжаются visual QA уже реализованной месячной статистики, категории/люди и остальные сценарии.
Production Credo sync начинается только после положительного результата feasibility gate.

## Production readiness (сквозной трек)

Privacy/About, version metadata, author attribution и явная Android backup policy уже добавлены.
Оставшиеся release-gates — public privacy URL/contact, лицензии, encrypted backup option,
release signing, Play Data safety + SMS declaration и полный release QA. Чеклист:
`docs/production-readiness.md`.

## Commit boundaries

1. Safe Room migrations + versioned JSON backup/restore foundation — complete.
2. WHFIN-code/biometric app lock and privacy behavior — complete.
3. SMS structured outcomes, diagnostics/history scan, mapping repair and explicit failure sharing.
4. Credo sandbox spike and documented go/no-go result.
5. Read-only Credo sync, только если production onboarding подтверждён.
