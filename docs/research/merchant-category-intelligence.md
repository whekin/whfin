# Merchant category intelligence for WHFIN

Research date: 2026-08-14. Sources are first-party product documentation, API references, pricing pages, and terms.

## Executive conclusion

There is no publicly documented, consumer-priced service that simultaneously:

- accepts a noisy bank merchant descriptor with no coordinates or MCC;
- has demonstrably strong Georgia/Tbilisi coverage;
- returns a reliable personal-finance category;
- permits WHFIN to retain the normalized merchant result indefinitely.

The products fall into three different classes:

1. **Card-network transaction enrichment** (Mastercard Merchant Identifier, Visa Merchant Search) is the closest semantic match, but production access and pricing are contractual and Georgia availability is not publicly established precisely enough.
2. **POI databases** (FSQ OS Places, Google Places, TomTom, HERE, OSM) can identify physical venues and return place types, but they are not merchant-descriptor resolvers. They become unreliable for processors, online merchants, truncated names, and descriptors without location. FSQ OS Places is notable because its open dataset can be filtered to Georgia and indexed offline.
3. **LLMs with web search** can resolve obscure local and global merchants and understand web evidence, but are probabilistic. They should generate evidence-backed suggestions, never silently become the source of truth.

For WHFIN, the recommended product is therefore a **precision-first cascade**:

`user rule/history → bundled rules → descriptor normalization → deterministic web/POI evidence → LLM synthesis → suggest or abstain`.

Automatic application should remain limited to learned user rules and an allowlisted set of high-confidence identities. Search/LLM results should initially be suggestions. The system should optimize precision, not coverage.

For a personal WHFIN installation, the most practical external evidence layer is a compact, offline **Georgia subset of FSQ OS Places**, optionally supplemented by a separately licensed OSM extract, plus LLM/web search only for misses and global/online merchants. This avoids sending the routine local ledger to a server and avoids per-transaction API cost.

## What WHFIN actually needs

The input available today is normally a merchant descriptor, currency, and transaction provenance. It often lacks MCC, city, coordinates, merchant ID, and card-present/card-not-present data. This matters because several specialist products require exactly those missing signals.

The desired output is not merely “what business is this?” It is:

- canonical merchant identity and aliases;
- a mapping into **the user's WHFIN categories**, not a provider taxonomy;
- evidence and provenance;
- calibrated confidence;
- an explicit `UNKNOWN` / `AMBIGUOUS` result;
- a durable rule only after sufficient evidence or user confirmation.

The distinction is essential. `GOOGLE *Play` can represent an app, subscription, game, film, or an intermediary payment, while `GOOGLE *Smart Launcher` supplies useful product identity. A broad Google rule would recreate the ZenMoney failure mode.

## Provider comparison

