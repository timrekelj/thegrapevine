#!/bin/sh
# Prints a fresh sign-in code for a user of the local stack. The experimental
# local stack can't put the code in the email, so this asks Auth for one.
# Local only; the new code replaces any code already emailed.
set -eu
email="${1:?usage: supabase/scripts/sign-in-code.sh you@example.com}"
# The native runtime prints status as JSON; the Docker runtime as a table.
key=$(supabase status 2>/dev/null | jq -r '.env.SECRET_KEY // empty' 2>/dev/null || true)
[ -n "$key" ] || key=$(supabase status -o env 2>/dev/null | sed -n 's/^SECRET_KEY="\(.*\)"$/\1/p')
[ -n "$key" ] || { echo "Local Supabase isn't running; start it first." >&2; exit 1; }
jq -n --arg email "$email" '{type: "magiclink", email: $email}' |
  curl -s http://127.0.0.1:55321/auth/v1/admin/generate_link \
    -H "apikey: $key" -H "Authorization: Bearer $key" -H "Content-Type: application/json" -d @- |
  jq -r '.email_otp // .msg // .'
