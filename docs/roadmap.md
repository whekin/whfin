# WHFIN roadmap

Актуальный порядок следующих продуктовых этапов. Этап считается завершённым только после тестов,
проверки реального рендера/поведения на disposable-эмуляторе и отдельного коммита.

## Текущий порядок приоритетов

1. ~~Пересобрать SMS ingestion вокруг отдельных monitoring/routing/import~~ — сделано: Unrouted
   operations в Feed, contextual resolver для одиночных и grouped операций, пользовательский Bank SMS
   вместо diagnostics.
2. ~~Одноэкранный Welcome choice, bank-centric Personal setup и временный Demo workspace без
   глобального switch~~ — сделано; детали: `docs/first-run-demo-and-bank-sms.md`.
3. После нового flow выполнить осторожный dry-run реальных SMS на OnePlus без записи до явного
   подтверждения. **Единственный незакрытый шаг SMS-этапа.**
4. Выделить bank-neutral границу импорта и добавить поддержку выписок **TBC** как следующий
   банковский интеграционный срез.
5. Реализовать небольшой **crypto watch-only MVP** на уже существующей модели: EVM + Tron,
   ETH/TRX/USDT, нативные балансы и ручное обновление без истории/DeFi/background sync.
6. После проверки общей банковской границы на TBC добавить **Bank of Georgia (BOG)** тем же путём.
7. Провести отдельный **Google Play release** этап: публичные privacy URL/contact, release signing, Play Data Safety
   и SMS declaration, полный device/accessibility QA.

## 1. Data Safety

Статус: этап завершён. Destructive fallback удалён; ручная v1→v2 migration и полный
earliest→current schema-test проходят на disposable-эмуляторе. Текущая DB — v4 с явными
v1→v2, v2→v3 и v3→v4 migrations, поэтому следующий schema change обязан получить migration v4→N.
Версионированный JSON export/restore
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
- [x] Обычный `.json` является читаемым чувствительным файлом и показывает явный warning;
  основной encrypted export использует portable `.whfin-backup` с passphrase-derived ключом.
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

Статус: кодовая часть этапа закрыта. Monitoring, routing и import разделены; Unrouted operations живут
в `sms_diagnostics` и проецируются в Feed вне ledger; contextual resolver закрывает одиночные и grouped
случаи; пользовательская поверхность называется Bank SMS. История за 90 дней имеет отдельное разрешение,
prominent disclosure и dry-run до записи. Не закрыт только ручной OnePlus dry-run.

- [x] Заменить nullable/silent результат importer на явный outcome: imported, duplicate,
  ignored (OTP/rejected/unrelated), unrecognized, needs card mapping и ambiguous account.
- [x] Добавить Settings → SMS diagnostics: новые сообщения показывать с локальным результатом обработки;
  историю телефона сканировать только по отдельному действию пользователя и после prominent disclosure.
- [x] Автоматический импорт по умолчанию выключен и требует хотя бы одну явную привязку последних
  четырёх цифр карты к банковскому ledger; тот же gate проверяется в BroadcastReceiver.
- [x] Исторический scan требует `READ_SMS`. Запрашивать его не на старте, ограничивать Credo/разумным периодом,
  не хранить и не загружать полный inbox. Перед release пройти Play restricted-permissions declaration;
  money-management exception вероятно применим, но это должен подтвердить review.
- [x] Для `needs mapping` дать выбрать счёт/карту и сохранить mapping, затем повторить import. Batch import
  выполняется только по явному действию и до записи показывает dry-run summary.
- [x] Нераспознанное сообщение можно отправить разработчику только через Android Sharesheet. По умолчанию
  payload редактируемый/редактированный; raw body добавляется лишь после отдельного подтверждения.
  Никакой фоновой telemetry или автоматической отправки SMS. Проверено end-to-end на disposable Pixel:
  synthetic failure → безопасный editor → exact-raw confirmation → editor → системный Sharesheet.
- [x] Разделить monitoring, routing и import: явное включение monitoring принимает будущие Credo SMS
  даже без card mapping, но не мутирует ledger до выбора счёта.
