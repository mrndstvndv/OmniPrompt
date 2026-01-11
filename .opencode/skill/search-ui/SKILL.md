---
name: search-ui
description: Search app UI architecture - Compose component hierarchy (SearchTheme, MainActivity, SettingsActivity), enum-based navigation pattern (no Navigation Component), dynamic theme system with Material 3, reusable settings components (SettingsGroup, SettingsSwitch, SettingsSliderRow, SettingsNavigationRow).
---

# UI Architecture

## Component Hierarchy

```
┌─────────────────────────────────────────────────────────────────┐
│                        SearchTheme                              │
│   (Dynamic colors, typography, shapes)                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   MainActivity                    SettingsActivity              │
│   ┌─────────────────────┐        ┌─────────────────────────┐   │
│   │                     │        │                         │   │
│   │  ┌───────────────┐  │        │  AnimatedContent        │   │
│   │  │ SearchField   │  │        │  (screen transitions)   │   │
│   │  └───────────────┘  │        │                         │   │
│   │                     │        │  ┌───────────────────┐  │   │
│   │  ┌───────────────┐  │        │  │ GeneralSettings   │  │   │
│   │  │RecentAppsList │  │        │  │ Screen            │  │   │
│   │  └───────────────┘  │        │  └───────────────────┘  │   │
│   │                     │        │           or            │   │
│   │  ┌───────────────┐  │        │  ┌───────────────────┐  │   │
│   │  │  ItemsList    │  │        │  │ WebSearchSettings │  │   │
│   │  │  (results)    │  │        │  │ Screen            │  │   │
│   │  └───────────────┘  │        │  └───────────────────┘  │   │
│   │                     │        │           or            │   │
│   │  ┌───────────────┐  │        │  ┌───────────────────┐  │   │
│   │  │ContactAction  │  │        │  │ ...other screens  │  │   │
│   │  │Sheet          │  │        │  └───────────────────┘  │   │
│   │  └───────────────┘  │        │                         │   │
│   │                     │        │                         │   │
│   └─────────────────────┘        └─────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Navigation Pattern

Settings uses enum-based screen state navigation (no Navigation Component):

```kotlin
// Location: SettingsActivity.kt

private enum class Screen {
    Home, Providers, Appearance, Behavior, Aliases, Ranking,
    WebSearch, FileSearch, TextUtilities, AppSearch, ProviderList,
    SystemSettings, ContactsSettings, BackupRestore, TermuxSettings
}

@Composable
fun SettingsNavigation() {
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    
    AnimatedContent(targetState = currentScreen) { screen ->
        when (screen) {
            Screen.Home -> GeneralSettingsScreen(
                onNavigate = { currentScreen = it }
            )
            Screen.Providers -> ProvidersSettingsScreen(...)
            Screen.WebSearch -> WebSearchSettingsScreen(...)
            // ... other screens
        }
    }
}
```

## Theme System

```kotlin
// Location: ui/theme/Theme.kt

@Composable
fun SearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

## Theme Files

| File                  | Purpose                              |
|-----------------------|--------------------------------------|
| `ui/theme/Theme.kt`   | SearchTheme, dynamic colors          |
| `ui/theme/Color.kt`   | Color definitions                    |
| `ui/theme/Type.kt`    | Typography                           |
| `ui/theme/MotionLocals.kt` | Animation preferences           |

## Reusable Settings Components

Located in `ui/components/settings/`:

| Component               | Purpose                                    |
|-------------------------|--------------------------------------------|
| `SettingsGroup`         | Groups related settings with a header      |
| `SettingsSwitch`        | Toggle switch with label and description   |
| `SettingsSliderRow`     | Slider for numeric values                  |
| `SettingsNavigationRow` | Clickable row that navigates to sub-screen |
| `SettingsTextField`     | Text input field for settings              |

### Usage Example

```kotlin
@Composable
fun ExampleSettingsScreen() {
    Column {
        SettingsGroup(title = "General") {
            SettingsSwitch(
                title = "Enable Feature",
                description = "Description of what this does",
                checked = isEnabled,
                onCheckedChange = { /* update */ }
            )
            
            SettingsSliderRow(
                title = "Value",
                value = sliderValue,
                onValueChange = { /* update */ },
                valueRange = 0f..100f
            )
            
            SettingsNavigationRow(
                title = "Sub-settings",
                onClick = { onNavigate(Screen.SubSettings) }
            )
        }
    }
}
```

## Main UI Components

Located in `ui/components/`:

| Component              | Purpose                           | Location                    |
|------------------------|-----------------------------------|-----------------------------|
| `SearchField`          | Search input field                | `components/SearchField.kt` |
| `ItemsList`            | Search results list               | `components/ItemsList.kt`   |
| `RecentAppsList`       | Recent apps horizontal list       | `components/RecentAppsList.kt` |
| `ContactActionSheet`   | Contact actions bottom sheet      | `components/ContactActionSheet.kt` |
| `BottomSheet`          | Generic bottom sheet              | `components/BottomSheet.kt` |

## Related Skills

- `search-overview` - Overall architecture
- `search-guides` - Adding new settings screens
