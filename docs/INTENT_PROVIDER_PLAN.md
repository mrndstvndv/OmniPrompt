# IntentProvider Implementation Plan

## Overview

Add a new provider that allows launching Android intents from the search app. Users define "triggers" (short keywords) that, when typed followed by a URL or text, launch an intent to a specified app.

## Design Philosophy

- **Simple**: Users just add a "trigger" (like `ytdl`) and the URL/args come after
- **Flexible**: Support all Android Intent extras, actions, and data types

---

## Implementation Steps

### 1. Create IntentConfig Data Class

**File**: `provider/intent/IntentConfig.kt`

```kotlin
data class IntentConfig(
    val id: String,
    val trigger: String,           // e.g., "ytdl" → type "ytdl https://..."
    val title: String,             // e.g., "Download Video"
    val packageName: String,       // Target app package
    val action: String = Intent.ACTION_SEND,  // SEND, VIEW, etc.
    val type: String = "text/plain",         // MIME type
    val extraKey: String? = null,   // Optional: custom extra key
    val extraValue: String? = null, // Optional: custom extra value ($query replaced)
)
```

### 2. Create IntentSettings Data Class

**File**: `provider/intent/IntentSettings.kt`

```kotlin
data class IntentSettings(
    val configs: List<IntentConfig> = emptyList(),
) : ProviderSettings {
    companion object { /* JSON serialization */ }
}
```

### 3. Create IntentProvider

**File**: `provider/intent/IntentProvider.kt`

Key logic:
- Match query trigger against configured triggers
- Extract payload (everything after the trigger)
- Build Intent with configured action, package, type, and extras
- Launch via `startActivity()`

### 4. Create IntentSettingsScreen

**File**: `ui/settings/IntentSettingsScreen.kt`

Reuse existing patterns from `TermuxSettingsScreen`:
- List of configured intents
- Add/Edit dialog
- Delete functionality

### 5. Register in SettingsActivity

Add:
- Screen enum entry
- Repository creation
- Navigation to/from screen

### 6. Register in MainActivity

Add provider to the list of available providers with proper initialization.

---

## File Structure

```
provider/intent/
├── IntentProvider.kt          # Main provider logic
├── IntentSettings.kt          # Settings model + JSON serialization
└── IntentSettingsScreen.kt   # Settings UI

# Also need to add:
# - Repository factory function (createIntentSettingsRepository)
# - Navigation in SettingsActivity
# - Provider registration in MainActivity
```

---

## Default Configurations

Pre-load these common intents:

```kotlin
val defaultIntentConfigs = listOf(
    // YTDLnis variants
    IntentConfig("ytdlnis", "ytdl", "YTDLnis", "com.deniscerri.ytdl"),
    IntentConfig("ytdlnis_video", "ytdlv", "YTDLnis Video", "com.deniscerri.ytdl", 
                 extraKey = "TYPE", extraValue = "video"),
    IntentConfig("ytdlnis_audio", "ytdla", "YTDLnis Audio", "com.deniscerri.ytdl",
                 extraKey = "TYPE", extraValue = "audio"),
    IntentConfig("ytdlnis_command", "ytdlc", "YTDLnis Command", "com.deniscerri.ytdl",
                 extraKey = "TYPE", extraValue = "command"),
    
    // Generic
    IntentConfig("share", "share", "Share", "", action = Intent.ACTION_SEND),
    IntentConfig("open_url", "open", "Open URL", "", action = Intent.ACTION_VIEW, type = "*/*"),
)
```

---

## Usage Examples

| Input | Trigger | Result |
|-------|---------|--------|
| `ytdl https://youtube.com/watch?v=...` | ytdl | Opens YTDLnis ShareActivity with URL |
| `ytdlv https://youtube.com/watch?v=...` | ytdlv | Downloads as video (TYPE=video) |
| `ytdla https://youtube.com/watch?v=...` | ytdla | Downloads as audio (TYPE=audio) |
| `open https://google.com` | open | Opens URL in browser |
| `share Check this out!` | share | Opens system share sheet |

---

## Future Enhancements (Not in Initial Version)

- Multiple extras per intent
- Data URI support
- Category support
- Custom flags
- Component name (class) targeting
