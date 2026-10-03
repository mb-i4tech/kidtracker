#!/usr/bin/env bash
set -euo pipefail
# Run on locally built, explicitly named images. No source/secrets scanning or uploads.
image=${1:?usage: scan-image.sh IMAGE OUTPUT_DIRECTORY}
out=${2:?output directory required}
mkdir -p "$out"
out=$(cd "$out" && pwd)
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  -v kidtracker-trivy-cache:/root/.cache/trivy -v "$out:/reports" \
  aquasec/trivy:0.69.3 image --scanners vuln --format json --output /reports/trivy.json "$image"
python3 - "$out/trivy.json" <<'PY'
import json,collections,sys
r=json.load(open(sys.argv[1]))
for result in r.get('Results',[]):
 print(result['Target'],dict(collections.Counter(v['Severity'] for v in result.get('Vulnerabilities',[]))))
PY
