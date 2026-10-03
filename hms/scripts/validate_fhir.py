#!/usr/bin/env python3
"""Runs the live FHIR output through the HL7 validator.

Fetches every Patient, then each patient's Encounter, Observation (vital signs and laboratory), MedicationRequest and AllergyIntolerance,
saves the API's own responses, and validates them against FHIR R4 (4.0.1). Needs a running API with seeded data, Docker, and the validator jar:

    curl -L -o validator_cli.jar https://github.com/hapifhir/org.hl7.fhir.core/releases/latest/download/validator_cli.jar
    HMS_EMAIL=admin@lakeview.test HMS_PASSWORD=... VALIDATOR_JAR=validator_cli.jar scripts/validate_fhir.py

Terminology is not checked (-tx n/a): there is no terminology server here, so LOINC and UCUM codes are not looked up. The first run downloads the
FHIR core package, which takes a few minutes; the cache folder keeps it for the next run. Exit status is 1 if the validator reports any error.
"""
import json, os, pathlib, subprocess, sys, tempfile, urllib.request

API = os.environ.get("HMS_API_URL", "http://localhost:8100")
JAR = pathlib.Path(os.environ.get("VALIDATOR_JAR", "validator_cli.jar")).resolve()
CACHE = pathlib.Path(os.environ.get("VALIDATOR_CACHE", pathlib.Path.home() / ".cache" / "hms-fhir")).resolve()


def call(path, body=None, token=None):
    req = urllib.request.Request(API + path, data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Content-Type": "application/json", **({"Authorization": "Bearer " + token} if token else {})})
    return json.load(urllib.request.urlopen(req))


def main():
    if not JAR.exists():
        sys.exit(f"Validator jar not found at {JAR}. See the download line at the top of this file.")
    token = call("/v1/auth/login", {"email": os.environ["HMS_EMAIL"], "password": os.environ["HMS_PASSWORD"]})["token"]
    work = pathlib.Path(tempfile.mkdtemp(prefix="hms-fhir-"))
    CACHE.mkdir(parents=True, exist_ok=True)
    patients = call("/fhir/r4/Patient?_count=100", token=token)
    (work / "Patient.json").write_text(json.dumps(patients))
    first = {}
    for entry in patients.get("entry", []):
        pid = entry["resource"]["id"]
        for name, query in [("Encounter", "Encounter?patient=%s"), ("Observation-vitals", "Observation?patient=%s&category=vital-signs"),
                            ("Observation-lab", "Observation?patient=%s&category=laboratory"), ("MedicationRequest", "MedicationRequest?patient=%s"),
                            ("AllergyIntolerance", "AllergyIntolerance?patient=%s")]:
            bundle = call("/fhir/r4/" + query % pid, token=token)
            if bundle.get("entry"):
                (work / f"{name}-{pid[:8]}.json").write_text(json.dumps(bundle))
                first.setdefault(name, bundle["entry"][0]["resource"])
    # One single-resource read of each kind, as the read endpoint returns it.
    for name, resource in first.items():
        (work / f"read-{name}.json").write_text(json.dumps(call(f"/fhir/r4/{resource['resourceType']}/{resource['id']}", token=token)))
    files = sorted(p.name for p in work.glob("*.json"))
    print(f"Validating {len(files)} responses saved in {work}")
    out = subprocess.run(["docker", "run", "--rm", "--network", "host", "-v", f"{work}:/w", "-v", f"{JAR}:/validator_cli.jar:ro", "-v", f"{CACHE}:/root/.fhir", "-w", "/w",
                          "eclipse-temurin:21-jre", "java", "-Xmx2g", "-jar", "/validator_cli.jar", *files, "-version", "4.0.1", "-tx", "n/a"], capture_output=True, text=True)
    (work / "validator.log").write_text(out.stdout + out.stderr)
    failed = out.stdout.count("*FAILURE*")
    print(f"{out.stdout.count('Success:')} responses with no errors, {failed} with errors. Full report: {work / 'validator.log'}")
    for line in out.stdout.splitlines():
        if line.strip().startswith("Error"):
            print(line.strip()[:300])
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
