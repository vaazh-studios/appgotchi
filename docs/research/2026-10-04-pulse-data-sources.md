# Pulse data sources: App Store Connect and Google Play

Date: 2026-10-04. Method: official docs only (plus a few clearly labelled community sources). Nothing was run against a real account, so anything marked UNVERIFIED must be confirmed with a live key before we promise it in UI copy.

Evidence labels used below:
- DOC = stated on an official Apple/Google page (URL cited).
- COMMUNITY = third-party or forum; plausible but not official.
- INFERRED = my deduction from DOC statements.

## 0. Executive summary

1. **App Manager is NOT enough for Apple sales data.** Apple's own API docs list only Admin, Sales and Reports (`SALES_AND_REPORTS`) and Finance as roles that may read `/v1/salesReports` or analytics reports. App Manager is not in that list (DOC). Our current "App Manager" suggestion works for apps and customer reviews only. For downloads and proceeds the user needs a second team key with the "Sales and Reports" role, plus the vendor number.
2. **Individual API keys cannot call sales/finance endpoints at all** (DOC). It must be a Team key, and only an Admin can create Team keys.
3. **Apple vendor number cannot be discovered via API** (COMMUNITY forum answer; DOC says only "find it in App Store Connect"). The user copies it from Reports > top-left (Account Holder, Admin or Finance can see that page).
4. **Play crash rate works today with no new permission or scope.** `playdeveloperreporting` scope plus account-level "View app information and download bulk reports (read-only)" is what the crash rate resource asks for (DOC: "View app information (read-only)").
5. **Play downloads, ratings and estimated sales are only in the Cloud Storage bulk-report bucket** (monthly CSV / zip, UTF-16 CSVs). There is no Reporting API metric set for installs or ratings. The bucket name (`pubsite_prod_rev_...`) cannot be found through an API; the user copies the URI from Play Console (DOC + COMMUNITY). It needs one extra OAuth scope (`devstorage.read_only`).
6. **Play `reviews.list` needs "Reply to reviews"** per Google's docs (a write-capable permission) and only returns the last 7 days of reviews that have text. The same review text is also in the GCS `reviews/` CSV, which only needs the bulk-reports permission we already ask for.
7. **Crash data on Apple is the heaviest item.** Needs an Admin key to create an Analytics Report Request once per app, is async (first data in 1-2 days), report instances live 35 days, and App Crashes gives counts, not a rate. Defer.

## 1. Per-metric matrix

### Apple (App Store Connect API, JWT ES256, base `https://api.appstoreconnect.apple.com`)

| Metric | Endpoint / source | Role needed (API key) | Extra setup | Freshness / limits |
|---|---|---|---|---|
| Downloads (7d vs prior 7d) | `GET /v1/salesReports` with `filter[reportType]=SALES`, `filter[reportSubType]=SUMMARY`, `filter[frequency]=DAILY`, `filter[version]=1_0`, `filter[vendorNumber]`, `filter[reportDate]=YYYY-MM-DD`. Sum `Units` where Product Type Identifier is 1, 1F, 1T or F1 (first-time downloads). Exclude 3/3F (re-downloads) and 7/7F/7T/F7 (updates) | Team key with Sales and Reports, Finance or Admin. Not App Manager. Not an Individual key | Vendor number (manual copy) | One request per day (14 requests for a 7 vs 7 comparison). Next-day, usually there by 8 a.m. PT. Day = 00:00-23:59 PT. Daily files kept 1 year. Response is `application/a-gzip`, tab-separated text |
| Proceeds / revenue | Same SALES SUMMARY DAILY file: `Units x Developer Proceeds (per unit)`, in the row's `Currency of Proceeds` | Same as above. Finance reports (`/v1/financeReports`) additionally need Finance or Admin (UI says Account Holder, Admin, Finance) | Same vendor number | Currency varies per row (no single currency; multiple rows per territory). Free apps and updates give 0. Refunds appear as negative units. Subscription/IAP rows use product types IA1, IA9, IAY etc. Settled money is only in the monthly finance report (fiscal month, available by the first Friday of the next fiscal month) |
| Average rating | Public iTunes Lookup: `GET https://itunes.apple.com/lookup?id=<appId>&country=<cc>` returns `averageUserRating`, `userRatingCount` (also `...ForCurrentVersion`) | None (no auth) | Pick which storefronts to query | Per country only; no worldwide figure. Fields are not described on Apple's doc page, but they are returned today (I confirmed in a live call). Search API doc says about 20 calls/minute |
| New reviews | `GET /v1/apps/{id}/customerReviews?sort=-createdDate&limit=N` (`filter[rating]`, `filter[territory]` available) | Apple Help lists Account Holder, Admin, App Manager, Customer Support, Developer, Marketing as roles that can view ratings and reviews. The API page itself names no role (INFERRED: key roles mirror user roles). Third-party aso.dev claims Sales and Reports keys cannot read reviews (COMMUNITY) | App Manager key we already ask for | Written reviews only (INFERRED: star-only ratings are not objects here). No numeric rating-summary endpoint exists. `customerReviewSummarizations` is a text summary, not a number |
| Crash rate | Analytics Reports API: `POST /v1/analyticsReportRequests` (accessType ONGOING), then `GET .../reports`, `.../instances`, `.../segments`, download segment URLs. Report "App Crashes" (Crashes, Unique Devices by app version/device/OS) | POST needs an **Admin** key; Sales and Reports or Finance can then list and download | Admin key once per app. Request has to be made first; keep polling or it goes `stoppedDueToInactivity` | First data 1-2 days after request. Daily instance, complete "within five days". Instances deleted after 35 days; one ONE_TIME_SNAPSHOT per month for history (from 2024-01-01). Only rows with at least 5 users. Gives a crash **count**, not a rate. To make a rate you would also need the "App Sessions" report |
| Crash alternative | `GET /v1/apps/{id}/perfPowerMetrics` (Xcode metrics). Category `TERMINATION` | Not documented per role | None | Termination counts unexpected exits including background kills, so it is not a crash rate. Not recommended as a Pulse crash number |

