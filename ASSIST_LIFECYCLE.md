# Assist Lifecycle Notes

This project can be launched via `ACTION_ASSIST` and `SEARCH_LONG_PRESS`.
On some Android 14/15 builds (especially customized ROMs), the system treats this as a short-lived assist session and may background the activity immediately after launch.

## Why the activity gets closed/backgrounded

- `MainActivity` starts from assist entry points (`ACTION_ASSIST` / `SEARCH_LONG_PRESS`).
- After gesture/session teardown, the system can trigger `onPause()` with `isFinishing=false` and then `onStop()`.
- This happens even though the user did not explicitly dismiss the app.
- Result: launcher-like UI appears, then vanishes.

## Current workarounds in this repo

Location: `app/src/main/java/com/mrndstvndv/search/MainActivity.kt`

1. Intent rewriting
   - On `onCreate` and `onNewIntent`, assist actions are rewritten to `ACTION_MAIN`.
   - Goal: make this activity look like a normal launch and escape assist-session semantics.

2. Touch gate for gesture carry-over
   - `dispatchTouchEvent` blocks all events until a fresh `ACTION_DOWN` is observed.
   - Goal: avoid stray gesture tail events causing immediate accidental taps.

3. Background-prevention branch in `onPause`
   - If paused unexpectedly (`!isFinishing && launchedFromAssist`):
     - Try `moveTaskToFront()` only if `REORDER_TASKS` is granted.
     - If permission is missing, use fallback relaunch (`ACTION_MAIN`, `NEW_TASK|CLEAR_TOP|SINGLE_TOP`).
   - Safety: `moveTaskToFront()` is guarded to avoid `SecurityException` crashes.

## Known side effects

- A visible flicker/focus bounce can occur on devices where fallback relaunch is used.
- The app may briefly lose focus and return (`onPause` -> `onNewIntent` -> `onResume`).
- This is expected with the current fallback and is preferable to app closure/crash.

## Alternatives to explore

1. Trampoline activity
   - Add a tiny exported assist-entry activity that immediately launches `MainActivity` as normal `ACTION_MAIN`, then finishes.
   - Pros: isolates assist-session lifecycle from main UI.
   - Cons: can still flicker if transition is not tuned.

2. Dedicated transparent shell + delayed handoff
   - Keep shell in assist path, then hand off to `MainActivity` after assist teardown signal/time window.
   - Pros: more control over transition timing.
   - Cons: extra complexity and state management.

3. Launch/task mode and affinity experiments
   - Test `singleTask`/task affinity combinations for assist entry.
   - Pros: might reduce relaunch churn on some OEM builds.
   - Cons: can introduce unexpected back stack behavior.

4. Transition/animation tuning
   - Remove/replace per-call transition overrides and tune theme window animations.
   - Pros: can reduce perceived flicker even if lifecycle still bounces.
   - Cons: visual improvement only, not root-cause elimination.

5. Optional privileged path for rooted/Shizuku users
   - Keep current unprivileged fallback as default.
   - Optionally provide a privileged route to stronger task control where available.
   - Cons: not portable for standard installs.

## Validation guidance

- Confirm no `SecurityException` from `moveTaskToFront`.
- Confirm app remains visible after assist launch.
- Measure flicker frequency and focus changes via debug logs.
- Test across at least one AOSP-like build and one OEM/custom ROM build.
