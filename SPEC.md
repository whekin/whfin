# whfin — personal finance tracker (Android)

Замена ZenMoney под личные сценарии. Главный принцип: **минимум ручного труда** —
транзакции приходят сами (SMS), сверяются автоматически (выписка), категории запоминаются навсегда.

## Боли ZenMoney, которые решаем

- Ненадёжный банк-синк, постоянный рассинхрон
- Не запоминает мерчант → категория (магазин, перевод → аренда)
- Неудобный учёт долгов
- Корректировка баланса портит статистику
- UI

## Источники данных

| Источник | Роль | Статус транзакции |
|---|---|---|
| SMS банка (Credo) | реальное время, черновик | `pending` |
| Excel-выписка (MYCREDO xlsx) | источник правды, сверка | `confirmed` |
| Credo Open Banking (после onboarding) | read-only автоматический sync | `confirmed` |
| Ручной ввод / виджет | кеш, корректировки | `manual` |
| Крипто watch-адреса (позже) | TrustWallet, EVM/Tron read-only | `confirmed` |

### Реконсиляция (ядро анти-рассинхрона)

При импорте выписки: матчим pending-SMS-транзакции по **нормализованный мерчант + дата покупки**
(дата покупки есть в SMS и внутри Description выписки; дата списания на 1–2 дня позже).
Сумму матчим с допуском: FX-платежи в SMS в валюте покупки (USD/EUR/GBP), в выписке — GEL по курсу расчёта.
Выписка перезаписывает черновик и обогащает (Beneficiary Name + IBAN). Несматченные строки выписки → новые confirmed.
Balance в каждой строке выписки → сверка цепочки баланса.

### Формат выписки Credo (проверено на приватных тестовых выписках)

- Лист `Account Details`: holder, period, IBAN, currency, opening/closing balance
- Лист `Transactions`: Date, Operation (17 грузинских типов — стабильные ключи), Turnover DB/Cr, Balance, Description, Beneficiary Name/Account
- Карточные операции (`საბარათე ოპერაცია`): `გადახდა - {MERCHANT} {AMOUNT} GEL {дата покупки}`
- Переводы между своими счетами + конвертации = transfer (не расход/доход!), их ~170/год
- Комиссии (`ლარის გადარიცხვის საკომისიო`) отдельными строками сразу за родительским переводом — приклеивать

### Формат SMS Credo (проверено на приватных fixtures)

6 типов: Payment, Outgoing transfer, Incoming transfer, Transfer between accounts, Currency exchange, Rejected payment (скип).
Мусор: `CODE: NNNNNN confirms...`, хвост `want to split the bill? ...`.

**Ловушки:**
- Два формата даты: Payment `dd/MM/yyyy HH:mm:ss` (24ч), переводы `M/d/yyyy h:mm:ss AM/PM`
- FX-платежи: сумма в валюте покупки, Balance в GEL
- Мерчант: `spar>Tbilisi`, `NIKORA TRADE JSC>DIDGORI` → нормализация: lowercase, отрезать `>город страна`, trim + таблица алиасов
- `Card N ****0001 / ****0002` → маппинг карта→счёт (две синтетические карты, один GEL-счёт)
- В SMS перевода нет получателя — обогащается выпиской

## Модель данных (Room, суммы в minor units / Long)

- **Account**: name, type (`BANK | CASH | SAVINGS | CRYPTO | PERSON`), currency, iban?, isArchived; card masks отдельной таблицей
- **Transaction**: accountId, amountMinor (signed), currency, origAmountMinor?/origCurrency? (FX),
  occurredAt (дата покупки), postedAt? (дата списания), merchantId?, counterpartyName/Iban?,
  categoryId?, note, status (`PENDING | CONFIRMED | MANUAL`), source (`SMS | STATEMENT | MANUAL | ADJUSTMENT | CRYPTO`),
  transferPeerId? (парная транзакция перевода), balanceAfterMinor?, externalKey (дедуп)
- **Category**: дерево 2 уровня, name, icon, color, kind (`EXPENSE | INCOME`), isSystem; системная **Unaccounted**
- **Merchant**: normalizedKey, displayName, categoryId (память навсегда); **MerchantAlias**: pattern → merchant

