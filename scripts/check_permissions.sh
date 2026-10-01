#!/usr/bin/env bash
# Fails if the packaged APK requests any permission other than the allow-listed ones.
# androidx.core declares a signature-level <app id>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION used
# internally by ContextCompat.registerReceiver; it is not a user-facing or network permission.
set -euo pipefail
APK="$1"
AAPT2="${AAPT2:-aapt2}"
APP_ID="${APP_ID:-com.sumpilot}"
ALLOWED="${APP_ID}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

echo "Permissions in $APK:"
"$AAPT2" dump permissions "$APK" | tee /tmp/permissions.txt
requested=$(grep -E "^uses-permission" /tmp/permissions.txt | sed -E "s/.*name='([^']+)'.*/\1/" || true)
bad=0
for p in $requested; do
  if [[ "$p" != "$ALLOWED" ]]; then
    echo "::error::Unexpected permission: $p"
    bad=1
  fi
done
for forbidden in android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE; do
  if grep -q "$forbidden" /tmp/permissions.txt; then
    echo "::error::Forbidden permission present: $forbidden"
    bad=1
  fi
done
if [[ $bad -ne 0 ]]; then exit 1; fi
echo "Permission check passed (only: ${requested:-none})."