| Option | Georgia / global coverage | Dirty descriptor without coordinates | Category signal | Retention / operational fit | WHFIN verdict |
|---|---|---|---|---|---|
| FSQ OS Places open dataset | 120M+ POIs across 200+ countries/territories; can filter `country=GE` | Offline fuzzy matching is possible, but it still contains POI names rather than bank descriptors | 1K+ category taxonomy; name, website, locality and category IDs/labels | Apache 2.0; downloadable through Places Portal/Iceberg; no runtime key after preparing a compact index | Best free Georgia seed and privacy-preserving first retrieval layer |
| Google Places Text Search | Google publishes broad Maps coverage; no POI completeness guarantee for Georgia | Accepts arbitrary text and optional region/location bias; plausible but not descriptor-specific | Place `primaryType` and `types`, not MCC | Field-based billing; Google documents Place IDs as exempt from caching restrictions, which implies other content remains subject to Maps terms | Strong candidate generator for physical merchants; not sufficient alone |
| Gemini + Google Maps grounding | Google states global availability and over 250M places | Textual Maps search; coordinates optional, but local results are better with them | Model interpretation over Maps evidence | Must display Maps sources; currently English-only; prompts/context/output retained 30 days and this cannot be disabled | Powerful experiment, but privacy/retention and English-only output are poor defaults for WHFIN |
| TomTom Search | Explicitly lists Georgia with detailed address, house-number, street, city and EV coverage | Fuzzy Search can search the whole world without a geo anchor and supports `countrySet=GE` | POI categories and brands, not MCC | API key and hosted API; legacy Search has 2,500 free requests/month at research time | Best POI benchmark with explicit Georgia coverage; still not transaction enrichment |
| HERE Discover | HERE describes a global set of 120M+ places and 400M addresses | Free-form place search, but `/discover` requires `at` or a geographic `in` constraint; country filter can scope to Georgia | HERE place categories, not MCC | Limited plan: 1,000 requests/day, 5 RPS for Discover; terms generally restrict result storage to cache headers / up to 30 days and forbid building a POI repository | Usable candidate source, weaker fit when the transaction has no location |
| OSM + Nominatim/Overpass | Global open dataset, including Georgia, but community coverage has no completeness SLA | Nominatim supports free-form search and `countrycodes=ge`; exact merchant descriptors and processors are weak | OSM `amenity`, `shop`, `brand`, etc.; not MCC | ODbL attribution/share-alike. Public Nominatim is capped at 1 req/s, discourages periodic/bulk use and requires caching; production requires a commercial provider or self-hosting | Excellent open supplement and local override source; not a dependable primary matcher |
| Foursquare hosted Places API | Advertises global POI data | General search is location-oriented. Place Match currently says only English-speaking countries; batch Place Match's valid-country list omits Georgia | FSQ place categories | New Places API pricing is usage-based; 500 free calls then $15/1K up to 100K from June 2026 | Prefer its open offline dataset for Georgia; hosted matching endpoints are not a good fit today |
| Foursquare Transaction Match | Transaction-specific, but published input is strongly US-shaped | Requires DBA name, MCC, card presence, city, and state | MCC plus matched POI, with High/Medium/Low/No Match outputs | Offline CSV/Parquet job rather than a lightweight on-device flow | Interesting validation design, but WHFIN lacks required inputs and Georgia support is not documented |
| Mastercard Merchant Identifier / Places | Mastercard describes a global merchant-location database | Merchant Identifier is explicitly designed to resolve unrecognizable card-statement merchant names | Returns merchant category; release notes show Card Acceptor ID / merchant identifier lookups and ecommerce/brick-and-mortar attributes | Production access/pricing are not publicly self-serve; commercial relationship likely required | Best semantic fit to investigate with sales, not suitable as the first implementation dependency |
| Visa Merchant Search | Visa calls it a global merchant repository and supports all payment networks | Transaction Enrichment accepts transaction data; Generic Search accepts public name/location | Enhanced merchant information and Visa identifiers; exact returned taxonomy depends on contracted API | Sandbox has dummy data; production requires commercialization agreement and use-case pricing. Public availability table does not establish Georgia clearly | Also an enterprise discovery call, not an MVP dependency |
| Plaid Enrich | **US/Canada only** | Built specifically for raw transaction descriptions; can use amount, currency, location, MCC and date | Merchant, category and location enrichment | Server-side secret; price available only after production request/contact | Technically excellent but irrelevant to Georgia today |
| OpenAI Responses API + web search | Web-wide; approximate country/city can bias results | Good at turning a cleaned descriptor into multiple search queries and reading local/company sites | Must map evidence to WHFIN's closed category enum; no MCC guarantee | Web search is $10/1K calls plus model tokens at research time. API data is not used for training by default; default abuse-monitoring logs can be retained up to 30 days. Web Search is eligible for approved ZDR | Best general fallback for both Georgian and global online merchants, if server-side and evidence-gated |
| Gemini + Google Search | Web-wide and multilingual | Similar agentic web lookup with source annotations | Model maps evidence to WHFIN taxonomy | Search grounding has citations and region-specific web retrieval, but Google documents 30-day prompt/context/output retention with no disable option | Viable alternative; less attractive for a privacy-first default |