- [x] Показывать parsed Unrouted operations приглушёнными строками Feed без участия в balance,
  day/month totals и statistics; statement-first reconciliation прикрепляет SMS без дубля.
- [x] Добавить contextual Routing resolver с возвратом после создания недостающего ledger и поддержкой
  grouped transfer/conversion до атомарного создания настоящих legs.
- [x] Пересобрать Settings → SMS diagnostics в Bank SMS: status → needs attention → recent activity →
  cards/accounts → optional recent scan → troubleshooting.
- [x] Выбор счёта для карточного SMS одним действием сохраняет routing, подтверждает явно проверенную
  операцию и backfill-ит совместимые queued сообщения той же карты; автоматически привязанные соседи
  остаются PENDING.
- [~] Проверка: golden/unit tests и injected Credo SMS на disposable emulator выполнены; dry-run существующих SMS на
  OnePlus, затем одна новая реальная операция. На физическом телефоне по-прежнему без instrumentation.

Детальный технический контракт: `docs/sms-import.md`. Принятый следующий продуктовый flow:
`docs/first-run-demo-and-bank-sms.md`.

## 4. Private MyCredo connector (experimental dogfood)

Цель — прямо сейчас убрать ручной выбор восьми XLSX, не выдавая нестабильный веб-протокол за
production bank sync.

- [x] Изолировать read-only adapter: login/OTP, accounts и экспорт XLSX; платежные mutations отсутствуют.
- [x] Пускать каждую скачанную выписку через существующий `StatementImporter`, дедуп и реконсиляцию.
- [x] Хранить optional пароль только в отдельном AES-GCM store с Android Keystore key; исключить secret
  preferences, OTP, access/refresh tokens из Android backup и переносимого JSON.
- [x] Первый этап оставить foreground-only: access/refresh tokens не персистятся, фоновых retry нет,
  OTP не читается и не подтверждается автоматически.
- [x] Проверить реальный username/password → OTP → список ledger’ов и импорт XLSX на OnePlus вручную, без передачи
  credentials разработчику и без instrumentation на пользовательском устройстве.
- [ ] После dogfood решить, нужен ли session refresh/background sync; до этого не расширять secret scope.

### Immediate dogfood UX

- [x] Объединить ручные и MyCredo-выписки в одном журнале с явным provenance. Повторную запись можно
  удалить только если импорт ничего не добавил, не реконсилировал и не создал review items.
- [x] Сделать Accounts семантическим: тап по валютному ledger открывает его транзакции; редактирование
  вынести в явное действие; добавить пользовательское имя IBAN-контейнера и разделить everyday от
  savings/deposits без ложного объединения разных валют.
- [x] Заменить системное OTP-поле на локальную четырёхточечную WHFIN-клавиатуру, не сохраняя OTP и не
  включая автоматическое чтение/подтверждение.
- [ ] Полный rollback результативной выписки не предлагать, пока транзакции и изменения реконсиляции
  не получат обратимый import provenance. Удаление audit record при сохранённых данных запрещено.

Стабильный контракт и failure policy: `docs/credo-private-sync.md`.

## 5. Multi-bank statements: TBC first, BOG second

Цель — расширить уже проверенную модель «выписка = источник правды» на другие грузинские банки,
не копируя Credo-специфичную логику в UI, Room и реконсиляцию.

- [x] Выделить bank-neutral adapter boundary: банковский parser преобразует исходный файл в общий
  statement model, а дедуп, импорт, reconciliation, coverage/history и review queue остаются общими.
  Контракт и правила нового adapter: `docs/statement-import.md`.
- [ ] **TBC — приоритет №1:** собрать только приватные локальные примеры доступных экспортов,
  зафиксировать формат/варианты и реализовать ручной statement import через существующий pipeline.
- [ ] Проверить TBC на synthetic golden fixtures, приватных локальных файлах и disposable-эмуляторе:
  opening/closing balance, цепочка балансов, multi-currency ledgers, transfers, fees, dedup и повторный импорт.
