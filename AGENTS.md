## Project Guidelines

### Kotlin Development
- After any Kotlin file change, run: `./gradlew :app:compileDebugKotlin`
- First compilation per session requires escalated permissions (gradle needs ~/.gradle access)

### Commit Messages
Follow semantic commit format with short, concise titles:

**Format:** `<type>[optional scope]: <description>`

**Types:**
- `feat` - new features
- `fix` - bug fixes
- `update` - enhancements to existing features
- `ui` - UI changes
- `chore` - maintenance tasks
- `ci` - CI/CD changes
- `doc` - documentation
- `agent` - agent/AI configuration changes
- `refactor` - code refactoring
- `perf` - performance improvements

**Rules:**
- Max 50 characters for the entire title
- Imperative mood: "add", "fix", "update" (not "added", "fixed", "updated")
- All lowercase after the type prefix
- No period at the end
- Optional scope in parentheses: `fix(ui):`, `update(ranking):`
- Body is optional, only for complex changes

**Examples:**
- `feat: add dark mode toggle`
- `fix(ranking): exclude utils from freq calc`
- `ui: update icon tint for permission button`
- `update(freq ranking): lower normalization from 4 to 2`

### Coder Subagent Usage
- **Prompt Optimization**: Modify the prompt to optimize for agents. Provide clear, exact, detailed instructions including specific tasks, file paths, context, and expected output format. Avoid ambiguity.
- **Invocation Triggers**: Feel free to run the coder subagent if the user says "do it" or phrases along those lines.
- **Usage Thresholds**:
  - **Use Subagent**: If the change is larger than a 1-5 line tweak, encompasses multiple files, or requires multiple complex edits in different positions within the same file.
  - **Use Main Agent**: If the change is a small tweak (1-5 lines) or involves simple, repetitive updates (like renaming a symbol) even if scattered across the file.
