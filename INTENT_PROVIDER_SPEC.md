# IntentProvider UX Improvement Specification

## Overview

Improve the IntentProvider user experience by allowing users to easily discover and configure Android intents from installed apps, with flexible payload customization.

---

## Current Behavior

### How It Works Now

1. **Keyword Matching**: User types first word → fuzzy matched against configured intent titles
2. **Payload Extraction**: Everything after the title is treated as payload
3. **Intent Launch**: Payload injected as:
   - `Intent.EXTRA_TEXT` for `ACTION_SEND`
   - URI data for `ACTION_VIEW`
   - `$query` placeholder in custom extras

### Limitations

- No way to discover what intents an app supports
- Must manually know intent actions (SEND, VIEW, etc.)
- Payload always appended after title — no customization
- No support for intents that don't need payloads (settings, etc.)
- `$query` replacement only works for custom extras, not main payload

---

## Proposed UX Flow

### Step 1: Select App

- Query `PackageManager` for apps that support our target intents:
  - `ACTION_SEND` (share)
  - `ACTION_VIEW` (open URLs)
  - `ACTION_SENDTO` (send to address)
- Only show apps that handle at least one of these intent types
- Display app icon + name + package
- Search/filter capability

### Step 2: Select Intent (Auto-populated from App)

- Query `PackageManager.queryIntentActivities()` for the selected app
- Show intents the app supports as selectable cards:
  - `ACTION_SEND` → "Share content"
  - `ACTION_VIEW` → "Open URL / View content"
  - `ACTION_SENDTO` → "Send to address"
  - Custom intent-filters the app declares
- **Note**: `ACTION_MAIN` (launch app) is handled by a separate AppLauncherProvider
- **User taps an intent → Action is auto-selected**

### Step 3: Configure Intent

Present configuration form with fields (action is pre-filled from selection):

| Field | Type | Description | Auto-filled? |
|-------|------|-------------|---------------|
| `Title` | text | Display name for fuzzy matching | ❌ |
| `Action` | auto | Intent action (pre-selected) | ✅ from step 2 |
| `MIME Type` | dropdown | Content type (pre-populated from app) | ✅ from intent query |
| `Package` | auto | Target app package | ✅ from app selection |
| **Payload Template** | text | Customizable sent text | ❌ |
| Extra Key | text | Custom extra key | ❌ |
| Extra Value | text | Custom extra value | ❌ |

**Note**: 
- User only manually edits Title, Payload Template, and Custom Extras
- MIME Type is a dropdown pre-populated with types the app declares for that intent
- "Any" option available if app supports wildcard or multiple types

---

## Configuration Model

### IntentConfig (Updated)

```kotlin
/**
 * Single extra key/value pair with $query replacement support.
 */
data class IntentExtra(
    val key: String,           // e.g., "android.intent.extra.STREAM"
    val value: String,         // e.g., "$query" or "fixed value"
)

/**
 * Configuration for a single intent.
 */
data class IntentConfig(
    val id: String = UUID.randomUUID().toString(),
    val title: String,                      // e.g., "Instagram"
    val packageName: String,                 // Target app
    val action: String = Intent.ACTION_SEND, // SEND, VIEW, SENDTO, etc.
    val type: String? = null,                // MIME type (null = any/not set)
    
    // Payload customization
    val payloadTemplate: String? = null,     // "yabai $query" or null for raw
    
    // Custom extras (multiple supported)
    val extras: List<IntentExtra> = emptyList(),
)
```

### MIME Type Collection

When querying an app's intents, collect all MIME types from all intent-filters for the selected action:

```kotlin
// Pseudocode for collecting MIME types
val mimeTypes = mutableSetOf<String>()
resolveInfos.forEach { info ->
    info.activityInfo?.intentFilters?.forEach { filter ->
        filter.mimeTypes?.forEach { mimeType ->
            mimeTypes.add(mimeType)
        }
    }
}
// Result: ["image/*", "video/*", "image/gif"]
```

