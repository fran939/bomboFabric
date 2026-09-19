# General Coding & Environment Rules
- Stack: Java (Fabric / Yarn mappings) for Minecraft modding, and JavaScript/TypeScript or PowerShell where applicable.
- Style: Keep code clean, performant, and avoid heavy reflections or unnecessary allocations in tick loops and render passes.
- Code output: Provide direct, working code implementations. Omit conversational preamble and setup filler.
- Endpoints: The backend web UI is hosted at `bombo.dpdns.org` and API endpoints are at `api.bombo.dpdns.org`. When building or querying data parsers, structure requests around JSON schemas from this API.

# Security & Secrets Protection
- CRITICAL: Never commit, expose, or generate plaintext secrets in source files.
- Before committing or pushing, explicitly inspect files to ensure no public or private API keys, tokens, Discord webhooks, passwords, or credentials are left hardcoded.
- Always isolate secrets into environment variables, local `.env` files, or `.gitignore`-protected configuration files.

# Git Automation & Clean Commits
- Commit & Push Automation:
  - After modifying, creating, or refactoring code for any requested task, stage the changes, create a Git commit, and push directly to the remote GitHub repository.
  - The commit message must specifically itemize every feature added, bug fixed, or file changed (avoid vague messages like "update files").
- File Cleanliness:
  - Do NOT commit useless, generated, or transient files and directories (e.g., `.gradle/`, `build/`, `node_modules/`, `out/`, `.antigravity/`, `.vs/`, `.log`, or OS metadata like `Thumbs.db` and `.DS_Store`).
  - Verify these entries exist in `.gitignore` before executing commits.
- Changelog Maintenance:
  - Every time a new feature, fix, or code update is implemented, update the project's changelog file (`changelog.json` or `CHANGELOG.md`).
  - Document the exact changes alongside the timestamp or version bump.