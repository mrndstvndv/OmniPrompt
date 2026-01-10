## Project Guidelines

### Kotlin Development
- After any Kotlin file change, run: `./gradlew :app:compileDebugKotlin`
- First compilation per session requires escalated permissions (gradle needs ~/.gradle access)

### Coder Subagent Usage
- **Prompt Optimization**: Modify the prompt to optimize for agents. Provide clear, exact, detailed instructions including specific tasks, file paths, context, and expected output format. Avoid ambiguity.
- **Invocation Triggers**: Feel free to run the coder subagent if the user says "do it" or phrases along those lines.
- **Usage Thresholds**:
  - **Use Subagent**: If the change is larger than a 1-5 line tweak, encompasses multiple files, or requires multiple complex edits in different positions within the same file.
  - **Use Main Agent**: If the change is a small tweak (1-5 lines) or involves simple, repetitive updates (like renaming a symbol) even if scattered across the file.