### Payload Resolution Logic

```
User input: "instagram hello world"
Title match: "instagram"
Payload: "hello world"

payloadTemplate: null      → sends: "hello world"
payloadTemplate: "$query" → sends: "hello world"
payloadTemplate: "yabai $query" → sends: "yabai hello world"
payloadTemplate: "check this out:" → sends: "check this out:" (no $query replacement)
```

### Extras Resolution Logic

Each extra in the `extras` list is processed with `$query` replacement:

```kotlin
// Example config:
extras = [
    IntentExtra("android.intent.extra.STREAM", "$query"),
    IntentExtra("android.intent.extra.SUBJECT", "Check this out")
]

// User input: "instagram myphoto.jpg"
// Result:
putExtra("android.intent.extra.STREAM", "myphoto.jpg")  // $query replaced
putExtra("android.intent.extra.SUBJECT", "Check this out")  // static value
```

---

## Supported Intent Types

### Intents With Payloads

| Intent | Payload Purpose | Example |
|--------|-----------------|---------|
| `ACTION_SEND` | Text/content to share | "Check this: $query" |
| `ACTION_VIEW` | URL or URI | $query (as URL) |
| `ACTION_SENDTO` | Email/message address | "mailto:user@example.com?body=$query" |

### Intents Without Payloads

| Intent | Purpose | Payload Handling |
|--------|---------|------------------|
| `ACTION_SETTINGS` | Open settings | Ignored |
| `ACTION_WIFI_SETTINGS` | WiFi settings | Ignored |
| `ACTION_BLUETOOTH_SETTINGS` | Bluetooth settings | Ignored |
| `ACTION_VOICE_COMMAND` | Voice assistant | Ignored |

**Note**: `ACTION_MAIN` (launch app) is handled by AppLauncherProvider, not IntentProvider.

**Implementation Note**: Add `requiresPayload: Boolean = true` to IntentConfig, defaulting based on action type.

---

## UI Mockup (Text-based)

### App Selection Screen

```
┌─────────────────────────────────┐
│  Select App                     │
│  (apps with share/view/sendto) │
├─────────────────────────────────┤
│  🔍 Search apps...              │
├─────────────────────────────────┤
│  📷 Instagram          com.instagram.android │
│  📺 YouTube            com.google.android.youtube │
│  💬 Telegram           org.telegram.messenger   │
│  📧 Gmail              com.google.android.gm   │
│  ...                                     │
└─────────────────────────────────┘
```

### Intent Selection Screen (Step 2)

```
┌─────────────────────────────────┐
│  ← Instagram                    │
├─────────────────────────────────┤
│  Select an intent this app     │
│  supports:                     │
├─────────────────────────────────┤
│  ┌───────────────────────────┐  │
│  │ 📤 Share content          │  │
│  │     ACTION_SEND           │  │
│  └───────────────────────────┘  │
│                                 │
│  ┌───────────────────────────┐  │
│  │ 🔗 Open URL / View        │  │
│  │     ACTION_VIEW           │  │
│  └───────────────────────────┘  │
│                                 │
│  ┌───────────────────────────┐  │
│  │ 📧 Send to address        │  │
│  │     ACTION_SENDTO         │  │
│  └───────────────────────────┘  │
│                                 │
│  Note: Launch app is handled   │
│  by AppLauncherProvider        │
│                                 │
└─────────────────────────────────┘
```

### Intent Configuration Screen (Step 3)