## Ключевые фичи

1. **Категоризация-память**: раз присвоил мерчанту категорию — она запоминается; то же работает для отправителей (`EXAMPLE EMPLOYER` → Salary) и получателей (`EXAMPLE LANDLORD` → Rent)
2. **Корректировка баланса** — транзакция первого класса: разница → системная категория Unaccounted, видна в статистике отдельно, ничего не ломает
3. **Виджет** (Glance) — добавить расход не открывая приложение: сумма + категория + счёт; кеш-шаблоны одним тапом
4. **Статистика по месяцам**: свайп по месяцам, доход/расход/дельта, донат по категориям, сравнение месяц-к-месяцу, тренды; мультивалютность через пересчёт в базовую
5. **Накопления**: SAVINGS-счета с опциональной целью; переводы туда = «отложено», не расход
6. **Долги**: отдельные `DebtCase` + журнал `DebtEvent`, а не PERSON-счета. Долг бывает связан с
   движением по реальному счёту или создан без движения. Возврат может прийти другой суммой, валютой
   и на другой счёт; закрытие обязательства — явное действие, не результат сравнения банковских сумм.
7. **Доходы**: зарплата (плавающая дата — ок, календарный месяц; опция «финансовый месяц» позже), подработки, продажи

## Стартовые категории (редактируемые, кастомные с первого дня)

Продукты, Заведения (eating out), Rent, Utilities, Транспорт (bus_tbilisi, Yandex Go/Scooter),
Доставка еды, Подписки (музыка, ChatGPT, Claude, netcup VPS), Велосипед (запчасти/ремонт),
Велозаброски, Goods из-за границы (+ ONEX доставка), Здоровье, Техника, Дом + Income: Salary, Side income, Sales

## UI/UX

- Jetpack Compose, Material 3, светлая/тёмная/системная тема (DataStore)
- Локализация EN + RU с первого дня
- Просто и красиво — дизайн приоритет

## Безопасность и переносимость данных

- Начиная с текущей Room DB v2 все изменения схемы выполняются явными миграциями. Destructive
  migration запрещена для пользовательского устройства.
- Основной переносимый backup — версионированный JSON через SAF, спроектированный для round-trip
  restore. Raw SMS, OTP, app-lock secrets, банковские пароли/токены и Keystore keys не экспортируются.
- Незашифрованный JSON считается чувствительным файлом и требует предупреждения. Позже допускается
  зашифрованный контейнер `.whfin-backup` поверх того же логического формата.
- Системный Android backup — дополнительный канал восстановления: явный allowlist только для Room
  database и non-secret UI/widget preferences; cloud backup разрешён лишь при encryption capability,
  device-to-device transfer использует тот же scope. Банковские tokens/secrets должны храниться отдельно.
- App Lock использует системный `BiometricPrompt`: strong biometric или PIN/pattern/password устройства,
  с настраиваемым timeout и сокрытием содержимого в recent apps. Виджет не раскрывает баланс на
  заблокированном устройстве без явного opt-in.
- Автосинхронизация Credo строится только на официальном read-only Open Banking API после проверки
  sandbox и условий production onboarding. WHFIN не хранит пароль MyCredo и не скрейпит банк.
  OTP не сохраняется и не отправляется без явного действия пользователя.
- Подробный порядок реализации и критерии готовности: [docs/roadmap.md](docs/roadmap.md).

## Вторая волна

- Крипта: watch-адреса EVM/Tron (TrustWallet), цены CoinGecko; НЕ лезем в DeFi
- Теги «траты на девушку» с долей 0–100% на транзакции
- AI-анализ (Claude API, только по кнопке): авто-категоризация непонятного, месячный разбор, аномалии
- Парсинг push-уведомлений (NotificationListener) как второй источник
- Банк TBC
- Семейный бюджет — может быть

## Стек

Kotlin (built-in в AGP 9), Jetpack Compose (BOM 2026.06), Room 2.8 + KSP, DataStore,
WorkManager, Glance 1.1, Gradle 9.4.1 + AGP 9.2.1, compileSdk 36, minSdk 29.
Бэкап = versioned JSON через SAF; позже опциональный зашифрованный `.whfin-backup`.
