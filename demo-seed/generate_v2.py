#!/usr/bin/env python3
"""
Generates demo-seed v2 fixtures from v1.

v1 fixtures are uniformly perfect (every CV complete, confident, and dated at authoring
time), which makes the Library Quality report a wall of green and decays as the literal
dates age. v2 applies a deterministic per-index "persona profile" to every cvs.json
(identical across all 49 segment/language files, so translations stay in sync) and
rewrites all dates as relative tokens (@M-n@ = n months ago, @Y-n@ = n years ago)
resolved by DemoSeedService at seed time — the demo report is identical whenever the
account is created.

Target report (22 CVs/file): completeness 92, freshness 38, uniqueness 91,
AI confidence 82 → overall 79 ("Good — Freshness is dragging your score down"),
with every issue type represented.

Usage: python3 generate_v2.py   (run from demo-seed/; writes ./v2 from ./v1)
"""
import copy
import json
import sys
from pathlib import Path

V1 = Path("v1")
V2 = Path("v2")

# ---------------------------------------------------------------------------
# Persona profile — indexes into each 20-CV v1 file; clones 20/21 are appended.
# ---------------------------------------------------------------------------
FRESH = {0, 1, 2, 3, 4, 10}          # UP_TO_DATE   (latest evidence ~now-2mo)
AGING = {5, 6, 11, 15}               # REVIEW_SUGGESTED (~10 months)
STALE = {7, 8, 9, 12, 13, 16, 17, 18, 19, 20, 21}  # OUTDATED (~38 months)
UNKNOWN = {14}                       # bad parse — no dateable content at all

REMOVE_PHONE = {10, 14, 18}
REMOVE_EMAIL = {11, 14}
REMOVE_CAREER_START = {9, 14}
REMOVE_EDUCATION = {14, 16}
REMOVE_LANGUAGES = {7, 14, 19}
REMOVE_CERTIFICATIONS = {3, 14, 16, 17}
REMOVE_SALARY = {5, 12, 14}
REMOVE_LINKEDIN = {6, 13, 14, 16}
BAD_PARSE = {14}                     # also loses workExperience/keySkills/summary/clustering
LOW_CONFIDENCE = {15, 17, 19}        # clusterConfidenceScore -> 0.42

EMAIL_DUP_OF = 0                     # clone 20: same email as CV 0, different phone (old re-upload)
PHONE_DUP_OF = 1                     # clone 21: same phone as CV 1, different email


def rewrite_dates(cv, idx):
    """Anchor every date in the CV to its persona's freshness bucket via relative tokens."""
    if idx in UNKNOWN:
        return  # bad-parse persona: dateable sections are removed elsewhere

    if idx in FRESH:
        anchor, current_role = 2, True
    elif idx in AGING:
        anchor, current_role = 10, False
    else:
        anchor, current_role = 38, False

    roles = cv.get("workExperience") or []
    for i, role in enumerate(roles):  # fixtures are newest-first
        end = anchor + 30 * i
        start = anchor + 30 * (i + 1)
        role["from"] = f"@M-{start}@"
        role["to"] = "Present" if (i == 0 and current_role) else f"@M-{end}@"

    certs = cv.get("certifications") or []
    if idx in FRESH:
        # A current-role CV carries no parseable end date; a current-year certification
        # provides the "fresh" evidence. Without certifications, a recent job change does.
        if certs:
            for i, cert in enumerate(certs):
                cert["year"] = f"@Y-{0 if i == 0 else 1}@"
        elif roles:
            roles[0]["from"] = "@M-2@"
    else:
        cert_years_ago = 2 if idx in AGING else 4
        for cert in certs:
            cert["year"] = f"@Y-{cert_years_ago}@"

    for j, education in enumerate(cv.get("education") or []):
        education["year"] = f"@Y-{7 + 2 * j}@"


def degrade(cv, idx):
    """Apply the persona's completeness / confidence gaps."""
    contact = (cv.get("personalInformation") or {}).get("contact") or {}
    if idx in REMOVE_PHONE:
        contact.pop("phone", None)
    if idx in REMOVE_EMAIL:
        contact.pop("email", None)
    if idx in REMOVE_LINKEDIN:
        (contact.get("socialLinks") or {}).pop("linkedin", None)
    if idx in REMOVE_CAREER_START:
        cv.pop("careerStartYear", None)
    if idx in REMOVE_EDUCATION:
        cv.pop("education", None)
    if idx in REMOVE_LANGUAGES:
        (cv.get("skillsAndQualifications") or {}).pop("languages", None)
    if idx in REMOVE_CERTIFICATIONS:
        cv.pop("certifications", None)
    if idx in REMOVE_SALARY:
        cv.pop("salaryExpectation", None)
    if idx in BAD_PARSE:
        cv.pop("workExperience", None)
        cv.pop("keySkills", None)
        cv.pop("candidateProfileSummary", None)
        cv.pop("candidateClustering", None)
    if idx in LOW_CONFIDENCE and cv.get("candidateClustering"):
        cv["candidateClustering"]["clusterConfidenceScore"] = 0.42