- [ ] Не добавлять реальные TBC-файлы, IBAN, карты, имена или суммы в репозиторий, previews и screenshots.
- [ ] **BOG — банковский приоритет №2:** после того как TBC подтвердит общую границу, добавить отдельный parser
  и тот же набор invariants без второго импортного workflow.
- [ ] SMS и push notifications для каждого банка считать отдельными этапами. Поддержка statement-файла
  не должна автоматически обещать background sync.

## 6. Crypto watch-only MVP

Это не greenfield: Room уже хранит `WalletAddress` и chain-specific `CryptoAsset`, account связывает
address×asset, формы умеют создать CRYPTO/WALLET, а backup сохраняет эти таблицы. Не хватает корректной
сетевой границы и получения баланса.

- [x] Модель address/network + chain-specific asset существует; символ не используется как identity.
- [ ] Заменить эвристику `0x → Ethereum, всё остальное → Tron` на явный выбор сети и строгую
  network-specific валидацию адреса. Не показывать BTC/TON как рабочие варианты до их реализации.
- [ ] Ввести read-only `CryptoBalanceProvider` boundary без seed phrase/private key и первый
  foreground/manual refresh с явными loading/partial/error/last-updated состояниями.
- [ ] Первый scope: Ethereum mainnet ETH + ERC-20 USDT и Tron mainnet TRX + TRC-20 USDT.
- [ ] Добавить отдельный current-balance snapshot с `observedAt` и миграцией Room. Не изображать
  сетевой balance как фальшивую transaction: текущие account balances считаются суммой операций.
- [ ] Хранить on-chain amount в точных base units/decimals, а не в fiat minor-unit предположениях;
  повторное обновление баланса должно быть идемпотентным.
- [ ] Показывать нативные crypto-балансы в Accounts без ложного сложения с GEL. Рыночные цены,
  GEL-конвертация и timestamp котировки — отдельный второй slice после надёжных on-chain balances.
- [ ] Покрыть provider contract локальным fake, address validation unit-тестами и UI states
  light/dark/font 1.5; реальный read-only адрес проверять без сохранения чувствительных данных.

Не входят в MVP: seed/private keys, отправка транзакций, swap/bridge execution, DeFi positions,
полная on-chain transaction history, background polling, Bitcoin и TON.

## 7. Google Play release

Цель — не «сразу production rollout», а воспроизводимая release-сборка и безопасный проход через
internal/closed testing до любого публичного распространения.

- [ ] Опубликовать privacy policy на стабильном HTTPS URL и указать реальный support contact.
- [ ] Выбрать лицензию/режим исходников и собрать полные third-party notices.
- [ ] Создать release signing key вне репозитория, документировать recovery/rotation и настроить
  монотонный `versionCode` + release notes.
- [ ] Подготовить Play Console listing: название, short/long description, feature graphic,
  phone screenshots и widget screenshots.
- [ ] Заполнить Data Safety и restricted SMS permission declaration до загрузки production-track APK.
- [ ] Пройти internal testing, затем closed testing; обработать Play pre-launch accessibility/stability
  findings до решения о public rollout.
- [ ] Выполнить release QA matrix из `docs/production-readiness.md` и сохранить mapping/symbol artifacts.

## 8. Остальная продуктовая работа

После Data Safety, App Lock и основной UI-проходки локальное развитие продолжается независимо от
внешних партнёрств: категории/люди, AI-assisted analysis, push ingestion и другие сценарии.

Официальный доступ к банковским API не входит в текущий roadmap. Вернуться к его оценке можно только
после реального публичного запуска, если пользовательская ценность оправдает onboarding с банками.

## Commit boundaries

1. Safe Room migrations + versioned JSON backup/restore foundation — complete.
2. WHFIN-code/biometric app lock and privacy behavior — complete.
3. SMS structured outcomes, diagnostics/history scan, mapping repair and explicit failure sharing.
4. Bank-neutral statement adapter + TBC parser/import.
5. Crypto network/address validation + read-only balance provider + EVM/Tron MVP.
6. BOG parser/import поверх подтверждённой общей границы.
7. Google Play internal/closed release track.