```
┌─────────────────────────────────┐
│  ← Instagram                    │
├─────────────────────────────────┤
│  Title *                       │
│  ┌───────────────────────────┐  │
│  │ Instagram                 │  │
│  └───────────────────────────┘  │
│                                 │
│  Action (auto-selected)        │
│  ┌───────────────────────────┐  │
│  │ ACTION_SEND               │  │
│  └───────────────────────────┘  │
│                                 │
│  MIME Type (pre-populated)     │
│  ┌───────────────────────────┐  │
│  │ image/*               ▼  │  │
│  ├───────────────────────────┤  │
│  │ image/*                  │  │
│  │ video/*                  │  │
│  │ image/gif                │  │
│  │ (any)                    │  │
│  └───────────────────────────┘  │
│                                 │
│  Payload Template (optional)   │
│  ┌───────────────────────────┐  │
│  │ yabai $query              │  │
│  └───────────────────────────┘  │
│  💡 $query = user input after title │
│                                 │
│  Custom Extras                  │
│  ┌───────────────────────────┐  │
│  │ EXTRA_TEXT      │ $query  │ ✕│  │
│  ├───────────────────────────┤  │
│  │ EXTRA_SUBJECT   │ hello   │ ✕│  │
│  └───────────────────────────┘  │
│                                 │
│  + Add Extra                    │
│                                 │
│  ┌───────────────────────────┐  │
│  │       Save Intent         │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘
```

**Extra Input Fields:**
```
┌─────────────────────────────────┐
│  Extra Key                      │
│  ┌───────────────────────────┐  │
│  │ EXTRA_TEXT             ▼ │  │
│  ├───────────────────────────┤  │
│  │ EXTRA_TEXT                 │  │
│  │ EXTRA_SUBJECT              │  │
│  │ EXTRA_TITLE                │  │
│  │ android.intent.extra.STREAM│ │
│  │ (custom key)              │  │
│  └───────────────────────────┘  │
│                                 │
│  Extra Value                    │
│  ┌───────────────────────────┐  │
│  │ $query                     │  │
│  └───────────────────────────┘  │
│  💡 Use $query for dynamic text │
└─────────────────────────────────┘
```

---

## Implementation Tasks

### Phase 1: App Discovery
- [ ] Create `AppInfo` data class with package name, app name, icon
- [ ] Implement `PackageManager` query for installed apps
- [ ] Add app list UI with search/filter

### Phase 2: Intent Querying
- [ ] Query `queryIntentActivities()` for selected app
- [ ] Parse and categorize supported intents
- [ ] Show intent options with descriptions

### Phase 3: Configuration Form
- [ ] Build intent config form UI
- [ ] Add payload template field with `$query` support
- [ ] Support multiple custom extras (add/remove rows)
- [ ] Include common EXTRA_* constants in dropdown

### Phase 4: Payload Handling
- [ ] Update `IntentConfig` with `payloadTemplate`
- [ ] Implement template resolution in `executeIntent()`
- [ ] Handle intents that don't need payloads

### Phase 5: Testing
- [ ] Test with various apps (Instagram, Telegram, etc.)
- [ ] Verify payload template replacement
- [ ] Test intents without payloads (settings)

---

## Backward Compatibility

- Existing `IntentConfig` entries without `payloadTemplate` default to current behavior (raw payload)
- Existing single `extraKey` / `extraValue` fields should be migrated to `extras` list on load
- Migration path: Add `null` defaults for new fields, convert single extra to list

### JSON Schema (Updated)

```kotlin
// IntentExtra as JSON object
{
    "key": "android.intent.extra.STREAM",
    "value": "$query"
}

// IntentConfig with extras array
{
    "id": "...",
    "title": "Instagram",
    "packageName": "com.instagram.android",
    "action": "android.intent.action.SEND",
    "type": "image/*",
    "payloadTemplate": "yabai $query",
    "extras": [
        {"key": "android.intent.extra.STREAM", "value": "$query"}
    ]
}
```

---

## Open Questions

*(None remaining — all decisions made)*

---

## Related Files

- `IntentProvider.kt` - Main provider logic
- `IntentConfig.kt` - Configuration data class
- `IntentSettings.kt` - Settings repository
- `IntentSettingsScreen.kt` - Settings UI

---

*Last updated: 2026-03-02*
