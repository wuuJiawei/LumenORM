# Repository Guidelines

## Project Structure & Module Organization
- `pom.xml` defines the Maven build (Java 21) and is currently single-module.
- Base Java package is `io.lighting.lumen` for production and tests.
- `src/main/java` holds production code.
- `src/main/resources` is for templates, config, or other runtime assets.
- `src/test/java` and `src/test/resources` hold tests and fixtures.
- `target/` is the Maven build output and is ignored.

## Build, Test, and Development Commands
- `mvn clean package` builds and runs unit tests, writing output to `target/`.
- `mvn test` runs the test suite only.
- `mvn -DskipTests package` builds without tests when iterating locally.
- Ensure JDK 21 is active (`JAVA_HOME`) to match `pom.xml`.

## Coding Style & Naming Conventions
- No formatter or linter is configured yet; follow standard Java conventions.
- Indent 4 spaces (no tabs) and keep braces on the same line.
- Use `lowercase` for packages, `PascalCase` for classes, `lowerCamelCase` for methods/fields, and `UPPER_SNAKE_CASE` for constants.
- Keep one public class per file and align file names with the class.

## Testing Guidelines
- Use JUnit for all unit tests; place them in `src/test/java` and name them `*Test` so Maven discovers them.
- Maintain unit test coverage at or above 90% for new and changed code.
- Put small, focused fixtures in `src/test/resources`.

## Architecture & Principles
- Follow the Open/Closed Principle: extend behavior with new types or strategies instead of editing core flows.
- Before writing code, do a "Linus perspective" architecture review: challenge interfaces, complexity, and long-term maintenance, and avoid cleverness that hurts clarity.

## Documentation Links
- Design and architecture notes live in `README.md`; keep this guide and any changes aligned with it.

## Commit & Pull Request Guidelines
- Git history is empty, so there is no established commit message pattern yet.
- Use short, imperative summaries (for example, "Add sql ast nodes") and explain non-obvious changes in the body.
- PRs should include a brief description, rationale, any doc updates, and the exact test command run (or "not run" with a reason).