def make_clones(cvs):
    """Two duplicate personas: an old re-upload (same email) and a same-phone twin."""
    email_dup = copy.deepcopy(cvs[EMAIL_DUP_OF])
    contact = email_dup["personalInformation"]["contact"]
    if contact.get("phone"):
        contact["phone"] = contact["phone"][:-2] + "99"  # different phone, same email

    phone_dup = copy.deepcopy(cvs[PHONE_DUP_OF])
    contact = phone_dup["personalInformation"]["contact"]
    if contact.get("email"):
        local, _, domain = contact["email"].partition("@")
        contact["email"] = f"{local}.personal@{domain}"  # different email, same phone

    return [email_dup, phone_dup]


def transform(cvs):
    if len(cvs) != 20:
        raise SystemExit(f"expected 20 CVs, found {len(cvs)}")
    cvs = copy.deepcopy(cvs) + make_clones(cvs)
    for idx, cv in enumerate(cvs):
        degrade(cv, idx)
        rewrite_dates(cv, idx)
    return cvs


# ---------------------------------------------------------------------------
# Predicted-report simulation (mirrors LibraryQualityService scoring)
# ---------------------------------------------------------------------------
def predict(cvs):
    total = len(cvs)
    contact = lambda cv: (cv.get("personalInformation") or {}).get("contact") or {}
    has = {
        "email": sum(1 for c in cvs if contact(c).get("email")),
        "phone": sum(1 for c in cvs if contact(c).get("phone")),
        "name": sum(1 for c in cvs if (c.get("personalInformation") or {}).get("name")),
        "role": sum(1 for c in cvs if (c.get("personalInformation") or {}).get("role")),
        "workExperience": sum(1 for c in cvs if c.get("workExperience")),
        "keySkills": sum(1 for c in cvs if c.get("keySkills")),
        "careerStartYear": sum(1 for c in cvs if c.get("careerStartYear") is not None),
        "education": sum(1 for c in cvs if c.get("education")),
        "languages": sum(1 for c in cvs if (c.get("skillsAndQualifications") or {}).get("languages")),
        "certifications": sum(1 for c in cvs if c.get("certifications")),
        "salaryExpectation": sum(1 for c in cvs if c.get("salaryExpectation")),
        "linkedin": sum(1 for c in cvs if (contact(c).get("socialLinks") or {}).get("linkedin")),
        "summary": sum(1 for c in cvs if c.get("candidateProfileSummary")),
    }
    pct = lambda n: round(n / total * 1000) / 10
    weights = dict.fromkeys(["email", "phone", "name", "role"], 3)
    weights.update(dict.fromkeys(["workExperience", "keySkills", "careerStartYear", "education"], 2))
    weights.update(dict.fromkeys(["languages", "certifications", "salaryExpectation", "linkedin", "summary"], 1))
    completeness = round(sum(w * pct(has[f]) for f, w in weights.items()) / sum(weights.values()))

    up, review, out, unk = len(FRESH), len(AGING), len(STALE), len(UNKNOWN)
    freshness = round((up + 0.5 * review) / (total - unk) * 100)
    uniqueness = round(100 * (total - 2) / total)
    conf_scores = [(c.get("candidateClustering") or {}).get("clusterConfidenceScore") for c in cvs]
    confident = sum(1 for s in conf_scores if s is not None and s >= 0.5)
    confidence = round(100 * confident / total)
    overall = round(0.4 * completeness + 0.2 * freshness + 0.2 * uniqueness + 0.2 * confidence)
    return {"completeness": completeness, "freshness": freshness, "uniqueness": uniqueness,
            "confidence": confidence, "overall": overall,
            "buckets": {"UP_TO_DATE": up, "REVIEW_SUGGESTED": review, "OUTDATED": out, "UNKNOWN": unk}}


def main():
    if not V1.is_dir():
        raise SystemExit("run from the demo-seed/ directory (v1/ not found)")

    prediction = None
    files = 0
    for src in sorted(V1.glob("*/*/cvs.json")):
        dst = V2 / src.relative_to(V1)
        dst.parent.mkdir(parents=True, exist_ok=True)
        cvs = transform(json.loads(src.read_text()))
        dst.write_text(json.dumps(cvs, ensure_ascii=False, indent=2) + "\n")
        job_posts = src.parent / "job-posts.json"
        if job_posts.exists():
            (dst.parent / "job-posts.json").write_text(job_posts.read_text())
        prediction = prediction or predict(cvs)
        files += 1

    print(f"transformed {files} cvs.json files (+ job-posts copied) into {V2}/")
    print("predicted Library Quality report per demo tenant:")
    print(json.dumps(prediction, indent=2))


if __name__ == "__main__":
    sys.exit(main())
