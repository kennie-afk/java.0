#!/usr/bin/env bash
# Recreates the Lakeview Community Hospital demo through the API. Needs the stack up (API on :8100)
# and an empty database; phase 1 refuses to run twice because the organisation slug is taken.
set -euo pipefail
cd "$(dirname "$0")"
export HMS_SEED_STATE="${HMS_SEED_STATE:-/tmp/hms_seed_state.json}"
python3 phase1_foundation.py
python3 phase2_visits.py
python3 phase4_polish.py
python3 phase5_mch.py
python3 phase3_check.py
