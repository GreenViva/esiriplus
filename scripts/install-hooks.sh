#!/usr/bin/env bash
set -euo pipefail

HOOKS_DIR="$(git rev-parse --show-toplevel)/.git/hooks"
PRE_COMMIT="$HOOKS_DIR/pre-commit"

cat > "$PRE_COMMIT" << 'HOOK'
#!/usr/bin/env bash
set -euo pipefail

echo "Running ktlintCheck..."
./gradlew ktlintCheck --daemon --quiet

echo "Running detekt..."
./gradlew detekt --daemon --quiet

echo "Pre-commit checks passed!"
HOOK

chmod +x "$PRE_COMMIT"
echo "Pre-commit hook installed successfully."
