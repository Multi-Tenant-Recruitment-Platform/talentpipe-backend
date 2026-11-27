import pytest

from app.safety.pii_redactor import CvPiiRedactor, RegexRule


def test_redactor_masks_contact_identity_while_keeping_university_and_degree():
    redactor = CvPiiRedactor()
    cv_text = (
        "Kasun Silva, University of Moratuwa\n"
        "BSc in Computer Science\n"
        "Email: kasun@uom.lk\n"
        "5 years Python & Java"
    )

    result = redactor.redact(cv_text)

    assert result == (
        "[NAME_REDACTED], University of Moratuwa\n"
        "BSc in Computer Science\n"
        "Email: [EMAIL_REDACTED]\n"
        "5 years Python & Java"
    )


@pytest.mark.parametrize(
    ("raw_text", "expected_fragment"),
    [
        ("Contact: kasun.perera+jobs@uom.lk for details", "[EMAIL_REDACTED]"),
        ("Portfolio: https://linkedin.com/in/kasun-silva", "[URL_REDACTED]"),
        ("Profile: linkedin.com/in/kasun-silva", "[URL_REDACTED]"),
        ("GitHub: github.com/kasun-dev/projects", "[URL_REDACTED]"),
        ("Code: www.github.com/kasun-dev/projects", "[URL_REDACTED]"),
        ("Mobile: +94 77 123 4567", "[PHONE_REDACTED]"),
        ("Phone: 077-123-4567", "[PHONE_REDACTED]"),
        ("US Tel: +1 (415) 555-2671", "[PHONE_REDACTED]"),
        ("NIC: 981234567V", "[NIC_REDACTED]"),
        ("New NIC: 199812345678", "[NIC_REDACTED]"),
        ("Gender: Female | Pronouns: she/her", "[GENDER_REDACTED]"),
        ("Mr. Kasun Perera applied", "[GENDER_REDACTED]"),
    ],
)
def test_redactor_masks_privacy_pii_regardless_of_blind_mode(raw_text: str, expected_fragment: str):
    redactor = CvPiiRedactor()

    assert expected_fragment in redactor.redact(raw_text, blind_mode=True)
    assert expected_fragment in redactor.redact(raw_text, blind_mode=False)


@pytest.mark.parametrize(
    "degree_and_university",
    [
        "BSc Computer Science, University of Colombo",
        "MSc Software Engineering\nMassachusetts Institute of Technology",
        "B.Sc. in Information Technology, SLIIT Malabe",
        "Master of Engineering, UCSC",
    ],
)
def test_redactor_preserves_degree_and_university_details_for_llm(degree_and_university: str):
    redactor = CvPiiRedactor()

    assert redactor.redact(degree_and_university, blind_mode=True) == degree_and_university
    assert redactor.redact(degree_and_university, blind_mode=False) == degree_and_university


def test_redactor_preserves_dates_ports_and_non_pii_words():
    redactor = CvPiiRedactor()
    cv_text = (
        "Senior Java Engineer\n"
        "Backend Engineer (2020 - 2024)\n"
        "Worked 01/2021 - 05/2024 in a two-man agile team.\n"
        "Built email integration and REST APIs on port 8000."
    )

    result = redactor.redact(cv_text)

    assert "Senior Java Engineer" in result
    assert "(2020 - 2024)" in result
    assert "01/2021 - 05/2024" in result
    assert "two-man" in result
    assert "email integration" in result
    assert "8000" in result


def test_name_header_rule_skips_headings_and_redacts_name_line_once():
    redactor = CvPiiRedactor()
    cv_text = (
        "CURRICULUM VITAE\n"
        "NUWAN PERERA - Senior Java Engineer\n"
        "Profile Summary\n"
        "6 years Spring Boot and PostgreSQL."
    )

    result = redactor.redact(cv_text)

    assert "CURRICULUM VITAE" in result
    assert "[NAME_REDACTED] - Senior Java Engineer" in result
    assert "Profile Summary" in result
    assert "NUWAN PERERA" not in result


def test_redactor_allows_extending_with_custom_rule():
    custom_rule = RegexRule.from_pattern(r"\bEMP-\d{4}\b", "[EMP_ID_REDACTED]")
    redactor = CvPiiRedactor(extra_rules=(custom_rule,))

    result = redactor.redact("Employee EMP-4021 Email: dev@example.com")

    assert "[EMP_ID_REDACTED]" in result
    assert "[EMAIL_REDACTED]" in result


def test_name_header_rule_masks_name_following_redacted_honorific():
    redactor = CvPiiRedactor()

    result = redactor.redact("\n\nMr. Kasun Silva\n6 years Python")

    assert result == "\n\n[GENDER_REDACTED] [NAME_REDACTED]\n6 years Python"
