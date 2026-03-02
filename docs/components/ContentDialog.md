# ContentDialog - Design Specification

## Problem

Currently, dialogs in the app use `ScrimDialog` inconsistently with manual layout management. This leads to:

1. **Excessive vertical space** - Using `.weight(1f)` on scrollable content pushes buttons to the bottom, leaving large gaps when content is short
2. **Inconsistent patterns** - Each dialog manually implements scrolling, spacing, and layout
3. **No auto-sizing** - Dialogs don't adjust height to fit their content naturally

## Solution

Create a reusable `ContentDialog` component that provides:

- **Structured layout** - Title, scrollable content, and buttons with proper spacing
- **Auto-scrolling** - Content scrolls only when it overflows available space
- **Auto-height** - Dialog fits content by default, scrolls when needed

## Component API

```kotlin
@Composable
fun ContentDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
)
```

### Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `onDismiss` | `() -> Unit` | Called when dialog should close (scrim tap or back) |
| `modifier` | `Modifier` | Modifier for the dialog Surface |
| `title` | `@Composable () -> Unit` | Dialog title (sticky at top) |
| `buttons` | `@Composable RowScope.() -> Unit` | Action buttons (sticky at bottom) |
| `content` | `@Composable ColumnScope.() -> Unit` | Main dialog content (scrollable if overflow) |

## Implementation Details

### Layout Structure

```
┌─────────────────────────────────────┐
│         Scrim (tap to dismiss)     │
├─────────────────────────────────────┤
│  ┌───────────────────────────────┐  │
│  │  Title (sticky)               │  │
│  ├───────────────────────────────┤  │
│  │                               │  │
│  │  Content                      │  │
│  │  (scrollable if overflow)    │  │
│  │                               │  │
│  ├───────────────────────────────┤  │
│  │  Buttons (sticky)             │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

### Key Behaviors

1. **Content-first sizing** - Dialog height = content height + title + buttons + padding
2. **Conditional scrolling** - Content scrolls only when:
   - Total dialog height > ~60% of screen, OR
   - Content explicitly requests more space
3. **Sticky positioning** - Title and buttons stay fixed while content scrolls

### Internal Implementation

```kotlin
@Composable
fun ContentDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScrimDialog(
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val screenHeight = maxHeight
            
            // If content will exceed ~60% of screen, enable scrolling
            val shouldScroll = screenHeight > 500.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Title (sticky)
                title()
                
                Spacer(modifier = Modifier.height(16.dp))

                // Content
                if (shouldScroll) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = maxHeight - 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        content()
                    }
                } else {
                    Column(content = content)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons (sticky)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    content = buttons
                )
            }
        }
    }
}
```

## Migration Plan

### Phase 1: Create Component
1. Add `ContentDialog` to `ui/components/`
2. Update `ScrimDialog` if needed for the new component

### Phase 2: Migrate Complex Dialogs
Prioritized by current issues:

| Priority | File | Dialog | Complexity |
|----------|------|--------|------------|
| 1 | IntentSettingsScreen.kt | IntentConfigAddDialog, IntentConfigEditDialog | High (has weight issue) |
| 2 | AppSearchSettingsScreen.kt | AddPinnedAppDialog | Medium |
| 3 | WebSearchSettingsScreen.kt | 4 dialogs | Medium |
| 4 | TermuxSettingsScreen.kt | 2 dialogs | Medium |
| 5 | TermuxPermissionDialog.kt | - | Low |
| 6 | WebSearchProviderSettingsDialog.kt | - | Low |

### Phase 3: Deprecate Old Patterns
- Mark `ScrimDialog` with content parameter as `@Deprecated`
- Keep `ScrimDialog` for simple use cases (no structured content needed)

## Usage Examples

### Before (current pattern)
```kotlin
ScrimDialog(onDismiss = onDismiss) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(text = "Add Intent", style = MaterialTheme.typography.titleLarge)
        
        Column(
            modifier = Modifier
                .weight(1f)  // Causes large gap
                .verticalScroll(rememberScrollState())
        ) {
            // All fields...
        }
        
        Row(...) { /* buttons */ }
    }
}
```

### After (new pattern)
```kotlin
ContentDialog(
    onDismiss = onDismiss,
    title = {
        Text(text = "Add Intent", style = MaterialTheme.typography.titleLarge)
    },
    buttons = {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Button(onClick = onSave) { Text("Save") }
    },
    content = {
        // All fields - scrolls automatically if needed
        OutlinedTextField(...)
        OutlinedTextField(...)
    }
)
```

## Benefits

1. **Less code** - No manual weight/height management
2. **Better UX** - No large gaps, natural height
3. **Consistency** - All dialogs follow same pattern
4. **Maintainability** - Single place to update dialog styling