## Source notes

### Google Places and Google Maps grounding

[Places Text Search](https://developers.google.com/maps/documentation/places/web-service/text-search) accepts a `textQuery`, optional location bias, `regionCode`, and a field mask. `primaryType` and `types` are Pro fields. Google also states that identical requests are not guaranteed to return consistent results. This makes the API a source of candidates, not a stable classifier.

At current list pricing, Text Search Pro includes 5,000 free monthly events, then costs $32 per 1,000 in the first paid tier ([Google Maps Platform pricing](https://developers.google.com/maps/billing-and-pricing/pricing)). Requesting only IDs is cheaper/free at the documented tier, but the name and type fields needed for categorization trigger Pro.

Google explicitly says [Place IDs may be stored](https://developers.google.com/maps/documentation/places/web-service/place-id) and are exempt from caching restrictions; IDs older than 12 months should be refreshed. WHFIN should not assume it may permanently materialize names, types, or a derivative merchant database from Places results without a terms review.

[Gemini Grounding with Google Maps](https://ai.google.dev/gemini-api/docs/maps-grounding) is more convenient because the model itself searches Maps and returns cited place evidence. Google states that it is globally available and backed by more than 250 million places, but also documents that it currently supports English prompts/responses, that sources must be displayed, and that results improve when coordinates are supplied. For privacy, the decisive caveat is Google's [ZDR documentation](https://ai.google.dev/gemini-api/docs/zdr): Maps-grounded prompts, context, and outputs are retained for 30 days and this storage cannot be disabled.

### TomTom

[TomTom Fuzzy Search](https://developer.tomtom.com/search-api/documentation/search-service/fuzzy-search) accepts a single-line fuzzy query, can search worldwide without a location anchor, and supports country, POI-category, and brand filters. Its [market coverage table](https://developer.tomtom.com/search-api/documentation/product-information/market-coverage) explicitly lists Georgia (`GE/GEO`) with detailed coverage for address points, house numbers, streets, cities, and static EV data. That is the clearest first-party Georgia statement among the POI vendors reviewed.

TomTom's [current pricing page](https://docs.tomtom.com/pricing) lists 2,500 free monthly requests for the legacy Search API; newer Orbis Search Discover and Details each list 5,000. This is inexpensive enough for an evaluation against WHFIN's private labeled merchants. However, TomTom is still matching place names, not card descriptors, and returned POI categories should only become evidence.

### HERE

[HERE Discover](https://docs.here.com/geocoding-and-search/reference/post_discover) accepts a free-form query and orders results by relevance, but requires a search center or geographic constraint. A country constraint can avoid sending precise coordinates. HERE advertises a [global dataset of over 120 million places and 400 million addresses](https://www.here.com/geocoding-and-search-campapr25-v2), but does not provide a merchant-descriptor or MCC resolver.

The free/limited plan currently documents a 1,000-request daily limit and 5 RPS for Discover/Search ([HERE plan limits](https://www.here.com/get-started/pricing/rps-limits-excluded-use-cases)). The [HERE platform terms](https://legal.here.com/us-en/terms/here-platform/terms-november-2021) prohibit building a derivative POI database and generally restrict storage to returned cache headers / 30 days. That conflicts with using HERE results as a permanent shared merchant knowledge base; a user-confirmed WHFIN rule should be stored as the user's decision, not as copied HERE content.

### OpenStreetMap

[Nominatim search](https://nominatim.org/release-docs/latest/api/Search/) supports free-form queries, a hard `countrycodes=ge` filter, and extra OSM tags/names. The classification reflects heterogeneous OSM tagging and is explicitly described as difficult to use consistently. Overpass can retrieve POIs by tags, but it does not solve noisy descriptor identity.

OSM itself is open under ODbL with attribution and share-alike obligations ([OSM copyright and license](https://www.openstreetmap.org/copyright)). This makes a self-hosted Georgia-focused index possible. The public [Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/) is not production infrastructure: maximum 1 request/second, periodic/bulk work is discouraged, repeat results must be cached, and apps must be able to switch providers. Public Nominatim should only be used for deliberate low-volume prototyping; a production implementation needs self-hosting or a contracted provider.

### Foursquare

Foursquare has two materially different offerings. **FSQ OS Places is the strongest ready-made open database found for a Georgia-first prototype.** Foursquare says it contains [120M+ POIs across 200+ countries and territories with a 1K+ category taxonomy](https://docs.foursquare.com/data-products). Its [Apache-2.0 schema](https://docs.foursquare.com/data-products/docs/places-os-data-schema) includes name, country, coordinates, locality, website, category IDs/labels, refresh/closure dates, and unresolved quality flags. The dataset can be queried/downloaded through the [Places Portal and Iceberg catalog](https://docs.foursquare.com/data-products/docs/access-fsq-os-places).

A build-time job can filter `country = 'GE'`, remove closed/non-commercial/unresolved records, normalize names and websites, and generate a compact SQLite/FTS asset. The app can then query local evidence without a network request or API secret. The remaining limitation is semantic: a POI name such as `Silknet` or `Agrohub` is matchable, but processor/truncation strings and global online products still need normalization or web evidence. Coverage quality in Georgia must be measured against the private test set; the global count is not a Georgia completeness guarantee.

The hosted [Foursquare Places API](https://docs.foursquare.com/fsq-developers-places/reference/places-api-overview) offers search, categories, place matching, and transaction matching. But the published specialist endpoints reveal the mismatch with WHFIN:

- [Transaction Match](https://docs.foursquare.com/fsq-developers-places/reference/batch-transaction-match) requires merchant DBA, four-digit MCC, card presence, city, and state; it returns explicit High/Medium/Low/Invalid/No-Match confidence.
- [Place Match](https://docs.foursquare.com/fsq-developers-places/reference/place-match) requires name, street address, city, and country, and currently says only English-speaking countries are supported.
- The valid-country list for [batch Place Match](https://docs.foursquare.com/fsq-developers-places/reference/batch-place-match-1) does not include Georgia.

The confidence/abstention design is worth copying, but the service is not a current fit. Published pricing changes from June 2026 provide 500 free Pro calls, then $15/1,000 up to 100,000 ([Foursquare pricing change](https://docs.foursquare.com/developer/reference/upcoming-changes)).

### Payment-network enrichment

Mastercard describes [Merchant Identifier](https://developer.mastercard.com/products) as addressing unrecognizable merchant names in card statements, and Mastercard Places as access to its global merchant-location database. Public [release notes](https://static.developer.mastercard.com/content/merchant-identifier/uploads/MerchantIdentifierAPI_ReleaseNotes_v3.0.3.pdf) confirm endpoints keyed by Merchant Identifier and Card Acceptor ID and fields such as ecommerce/brick-and-mortar; other official release notes show a returned merchant category. This is the closest data product to the problem, but public pages do not establish self-serve production pricing or Georgia descriptor hit rate.

Visa's [Merchant Search](https://developer.visa.com/capabilities/merchant_search/docs) has a Transaction Enrichment bundle, a Generic Search bundle, and Nearby Merchants. It accepts all payment networks, but its sandbox contains dummy data and real-data testing requires sales or existing production access. Visa's [pricing framework](https://developer.visa.com/pages/working-with-visa-apis/visa-developer-pricing) says production requires a contract and pricing depends on the use case. The public availability content does not provide a clear enough Georgia commitment to depend on it without a vendor conversation and sample evaluation.

These products should be revisited if WHFIN becomes a multi-user commercial service or Credo begins supplying MCC/Card Acceptor ID. They are disproportionate for a single-user dogfood app today.

### Plaid

[Plaid Enrich](https://plaid.com/docs/enrich/) is purpose-built: it accepts description, amount, direction, currency, account type, and optionally location/MCC/date, and returns merchant, category, and location. Unfortunately, Plaid explicitly limits Enrich to the US and Canada and only discloses production pricing after an access request. It is not relevant to Georgia now.

### LLM with web search

OpenAI's [web search tool](https://developers.openai.com/api/docs/guides/tools-web-search) lets a Responses API model decide whether and how to search, returns URL citations and a complete consulted-source list, supports an approximate country/city, and allows domain allow/block lists. The present [API pricing](https://openai.com/api/pricing/) lists web search at $10 per 1,000 calls, with model tokens billed separately. [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs) can force the response into a schema with a fixed WHFIN category enum and an explicit abstention status.

Privacy is manageable but not zero by default. OpenAI's [data controls](https://platform.openai.com/docs/models/default-usage-policies-by-endpoint) state that API data is not used for training unless the customer opts in, while default abuse-monitoring logs may retain customer content for up to 30 days. Web Search is eligible for Zero Data Retention for approved customers. A personal installation should send only the normalized merchant descriptor, broad country/city hint, and allowed category names—not amount, exact time, account, card, note, or transaction history.

Gemini also offers [Grounding with Google Search](https://ai.google.dev/gemini-api/docs/google-search), automatically creating queries and returning cited sources across supported languages. It is a credible alternative, especially for Georgian web discovery, but the same [30-day non-disableable retention](https://ai.google.dev/gemini-api/docs/zdr) applies to Search-grounded prompts and output.

## Recommended WHFIN design

### 1. Keep the deterministic core local

Resolve in this order and stop at the first trustworthy result:

1. Explicit user override for the transaction.
2. User-confirmed canonical merchant rule.
3. Existing merchant history, only when it is consistent.
4. Bundled high-precision Georgia/global rules.
5. External intelligence.

Manual choices must never be overwritten. A merchant with multiple confirmed categories is ambiguous by definition and should not receive a new automatic rule.

### 2. Search once per canonical descriptor, not once per transaction

Normalize processor wrappers and punctuation locally, but preserve both the raw and candidate canonical strings. Group all uncategorized operations by descriptor. Query external services only for a previously unseen canonical descriptor, then reuse the resulting local **user decision**.

Suggested external request:

```json
{
  "descriptor": "FLT MYSILKNETAPP",
  "country_hint": "GE",
  "city_hint": "Tbilisi",
  "allowed_categories": ["Utilities", "Subscriptions", "Transport", "Other"],
  "request_kind": "merchant_category_suggestion"
}
```

Do not include transaction amount, full date/time, account/card identifiers, balance, note, or other merchants.

### 3. Use retrieval and LLM as separate stages

The LLM should not simply answer from memory. A backend should obtain evidence, then ask the model to map it to WHFIN's taxonomy:

- web search for official merchant/company/product pages;
- optionally one POI search with `country=GE` / Tbilisi bias for physical-looking descriptors;
- known user history and bundled rules;
- closed output schema.

Example output contract:

```json
{
  "identity": "Silknet",
  "category": "Utilities",
  "decision": "SUGGEST",
  "confidence": 0.93,
  "evidence": [
    {"url": "https://...", "claim": "Silknet provides telecom and internet services"}
  ],
  "reason_codes": ["OFFICIAL_SITE", "DESCRIPTOR_PRODUCT_MATCH"],
  "alternatives": [],
  "rule_scope": "EXACT_DESCRIPTOR"
}
```

`confidence` is not trustworthy merely because the model emitted a high number. The backend must compute the final decision from observable evidence and validation rules.

### 4. Evidence gates and abstention

Suggested policy:

- **AUTO** only after a user-confirmed rule, or an allowlisted bundled identity with an exact/strong descriptor match. External search alone should not auto-apply in the first release.
- **SUGGEST** when an official source or high-quality POI record identifies the merchant uniquely and its business type maps to exactly one WHFIN category.
- **AMBIGUOUS** when multiple entities or categories remain plausible.
- **UNKNOWN** when no credible source resolves the descriptor.

Additional safeguards:

- payment processors (`PAYPAL`, `GOOGLE PLAY`, `APPLE.COM/BILL`, `VIP PAY`) never determine category by themselves;
- do not use amount as evidence of business type;
- personal names and bank transfers default to `UNKNOWN`;
- a generic POI result without a matching brand/domain is insufficient;
- map provider and web-source disagreement forces review;
- exact product suffix may narrow a processor descriptor, but the rule remains exact/product-scoped;
- all suggestions show “why” and which past/future transactions will be affected.

### 5. Architecture and secrets

Do not ship OpenAI, Google, HERE, TomTom, Foursquare, Visa, or Mastercard service secrets in the Android APK. Use a small backend/proxy with:

- authenticated app requests;
- strict request schema and redaction;
- per-user and global budgets;
- provider keys and retries;
- a short-lived cache keyed by a salted hash of normalized descriptor + country;
- provider attribution/source metadata;
- deletion and diagnostics controls.

For a single-user prototype, a user-supplied API key stored in Android Keystore could avoid operating a backend, but it complicates web-search citations, spend controls, provider terms, and key rotation. A minimal backend is the cleaner production design.

## Evaluation before choosing a provider

No marketing coverage claim establishes performance on Credo descriptors. Build a private, manually labeled test set from the backup:

- 100–200 unique descriptors, weighted toward repeated uncategorized spend;
- separate physical Georgia, global online, processor-wrapped, personal transfer, and deliberately ambiguous strata;
- label canonical identity, WHFIN category, and “must abstain”.

Measure per provider/cascade:

- **precision among automatic assignments** (primary metric; target at least 99% before enabling AUTO);
- suggestion precision;
- coverage at each confidence threshold;
- false-food and false-groceries rates as explicit regression metrics;
- abstention recall on ambiguous descriptors;
- result stability across repeated queries;
- cost and latency per new merchant, not per transaction;
- Georgia/Tbilisi physical-merchant hit rate separately from online merchants.

The first practical bake-off should compare:

1. existing WHFIN rules/history baseline;
2. offline FSQ OS Places Georgia index;
3. OpenAI web search only;
4. TomTom `countrySet=GE` only;
5. OSM on an offline Georgia extract (public Nominatim only for a deliberately tiny prototype);
6. OpenAI synthesis over local FSQ evidence + web evidence.

Google Places can be added if its licensing constraints and $32/1K Pro price are acceptable. A Mastercard/Visa sample should only be pursued after a sales contact confirms Georgia coverage, input fields available from Credo data, production price, and rights to retain canonical merchant/category mappings.

## Recommended first product slice

1. Add a grouped “category suggestions” queue and provenance model.
2. Build a compact Georgia merchant index from Apache-2.0 FSQ OS Places and match normalized descriptors locally. Treat matches as evidence, not automatic category assignments.
3. Implement an external-intelligence interface backed by OpenAI Responses API web search through a minimal server for offline-index misses and online/global merchants.
4. Query only when the user opens the queue or asks to identify a merchant; never background-send the full ledger.
5. Return exact-descriptor suggestions with citations and `UNKNOWN` support.
6. After confirmation, store a normal local user rule and never need external lookup for that descriptor again.
7. Run the private benchmark before permitting any search/LLM result to auto-apply.

This gives WHFIN useful intelligence for `jetshr`, `mySilknetAPP`, `Bike24 GmbH`, and exact Google product descriptors without delegating control of the ledger to a probabilistic classifier.
