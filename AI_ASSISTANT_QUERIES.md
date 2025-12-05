# AI Assistant Queries

This document describes the AI Assistant Queries feature, which allows users to send queries directly to AI apps (like Gemini and ChatGPT) from the search interface.

## Overview

The feature is integrated into the `AppListProvider` and transforms the AI assistant's app result based on the user's query. Instead of creating a separate result, the existing app entry is modified to show an "Ask" action when a query is detected.

## Supported AI Assistants

| ID | Package Name | Display Name | Trigger Name |
|----|--------------|--------------|--------------|
| `gemini` | `com.google.android.apps.bard` | Gemini | `gemini` |
| `chatgpt` | `com.openai.chatgpt` | ChatGPT | `chatgpt` |

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
    AiAssistant(
        id = "chatgpt",
        packageName = "com.openai.chatgpt",
        displayName = "ChatGPT",
        triggerName = "chatgpt"
    ),
    // Add new assistants here
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
- `ask chatgpt explain quantum physics`
- `ask chat what is machine learning` (fuzzy match on "chat")

### 2. Direct Trigger: `<assistant> <query>`

Examples:
- `gemini what is the weather`
- `gemi how do I cook pasta` (fuzzy match on "gemi")
- `chatgpt explain this code`
- `chat what is AI` (fuzzy match on "chat")

**Note:** The direct trigger only activates when there is query content after the assistant name. Typing just `gemini` or `chatgpt` will show the normal app launch result.

## Behavior Matrix

### Gemini

| User Input | Title | Subtitle | Action |
|------------|-------|----------|--------|
| `gemini` | `Gemini` | `com.google.android.apps.bard` | Launch app |
| `gemi` | `Gemini` | `com.google.android.apps.bard` | Launch app (fuzzy match) |
| `ask gemini` | `Gemini` | `com.google.android.apps.bard` | Launch app (no query) |
| `gemini what is AI` | `Ask: what is AI` | `Gemini` | Send query to Gemini |
| `gemi weather` | `Ask: weather` | `Gemini` | Send query (fuzzy match) |
| `ask gemini weather` | `Ask: weather` | `Gemini` | Send query to Gemini |
| `ask gemi what` | `Ask: what` | `Gemini` | Send query (fuzzy match) |

### ChatGPT

| User Input | Title | Subtitle | Action |
|------------|-------|----------|--------|
| `chatgpt` | `ChatGPT` | `com.openai.chatgpt` | Launch app |
| `chat` | `ChatGPT` | `com.openai.chatgpt` | Launch app (fuzzy match) |
| `ask chatgpt` | `ChatGPT` | `com.openai.chatgpt` | Launch app (no query) |
| `chatgpt explain code` | `Ask: explain code` | `ChatGPT` | Send query to ChatGPT |
| `chat hello` | `Ask: hello` | `ChatGPT` | Send query (fuzzy match) |
| `ask chatgpt help me` | `Ask: help me` | `ChatGPT` | Send query to ChatGPT |

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
- `chat` → `chatgpt`

## Future Considerations

### Potential Enhancements

1. **Custom intent formats**: Some AI apps may require different intent structures. The `AiAssistant` data class could be extended with a custom intent builder lambda.

2. **Voice input**: Integration with voice search to send spoken queries.

3. **Response handling**: If AI apps support returning responses via `startActivityForResult`, results could be displayed inline.

4. **Per-assistant settings**: Allow enabling/disabling specific assistants individually.

### Known AI App Intent Formats

When adding new assistants, verify their intent requirements using `adb`:

```bash
# Find package name
adb shell pm list packages | grep -i <app_name>

# Check for ACTION_SEND support
adb shell dumpsys package <package_name> | grep -E "Action:|Category:|mimeType"

# Test the intent
adb shell 'am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "test query" -p <package_name>'
```

| App | Package Name | Intent Format | Status |
|-----|--------------|---------------|--------|
| Gemini | `com.google.android.apps.bard` | `ACTION_SEND` with `EXTRA_TEXT` | Supported |
| ChatGPT | `com.openai.chatgpt` | `ACTION_SEND` with `EXTRA_TEXT` | Supported |
| Grok | `ai.x.grok` | N/A | Not supported (no `ACTION_SEND` handler) |
| Claude | TBD | TBD | Needs investigation |
| Copilot | TBD | TBD | Needs investigation |

### Grok Investigation Notes

Grok (`ai.x.grok`) was investigated but does not support third-party queries:
- No `ACTION_SEND` intent filter
- No `ACTION_WEB_SEARCH` intent filter
- No `ACTION_ASSIST` intent filter
- Deep links (`https://grok.com`, `xai-grok://`) do not accept query parameters
- The `xai-grok://voice*` schemes are for voice call control, not query input

If Grok adds support in the future, the package name is `ai.x.grok` and the trigger name would likely be `grok`.
