# AI Assistant Queries

This document describes the AI Assistant Queries feature, which allows users to send queries directly to AI apps (like Gemini) from the search interface.

## Overview

The feature is integrated into the `AppListProvider` and transforms the AI assistant's app result based on the user's query. Instead of creating a separate result, the existing app entry is modified to show an "Ask" action when a query is detected.

## Supported AI Assistants

| ID | Package Name | Display Name | Trigger Name |
|----|--------------|--------------|--------------|
| `gemini` | `com.google.android.apps.bard` | Gemini | `gemini` |

### Adding New AI Assistants

To add support for a new AI assistant, add an entry to the `AI_ASSISTANTS` list in `AppListProvider.kt`:

```kotlin
private val AI_ASSISTANTS = listOf(
    AiAssistant(
        id = "gemini",
        packageName = "com.google.android.apps.bard",
        displayName = "Gemini",
        triggerName = "gemini"
    ),
    // Add new assistants here:
    AiAssistant(
        id = "chatgpt",
        packageName = "com.openai.chatgpt",  // Verify actual package name
        displayName = "ChatGPT",
        triggerName = "chatgpt"
    )
)
```

**Requirements for new assistants:**
1. The app must support receiving text via `ACTION_SEND` intent with `EXTRA_TEXT`
2. The `packageName` must be the exact package name of the app
3. The `triggerName` is used for fuzzy matching (should be simple and recognizable)

## Trigger Patterns

The feature supports two trigger patterns:

### 1. Keyword Trigger: `ask <assistant> <query>`

Examples:
- `ask gemini what is the weather`
- `ask gemi how do I cook pasta` (fuzzy match on "gemi")

### 2. Direct Trigger: `<assistant> <query>`

Examples:
- `gemini what is the weather`
- `gemi how do I cook pasta` (fuzzy match on "gemi")

**Note:** The direct trigger only activates when there is query content after the assistant name. Typing just `gemini` will show the normal app launch result.

## Behavior Matrix

| User Input | Title | Subtitle | Action |
|------------|-------|----------|--------|
| `gemini` | `Gemini` | `com.google.android.apps.bard` | Launch app |
| `gemi` | `Gemini` | `com.google.android.apps.bard` | Launch app (fuzzy match) |
| `ask gemini` | `Gemini` | `com.google.android.apps.bard` | Launch app (no query) |
| `gemini what is AI` | `Ask: what is AI` | `Gemini` | Send query to Gemini |
| `gemi weather` | `Ask: weather` | `Gemini` | Send query (fuzzy match) |
| `ask gemini weather` | `Ask: weather` | `Gemini` | Send query to Gemini |
| `ask gemi what` | `Ask: what` | `Gemini` | Send query (fuzzy match) |

## Intent Format

When sending a query to an AI assistant, the following intent is used:

```kotlin
Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    setPackage(assistant.packageName)
    putExtra(Intent.EXTRA_TEXT, query)
}
```

## Settings

The feature can be toggled in **Settings > Applications > AI Assistant queries**.

- **Default:** Enabled
- **Setting key:** `aiAssistantQueriesEnabled` in `AppSearchSettings`

When disabled:
- The `ask <assistant> <query>` pattern is ignored
- The `<assistant> <query>` pattern is ignored
- AI assistant apps appear as normal app results

## Prerequisites

For the AI assistant result to appear:
1. The AI assistant app must be installed on the device
2. The "AI Assistant queries" setting must be enabled

If the app is not installed, the `ask <assistant>` pattern will not match anything (no result shown).

## Implementation Details

### Files

| File | Purpose |
|------|---------|
| `AppListProvider.kt` | Main logic for parsing queries and building results |
| `ProviderSettingsRepository.kt` | `AppSearchSettings.aiAssistantQueriesEnabled` field |
| `AppSearchSettingsScreen.kt` | Settings toggle UI |

### Key Functions in `AppListProvider.kt`

- `parseAskQuery()` - Parses `ask <assistant> <query>` pattern
- `parseDirectAiQuery()` - Parses `<assistant> <query>` pattern
- `buildAiQueryIntent()` - Creates the `ACTION_SEND` intent

### Fuzzy Matching

The trigger name is matched using `FuzzyMatcher` with a minimum score threshold of `ASK_TRIGGER_MIN_SCORE = 40`. This allows partial matches like:
- `gemi` → `gemini`
- `gem` → `gemini`

## Future Considerations

### Potential Enhancements

1. **Custom intent formats**: Some AI apps may require different intent structures. The `AiAssistant` data class could be extended with a custom intent builder lambda.

2. **Voice input**: Integration with voice search to send spoken queries.

3. **Response handling**: If AI apps support returning responses via `startActivityForResult`, results could be displayed inline.

4. **Per-assistant settings**: Allow enabling/disabling specific assistants individually.

### Known AI App Intent Formats

When adding new assistants, verify their intent requirements:

| App | Intent Format | Notes |
|-----|---------------|-------|
| Gemini | `ACTION_SEND` with `EXTRA_TEXT` | Confirmed working |
| ChatGPT | TBD | Needs verification |
| Claude | TBD | Needs verification |
| Copilot | TBD | Needs verification |
