# Ranking guide

This app has two layers of ranking:

1. **Provider-local ranking**: a provider decides the order of its own candidates before handing them to the app.
2. **Global result ranking**: `MainActivity.sortResults()` merges all provider results, then sorts them with provider rank and frequency data.

## Global ranking pipeline

Global sorting uses these inputs:

- `providerRank`: manual provider order from `ProviderRankingRepository`
- `frequencyKey`: stable identity for a reusable result/action
- `frequencyQuery`: bucket used for query-based ranking
- `excludeFromFrequencyRanking`: opt out of usage tracking

Behavior:

- If **frequency ranking is off**, results fall back to manual provider order.
- If **frequency ranking is on**, any result with `frequencyScore > 0` beats results with no frequency.
- Among ranked results, higher frequency wins.
- Unranked ties fall back to provider order.

So the main question is not just _"should this result be ranked?"_.
It is:

- **What stable thing are we ranking?**
- **What query bucket should compete together?**

## Ranking types used in the app

### 1. Manual provider order
Use when frequency data is missing or disabled.

Good for:
- default fallback ordering
- providers that should always stay roughly in a fixed position

Examples:
- all providers use this as fallback

---

### 2. Stable item + full query bucket
Use when the result is a reusable entity, but user choice still depends on the exact search text.

Pattern:
- `frequencyKey = stable item id`
- `frequencyQuery = full normalized query` (or leave null and let the app use the current query)

Good for:
- apps
- contacts
- files
- settings actions
- quicklinks

Why:
- the item is stable
- the query text is meaningful
- selecting `YouTube` for `yt` should not necessarily affect `maps`

---

### 3. Stable item + trigger/token bucket
Use when the first token chooses the action, and the rest is just payload/arguments.

Pattern:
- `frequencyKey = stable command/site/config id`
- `frequencyQuery = first token / trigger token`

Good for:
- Termux commands
- Intent configs
- web search engine triggers

Why:
- payload should not fragment ranking
- `g cats` and `g news` should reinforce the same Google trigger choice
- command arguments usually are not the reusable thing

---

### 4. Stable item + shared provider bucket
Use when the reusable choice is the tool itself, not the typed payload and not the exact alias.

Pattern:
- `frequencyKey = stable tool/action id`
- `frequencyQuery = provider-level shared bucket`

Good for:
- calculator
- text utilities

Why:
- `8+8` vs `9*9` are different payloads, but the reusable choice is still **Calculator**
- `b64`, `base64`, `url encode`, and the actual payload are different entry paths, but the reusable choice is still the **utility**

This is the right pattern for command-like tools where payload is transient.

---

### 5. Excluded helper/default results
Use when ranking would be noisy or misleading.

Pattern:
- `excludeFromFrequencyRanking = true`

Good for:
- invalid input states
- helper rows
- forced/default rows that should not learn

Why:
- they are not durable user intent
- they can dominate ranking for the wrong reasons

Examples:
- Text Utilities invalid input rows
- Web Search default site row

## Provider matrix

| Provider | Local ranking inside provider | Frequency identity | Frequency bucket | Notes |
| --- | --- | --- | --- | --- |
| App List | fuzzy score on label/package | package name | full query | reusable app entity |
| Contacts | fuzzy score, starred tie-break | contact id / sim id | full query | reusable person/entity |
| Calculator | single result | `calculator` | shared `calculator` bucket | rank the tool, not the expression |
| Text Utilities suggestions | fixed list order, then global ranking can lift used utilities | utility id | shared `text-utilities` bucket | rank the utility, not the payload |
| Text Utilities success results | single trigger result | utility id | shared `text-utilities` bucket | same semantic choice as suggestions |
| Text Utilities invalid results | none | excluded | excluded | helper/error row only |
| File Search | repository sort + heuristic score | document uri | full query | reusable file/folder |
| System Settings | fuzzy score | settings action | full query | reusable settings destination |
| Termux | fuzzy score on command title/path | command id | first token | args should not fragment ranking |
| Intent | fuzzy score on config title | config id | first token | payload should not fragment ranking |
| Web quicklinks | fuzzy score on title/domain | quicklink id | full query | destination depends on actual query |
| Web search site results | trigger/site matching | site id | trigger token | rank site choice, not search text payload |
| Web default site row | default fallback | excluded | excluded | should stay deterministic |

## Recommendations

### Text Utilities
**Recommended strategy:** stable utility id + shared `text-utilities` bucket.

Reason:
- the real reusable choice is the utility (`base64`, `trim`, `url`, etc.)
- payload is transient
- aliases like `b64` and `base64` should reinforce the same utility
- suggestion picks and successful executions should train the same utility choice

This is better than:
- ranking by exact payload
- ranking by exact alias only
- leaving suggestions fully unranked

### Web Search
**Recommended strategy:** keep the current split.

Use two behaviors:

1. **Quicklinks**: stable quicklink id + full query bucket
   - quicklinks behave like reusable destinations discovered from the actual query text
2. **Search engine/site results**: stable site id + trigger token bucket
   - the reusable choice is the site
   - the search payload should not fragment ranking
3. **Default site result**: keep excluded from frequency ranking
   - it is the deterministic fallback
   - ranking it tends to create noise, because it is already always present

## What to avoid

Avoid these patterns unless the result is truly reusable:

- `frequencyKey` that includes raw payload
- `frequencyKey` that includes hashes of transient output
- query buckets based on full payload when only the first token matters
- ranking default/helper/error rows

Bad example:
- calculator ranking by exact expression like `8+8`

Good example:
- calculator ranking by stable tool identity: `calculator`

## Rule of thumb

Pick the narrowest reusable identity:

- **entity search** → stable item + full query
- **triggered tools/commands** → stable item + first token
- **payload-driven utilities** → stable item + shared provider bucket
- **default/helper/error rows** → exclude