### Google Play

| Metric | Endpoint / source | Scope | Play Console permission | Extra setup | Freshness / limits |
|---|---|---|---|---|---|
| Crash rate | `POST https://playdeveloperreporting.googleapis.com/v1beta1/apps/{pkg}/crashRateMetricSet:query` (metrics `crashRate`, `userPerceivedCrashRate`, `crashRate7dUserWeighted`, `distinctUsers`). `GET .../apps/{pkg}/crashRateMetricSet` returns `freshnessInfo` | `https://www.googleapis.com/auth/playdeveloperreporting` (already have) | "View app information (read-only)" per the resource doc, which the account-level "View app information and download bulk reports (read-only)" covers. App-level "View app quality information" is a narrower alternative (DOC, permissions page) | Enable "Google Play Developer Reporting API" in the Cloud project | DAILY aggregation is only allowed in `America/Los_Angeles`; HOURLY only in UTC. Freshness is exposed per aggregation period via `freshnessInfo.latestEndTime`; Google does not publish a latency number. Default quota 10 queries/second. `distinctUsers` is rounded. Page size max 100,000 rows. Low-volume apps may return no rows (UNVERIFIED) |
| Downloads | GCS object `gs://pubsite_prod_rev_<id>/stats/installs/installs_<pkg>_YYYYMM_<dimension>.csv`. Columns include `Date`, `Daily Device Installs`, `Daily User Installs`, `Daily Device Uninstalls`, `Total User Installs`. The no-breakdown file is `..._overview.csv` (COMMUNITY: not in the official table) | `https://www.googleapis.com/auth/devstorage.read_only` (new) | "View app information and download bulk reports (read-only)" at account level, i.e. "Global" (already requested) | Copy the Cloud Storage URI from Play Console > Download reports > "Copy Cloud Storage URI". No API found to discover it | Data captured daily, "posted within 3 to 7 days in monthly CSV files". One CSV per month, so a 7 vs 7 window that crosses a month boundary needs two files. CSVs are UTF-16 (LE with BOM per COMMUNITY). Time zone for stats not stated on the page (UNVERIFIED; do not assume UTC). Community reports of 403s even when permissions look right, and of up to 24 h propagation after granting access |
| Proceeds / revenue | Estimated sales: `gs://<bucket>/sales/salesreport_YYYYMM.zip` ("low latency", gross amount paid by buyers, in buyer's local currency, no tax or Google fee deducted). Net: `gs://<bucket>/earnings/earnings_YYYYMM.zip` | `devstorage.read_only` | "View financial data, orders, and cancellation survey responses" at account level (Global). New permission. Financial reports cover **all apps** in the account | Same bucket URI | Estimated sales are not for accounting (Google says so). Earnings are monthly, usually by the 5th of the next month. Sales file dates are UTC; earnings dates are Pacific Time. Mixed currencies in estimated sales; earnings include converted amounts |
| Average rating | GCS `stats/ratings/ratings_<pkg>_YYYYMM_<dimension>.csv`: `Daily Average Rating`, `Total Average Rating` (the latter is the running overall value) | `devstorage.read_only` | Bulk reports (already granted) | Same bucket URI | Same monthly-file and 3-7 day latency caveats. Daily average can be empty on days with no new ratings. Use latest `Total Average Rating` |
| New reviews | Option A: `GET https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{pkg}/reviews` (`reviews.list`). Option B: GCS `reviews/reviews_<pkg>_YYYYMM.csv` | A: `https://www.googleapis.com/auth/androidpublisher` (new). B: `devstorage.read_only` | A: Google's reviews guide says to enable "Reply to reviews" for a service account. That permission also lets the account post replies. B: bulk reports only | A: enable Android Publisher API. B: bucket URI | A: only reviews created or modified in the **last week**, and only ones with a **text comment**; star-only ratings are invisible. Docs cite 200 GET/hour for reviews. `startIndex`/`token` pagination, `maxResults` up to 100. B: monthly file, same latency as other bulk reports |

## 2. Source notes by question

### Apple

1. Endpoint and scope. App Store Connect has no OAuth scopes. Every call carries an ES256 JWT signed by the API key. Authorization is determined by the key's role.
   - `salesReports` filters (all required): `frequency`, `reportDate` (YYYY-MM-DD, omitted for DAILY per the doc text), `reportSubType`, `reportType`, `vendorNumber`, `version`. Valid combination for sales: SALES / SUMMARY / DAILY, WEEKLY, MONTHLY, YEARLY / 1_0. https://developer.apple.com/documentation/appstoreconnectapi/get-v1-salesreports
   - `financeReports` filters: `regionCode`, `reportDate` (YYYY-MM fiscal month), `reportType=FINANCIAL`, `vendorNumber`. https://developer.apple.com/documentation/appstoreconnectapi/get-v1-financereports
2. Roles.
   - "These endpoints require a Team key and aren't usable with an Individual key." https://developer.apple.com/documentation/appstoreconnectapi/sales-and-finance
   - Individual keys "aren't able to use Provisioning endpoints, access sales-and-finance, or notaryTool"; Team keys must be generated by an Admin. https://developer.apple.com/documentation/appstoreconnectapi/creating-api-keys-for-app-store-connect-api
   - Analytics roles table: Admin can POST/DELETE report requests; Finance and "Sales and Reports" can list and download; "The Sales and Reports role can also read GET-v1-salesReports", and "can't access GET-v1-financeReports". https://developer.apple.com/documentation/appstoreconnectapi/downloading-analytics-reports and https://developer.apple.com/documentation/appstoreconnectapi/analytics
   - App Manager appears in none of those tables. Third-party summaries say the same (App Manager cannot reach sales data): https://aso.dev/app-store-connect/api-key-access-levels/ (COMMUNITY).
   - Finance reports in the UI: "Account Holder, Admin, or Finance are the only roles permitted to download financial reports." https://developer.apple.com/help/app-store-connect/getting-paid/download-financial-reports/
   - Reviews in the UI: "Required role: Account Holder, Admin, App Manager, Customer Support, Developer, Marketing." https://developer.apple.com/help/app-store-connect/monitor-ratings-and-reviews/view-ratings-and-reviews/
   - Role descriptions: Finance sees Payments and Financial Reports, Sales and Trends and App Analytics; Sales "analyzes sales, downloads, and other analytics". https://developer.apple.com/help/app-store-connect/reference/account-management/role-permissions
   - For human users, a user with App Manager can be given an "Access to Reports" additional permission (which also removes per-app restriction). That is a user concept; the API docs only name `SALES_AND_REPORTS`, so for a key use that role. https://developer.apple.com/help/app-store-connect/manage-your-team/edit-access-to-apps/
   - The role-by-feature matrix at https://developer.apple.com/support/roles/ renders its check marks as images, so I could not read per-cell values. The conclusions above rely on the API pages instead.
3. Extra setup.
   - Vendor number: Reports (top of App Store Connect) > Payments and Financial Reports; "Your Vendor Number appears in the top left hand corner under your Legal Entity Name." Required role for that page: Account Holder, Admin, or Finance. https://developer.apple.com/help/app-store-connect/getting-paid/view-payments-and-proceeds/
   - No API lookup: forum thread says there is none. https://developer.apple.com/forums/thread/118428 (COMMUNITY)
   - A Paid Applications Agreement must be in place for sales data. A 403 `FORBIDDEN.REQUIRED_AGREEMENTS_MISSING_OR_EXPIRED` was reported on `salesReports` when an agreement had lapsed. https://developer.apple.com/forums/thread/700371 (COMMUNITY). Surface that error text in the connect wizard.
   - Analytics reports need an Admin key once to create the request (see matrix).
4. Freshness and limits.
   - Availability: daily reports next day, generally by 8 a.m. PT; weekly on Mondays; monthly 5 days after month end; yearly 6 days after year end. Daily, weekly, monthly kept 1 year; yearly 10 years. https://developer.apple.com/help/app-store-connect/reference/reporting/sales-and-trends-reports-availability
   - Time zone: Pacific. "A day includes transactions from 12:00 a.m. to 11:59 p.m. PT." https://developer.apple.com/help/app-store-connect/view-sales-and-trends/download-and-view-reports/
   - Format: gzip (`application/a-gzip` per the response docs) wrapping tab-delimited text. Units can be negative (refunds); 0 can be a partial refund. "Developer Proceeds (per unit)" is customer price minus taxes and Apple commission; "Currency of Proceeds" is the currency you are paid in. https://developer.apple.com/help/app-store-connect/reference/reporting/summary-sales-report/
   - Reports only exist if there was activity (Summary Sales: at least one unit sold). Expect an HTTP error (commonly 404) for quiet days; treat as zero (the exact error body is UNVERIFIED).
   - Rate limit: per API key, rolling hour, shown in the `X-Rate-Limit` header (example in docs: 3,500/hour); 429 `RATE_LIMIT_EXCEEDED` when exceeded. https://developer.apple.com/documentation/appstoreconnectapi/identifying-rate-limits
   - Average rating: Apple has no numeric ratings API. Lookup is the public route. Search API: "approximately 20 calls per minute". https://performance-partners.apple.com/search-api . The response fields `averageUserRating` and `userRatingCount` are not documented on that page, so treat as stable-in-practice but unofficial.
   - Crash data: App Crashes report. Availability daily; complete within five days; history from 2024-01-01 on request; "Data is provided only when events exist from at least five users"; fields Date, App Version, Device, Platform Version, Crashes, Unique Devices. https://developer.apple.com/documentation/analytics-reports/app-crashes . Retention of instances is 35 days. https://developer.apple.com/documentation/analytics-reports . Request timing: "Your first report request generates in 1-2 days." https://developer.apple.com/documentation/appstoreconnectapi/downloading-analytics-reports . Data completeness approach (later processingDate overwrites earlier; do not merge): https://developer.apple.com/documentation/analytics-reports/data-completeness-corrections
   - Nothing simpler exists for Apple crash rate. `perfPowerMetrics` has no crash category (`TERMINATION` is unexpected exits). https://developer.apple.com/documentation/appstoreconnectapi/metriccategory
5. Gotchas.
   - Currency: proceeds come in each territory's payout currency, so a daily file has mixed currencies. Convert only for display and label as approximate; finance reports carry the real exchange rates.
   - "Downloads" definition: Summary Sales counts first-time downloads (product types 1, 1F, 1T, F1) separately from re-downloads and updates. https://developer.apple.com/help/app-store-connect/reference/reporting/product-type-identifiers . Apple's Analytics "Installs" (opt-in users only) will not match.
   - Sales and Trends counts are not threshold-filtered the way Analytics reports are (5-user minimum), so Sales is the better downloads source.
   - Rating from Lookup is per storefront. A worldwide figure has to be computed by weighting `averageUserRating` by `userRatingCount` across storefronts, which means N calls (rate limit applies).

### Google Play

1. Endpoints and scopes: see matrix. Sources: crash rate get/query https://developers.google.com/play/developer/reporting/reference/rest/v1beta1/vitals.crashrate and https://developers.google.com/play/developer/reporting/reference/rest/v1beta1/vitals.crashrate/query ; reviews list https://developers.google.com/android-publisher/api-ref/rest/v3/reviews/list ; GCS scope in the bulk-reports help https://support.google.com/googleplay/android-developer/answer/6135870 .
   - The Reporting API has vitals metric sets only (crash rate, ANR rate, slow start/render, wakeups, wakelocks, errors, anomalies). No installs, ratings or revenue. https://developers.google.com/play/developer/reporting/metricset-intro
2. Permissions.
   - "View app information and download bulk reports (read-only)": account-level only; "Read-only access to all app information for all apps ... but not financial data. Download bulk reports." https://support.google.com/googleplay/android-developer/answer/9844686
   - "View financial data, orders, and cancellation survey responses": account-level only; "View financial reports, sales reports, and orders." Same page.
   - "View app quality information (read-only)": app-level only; Android vitals and pre-launch reports. Same page.
   - "Reply to reviews": both levels. "Users without this permission can still view ratings and reviews but can't reply" (about the Console UI). Same page. The API guide says to enable it for service accounts. https://developers.google.com/android-publisher/reply-to-reviews . Whether `reviews.list` works without it is UNVERIFIED; test with a service account that lacks it before deciding.
   - Bulk reports in GCS: "To access bulk reports, your 'View app information' permission must be set to 'Global.' To download financial reports, your 'View financial data' permission must be set to 'Global.'" https://support.google.com/googleplay/android-developer/answer/6135870
3. Extra setup.
   - GCS bucket: reports sit in "a private Google Cloud Storage bucket for your Google Play Developer account"; the user finds the ID with "Copy Cloud Storage URI" on the Download reports pages; it "begins with pubsite_prod_rev". https://support.google.com/googleplay/android-developer/answer/6135870 . A Google forum answer confirms there is no programmatic discovery. https://discuss.google.dev/gc/Google-Cloud-s-operations-suite/Allocating-Google-Play-Console-Bucket-Programmatically/m-p/620005 (COMMUNITY). I found no listing or lookup API. The bucket is Google-managed, not in the user's project, so `storage.buckets.list` on their project will not show it.
   - A community issue shows a 403 on `storage.objects.get` even after granting the Play permission and `devstorage.read_only`, with no resolution posted. https://github.com/googleapis/nodejs-storage/issues/2688 . Plan for a "test connection" step with a clear error and a manual-CSV fallback.
   - Enable the Reporting API (and Android Publisher API for reviews) in the Cloud project. Reporting API getting started: https://developers.google.com/play/developer/reporting/overview
   - No official page states a propagation delay for new Play permissions; a community source says up to 24 hours. Allow for "connected but data not ready yet".
4. Freshness and limits.
   - Reviews: "you can retrieve only the reviews that users have created or modified within the last week"; "the API shows only the reviews that include comments." Quota per that page: GET 200/hour, POST 2,000/day. https://developers.google.com/android-publisher/reply-to-reviews . The general quota page lists 3,000 queries/minute per bucket and does not itemize reviews; trust the stricter reviews figure. https://developers.google.com/android-publisher/quotas
   - Vitals: `freshnessInfo` per aggregation period; Google notes DAILY can lag HOURLY. https://developers.google.com/play/developer/reporting/reference/rest/v1beta1/FreshnessInfo . No published latency, so always read `latestEndTime` and anchor the 7-day window to it instead of "today". Rate: 10 queries/second default. https://developers.google.com/play/developer/reporting/limits
   - Crash metric definitions: `crashRate` is "percentage of distinct users in the aggregation period that experienced at least one crash"; `userPerceivedCrashRate` counts crashes while the app was in active use. Rolling `crashRate7dUserWeighted` and `crashRate28dUserWeighted` exist (28d not in HOURLY). For a "last 7 days vs prior 7" tile, `crashRate7dUserWeighted` evaluated at two end dates is the simplest. User cohort defaults to `OS_PUBLIC`. https://developers.google.com/play/developer/reporting/reference/rest/v1beta1/vitals.crashrate/query
   - Bulk reports: "Data is captured daily and posted within 3 to 7 days in monthly CSV files." Google recommends "building systems that don't depend on the exports being updated at a specific time." https://support.google.com/googleplay/android-developer/answer/6135870
5. Gotchas.
   - Monthly files: `installs_<pkg>_YYYYMM_<dimension>.csv` holds one month of daily rows; the current month's file keeps growing. Fetch the current and previous month for any trailing window. Filenames use `YYYYMM` and the dimension suffix (`country`, `device`, `os_version`, `app_version`, `language`, `carrier`; `overview` is community-documented).
   - Encoding: "you need to convert the CSV files from UTF-16 to UTF-8" (BigQuery tip on the official page). Decode UTF-16 (BOM present per community) before parsing; do not assume column count or order ("We recommend that you don't rely on the number of columns").
   - Time zones are inconsistent: sales file order dates are UTC, earnings dates are Pacific Time, review timestamps UTC; crash rate DAILY is Pacific. Stats CSV zone is not stated. Display "as reported by store".
   - Currency: estimated sales are gross buyer-paid amounts in buyer currency (no tax, no fees), so they are not proceeds. Net proceeds only exist in the monthly earnings report (payout currency, around the 5th). Label clearly or hide.
   - Financial permission is global and shows all apps' financials, so a developer with several apps or a team account is granting broad read access to the service account.
   - Upcoming change: Google is changing Fee Description values in earnings reports from October 2026. Do not match on exact strings. Same page.
   - Total Average Rating from `ratings` is cumulative; Daily Average Rating is that day's new ratings only. The `Total Average Rating` in the overview file is the closest to the Play Store star number; treat as approximate.

## 3. Recommended minimal v1

Principle: ship what works with today's credentials, add at most one paste-in field per store, and gate anything needing a new role behind an optional "Unlock more" step.

### v1.0 (no new permissions, no new keys)

| Store | Metric | How |
|---|---|---|
| Play | Crash rate (7d vs prior 7d) | Reporting API `crashRateMetricSet:query`, `userPerceivedCrashRate` or `crashRate7dUserWeighted`, DAILY / `America/Los_Angeles`, window ends at `freshnessInfo.latestEndTime`. Needs only the scope and permission we already have, plus the API enabled |
| Apple | Average rating | Public iTunes Lookup per selected storefront (default to the app's primary storefront; optionally the top few), weighted by `userRatingCount`. No credentials |
| Apple | New reviews (count + latest few) | `customerReviews` with the existing App Manager key. Fall back to hiding the card on 403 |

### v1.1 (one new field per store, no new roles)

| Store | Metric | Added step |
|---|---|---|
| Play | Downloads (7d vs prior 7d) and average rating | Add scope `devstorage.read_only`; user pastes the `gs://pubsite_prod_rev_...` URI from "Copy Cloud Storage URI". Existing "View app information and download bulk reports" permission is enough. Read `stats/installs/..._overview.csv` and `stats/ratings/..._overview.csv` for this and last month; show data "as of" the last row date |
| Play | New reviews (last 7 days) | Read the GCS `reviews/reviews_<pkg>_YYYYMM.csv` rather than `reviews.list`, which avoids asking for "Reply to reviews" |

### v1.2 (new Apple key, optional)

| Store | Metric | Added step |
|---|---|---|
| Apple | Downloads and proceeds | User creates a **second Team key with role "Sales and Reports"** (Admin required to create it) and pastes the vendor number. 14 `salesReports` DAILY calls per refresh; cache every day's file forever (they are immutable once published, and kept only 1 year at Apple) |

Rationale: Apple sales data cannot be had with App Manager, so it must be an explicit, separate opt-in with its own key slot in the UI. Do not suggest Admin; "Sales and Reports" is the least-privilege role Apple names for this.

### Should wait

- **Apple crash rate.** Needs an Admin key to create the request, async with 1-2 day start, 35-day instance retention (so the app must poll and cache continuously), 5-user privacy floor, counts only (a rate needs App Sessions as denominator). Revisit once the app has a background sync and an Admin-key one-shot flow.
- **Play proceeds.** Needs the global "View financial data" permission (all apps' financials) and only gives gross estimated sales in buyer currency, not proceeds. Net is monthly. Ship only if users ask, with strong labelling.
- **Apple finance reports / settled proceeds.** Needs Finance or Admin; monthly fiscal-calendar files; multi-region files with different currencies.
- **Play `reviews.list`.** Revisit only if we want replies later; needs the write-capable "Reply to reviews" permission and 200 GET/hour.
- **Worldwide Apple rating.** Needs N storefront calls and weighting; start with one storefront.
- **Apple Analytics-based installs/sessions.** Opt-in-only data, 5-user floor, wrong source for "downloads".

## 4. Open questions to verify with real accounts

1. Does `GET /v1/apps/{id}/customerReviews` return 200 for a key with role App Manager? (Help page says App Manager can view reviews; API page is silent.)
2. Does `GET /v1/salesReports` with an App Manager key return 403, as the docs imply?
3. Does a "Sales and Reports" key read `customerReviews`? (Community says no.)
4. Play: does `reviews.list` work with only "View app information and download bulk reports"?
5. Play: crash rate DAILY actual lag, and what a low-volume app returns (empty vs error).
6. Play: GCS access with only `devstorage.read_only` and Play permissions, from a service account in a project that never touched Cloud Storage; exact permission-propagation time; exact stats CSV timezone and `overview` filename.
7. Apple: exact error for a day with no sales.

## 5. Sources

Apple
- https://developer.apple.com/documentation/appstoreconnectapi/get-v1-salesreports
- https://developer.apple.com/documentation/appstoreconnectapi/get-v1-financereports
- https://developer.apple.com/documentation/appstoreconnectapi/get-v1-apps-_id_-customerreviews
- https://developer.apple.com/documentation/appstoreconnectapi/sales-and-finance
- https://developer.apple.com/documentation/appstoreconnectapi/creating-api-keys-for-app-store-connect-api
- https://developer.apple.com/documentation/appstoreconnectapi/identifying-rate-limits
- https://developer.apple.com/documentation/appstoreconnectapi/analytics
- https://developer.apple.com/documentation/appstoreconnectapi/downloading-analytics-reports
- https://developer.apple.com/documentation/appstoreconnectapi/post-v1-analyticsreportrequests
- https://developer.apple.com/documentation/appstoreconnectapi/power-and-performance-metrics-and-logs
- https://developer.apple.com/documentation/appstoreconnectapi/metriccategory
- https://developer.apple.com/documentation/analytics-reports
- https://developer.apple.com/documentation/analytics-reports/app-crashes
- https://developer.apple.com/documentation/analytics-reports/app-sessions
- https://developer.apple.com/documentation/analytics-reports/data-completeness-corrections
- https://developer.apple.com/help/app-store-connect-analytics/overview/analytics-reports-api/
- https://developer.apple.com/help/app-store-connect/reference/account-management/role-permissions
- https://developer.apple.com/help/app-store-connect/manage-your-team/edit-access-to-apps/
- https://developer.apple.com/help/app-store-connect/monitor-ratings-and-reviews/view-ratings-and-reviews/
- https://developer.apple.com/help/app-store-connect/getting-paid/view-payments-and-proceeds/
- https://developer.apple.com/help/app-store-connect/getting-paid/download-financial-reports/
- https://developer.apple.com/help/app-store-connect/view-sales-and-trends/download-and-view-reports/
- https://developer.apple.com/help/app-store-connect/reference/reporting/sales-and-trends-reports-availability
- https://developer.apple.com/help/app-store-connect/reference/reporting/summary-sales-report/
- https://developer.apple.com/help/app-store-connect/reference/reporting/product-type-identifiers
- https://performance-partners.apple.com/search-api
- https://developer.apple.com/support/roles/

Google
- https://developers.google.com/play/developer/reporting/overview
- https://developers.google.com/play/developer/reporting/metricset-intro
- https://developers.google.com/play/developer/reporting/reference/rest/v1beta1/vitals.crashrate
- https://developers.google.com/play/developer/reporting/reference/rest/v1beta1/vitals.crashrate/query
- https://developers.google.com/play/developer/reporting/reference/rest/v1beta1/FreshnessInfo
- https://developers.google.com/play/developer/reporting/limits
- https://developers.google.com/android-publisher/api-ref/rest/v3/reviews/list
- https://developers.google.com/android-publisher/reply-to-reviews
- https://developers.google.com/android-publisher/quotas
- https://support.google.com/googleplay/android-developer/answer/6135870 (Download and export monthly reports)
- https://support.google.com/googleplay/android-developer/answer/9844686 (Add developer account users and manage permissions)

Community / unofficial (used only where labelled)
- https://aso.dev/app-store-connect/api-key-access-levels/
- https://developer.apple.com/forums/thread/118428 (vendor number has no API)
- https://developer.apple.com/forums/thread/700371 (403 agreements)
- https://github.com/googleapis/nodejs-storage/issues/2688 (bucket 403)
- https://discuss.google.dev/gc/Google-Cloud-s-operations-suite/Allocating-Google-Play-Console-Bucket-Programmatically/m-p/620005
- https://android-developers.googleblog.com/2015/04/integrate-play-data-into-your-workflow.html
