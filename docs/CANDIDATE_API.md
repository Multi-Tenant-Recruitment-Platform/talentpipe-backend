# Candidate API Specification

Base path: `/api/v1`

This document details all Candidate-related API endpoints, payload formats, authentication rules, lifecycle states, and identity validation specifications.

---

## 1. Endpoints Overview

| Method | Endpoint | Auth | Content-Type | Description |
|---|---|---|---|---|
| `POST` | `/public/candidates/register` | Public | `application/json` | Self-registration for candidates (creates `PENDING_VERIFICATION` account). |
| `GET` | `/candidates/me` | Bearer (`CANDIDATE`) | `application/json` | Fetches the current authenticated candidate's profile, talent pool status, and active CV metadata. |
| `PUT` | `/candidates/me` | Bearer (`CANDIDATE`) | `multipart/form-data` | Updates candidate profile attributes, photo, talent pool opt-in/opt-out, and uploads a new CV. |
| `GET` | `/candidates/me/cv/download` | Bearer (`CANDIDATE`) | Binary / Octet-stream | Downloads the candidate's active CV file attachment. |
| `GET` | `/public/media/candidate/{candidateId}/photos/{filename}` | Public | Image stream | Publicly serves candidate profile photos (PNG, JPEG, WebP). |

---

## 2. Sri Lankan National Identity Card (NIC) Validation Specification

Candidate identity numbers (`identityCardNumber`) are strictly validated and normalized at registration (`POST /public/candidates/register`) and profile updates (`PUT /candidates/me`).

### Step 0: Input Pre-Processing & Normalization
Before evaluating validation rules:
- **Strip Formatting:** Remove all whitespaces, tabs, and hyphens (`[ \t-]+`). E.g., `95123-4567-V` or `1995 1234 5678` is compacted.
- **Case Standardization:** Convert letters to uppercase (e.g., trailing `v` or `x` $\rightarrow$ `V` or `X`).

### Step 1: Structural & Format Validation (Regex)
The cleaned identifier must strictly match one of two historical patterns. If it matches neither, the system rejects it immediately with `"Format Invalid"`:
- **Pattern A (Old Format - 10 characters):**
  - Exactly 9 numeric digits followed by the letter `V` or `X`.
  - Regex: `^[0-9]{9}[VX]$`
- **Pattern B (New Format - 12 characters):**
  - Exactly 12 numeric digits (no letters allowed).
  - Regex: `^[0-9]{12}$`

### Step 2: Logical Range Validation (Gender-Agnostic)
To verify that the ID represents a valid calendar day without inferring or storing gender:
- **Isolate the 3-digit day sequence:**
  - **10-character format:** Extract 3rd, 4th, and 5th digits (index `2..5`, e.g., in `722441524V` $\rightarrow$ `244`).
  - **12-character format:** Extract 5th, 6th, and 7th digits (index `4..7`, e.g., in `197224401524` $\rightarrow$ `244`).
- **Mathematical Brackets:**
  - **Bracket 1:** `001` to `366` (inclusive, male calendar day or leap year day).
  - **Bracket 2:** `501` to `866` (inclusive, female calendar day offset by +500).
- **Rejection Criteria:**
  - `000`
  - `367` to `500`
  - `867` or higher
  - Any number outside these brackets is physically impossible and rejected with an explicit validation error.

---

## 3. Endpoints Detail

### 3.1 Candidate Self-Registration
- **Method:** `POST`
- **URL:** `/api/v1/public/candidates/register`
- **Auth:** Public

#### Request Payload (`application/json`)
```json
{
  "fullName": "Kasun Perera",
  "identityCardNumber": "941234567V",
  "address": "123 Galle Road, Colombo 03",
  "contactNumber": "+94 77 123 4567",
  "email": "kasun.perera@example.com",
  "password": "SecurePassword123!"
}
```

#### Field Constraints
- `fullName`: Required, max 100 characters.
- `identityCardNumber`: Required, validated via Sri Lankan NIC rules (old 10-char or new 12-char format).
- `address`: Required, max 255 characters.
- `contactNumber`: Required, max 30 characters.
- `email`: Required, valid email format, globally unique.
- `password`: Required, 8–100 characters.

#### Responses
- **`201 Created`**: Returns registered candidate summary with `status: "PENDING_VERIFICATION"`.
- **`400 Bad Request`**: Validation failed (e.g. invalid NIC format, missing required field).
- **`409 Conflict`**: Email already registered.

---

### 3.2 Get Current Candidate Profile
- **Method:** `GET`
- **URL:** `/api/v1/candidates/me`
- **Auth:** Bearer Token (`CANDIDATE` role)

#### Response (`200 OK`)
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "fullName": "Kasun Perera",
  "email": "kasun.perera@example.com",
  "identityCardNumber": "941234567V",
  "address": "123 Galle Road, Colombo 03",
  "phone": "+94 77 123 4567",
  "photoUrl": "/api/v1/public/media/candidate/3fa85f64-5717-4562-b3fc-2c963f66afa6/photos/avatar.jpg",
  "inTalentPool": true,
  "cv": {
    "id": "7ca64703-a17f-442f-87d2-7c5053ae2921",
    "fileName": "Kasun_Perera_Resume.pdf",
    "downloadUrl": "/api/v1/candidates/me/cv/download",
    "contentType": "application/pdf",
    "sizeBytes": 245120,
    "version": 1,
    "createdAt": "2026-10-14T10:15:30Z"
  }
}
```

#### Error Responses
- **`401 Unauthorized`**: Missing or invalid Bearer token.
- **`403 Forbidden`**: Candidate account is not verified (`PENDING_VERIFICATION`).
- **`404 Not Found`**: Candidate profile not found.

---

### 3.3 Update Candidate Profile & CV
- **Method:** `PUT`
- **URL:** `/api/v1/candidates/me`
- **Auth:** Bearer Token (`CANDIDATE` role)
- **Content-Type:** `multipart/form-data`

#### Multipart Form Fields
| Field Name | Type | Required | Description |
|---|---|---|---|
| `fullName` | Text | Yes | Candidate full name (HTML stripped). |
| `email` | Text | Yes | Candidate email (checks uniqueness if changed). |
| `identityCardNumber` | Text | Yes | Sri Lankan NIC (validates format and calendar day range). |
| `address` | Text | No | Residential address. |
| `phone` | Text | No | Contact phone number. |
| `addToTalentPool` | Boolean / String | No | `true` to opt into Talent Pool, `false` to opt out. |
| `removePhoto` | Boolean / String | No | `true` to delete existing profile photo. |
| `photo` | File | No | Profile photo image file (PNG, JPEG, WebP; max 2 MB). |
| `cv` | File | No | CV file (PDF or Word DOCX; max 5 MB; magic-byte verified). |

#### Talent Pool & CV Lifecycle Rules
1. **CV Upload Requirement:** A CV file can only be uploaded if `addToTalentPool` is `true` or candidate is already in the talent pool.
2. **Opt-Out Deactivation:** If `addToTalentPool` is toggled to `false`, any active CV is automatically deactivated, and a `LEFT_TALENT_POOL` audit record is stored.
3. **Deduplication:** Uploading an identical CV (matching SHA-256 hash) re-activates the existing record without duplicating storage files.
4. **Versioning:** Each distinct uploaded CV increments the version counter (`version 1`, `version 2`, etc.).

#### Responses
- **`200 OK`**: Returns updated profile matching `MyCandidateProfileResponse`.
- **`400 Bad Request`**: Validation error (invalid NIC, disallowed file type, spoofed extension, photo too large, etc.).
- **`409 Conflict`**: Email already registered to another user or candidate.

---

### 3.4 Download Active CV
- **Method:** `GET`
- **URL:** `/api/v1/candidates/me/cv/download`
- **Auth:** Bearer Token (`CANDIDATE` role)

#### Response
- **`200 OK`**: Binary stream with header `Content-Disposition: attachment; filename="<original_filename>"`.
- **`404 Not Found`**: Candidate has no active CV uploaded.

---

### 3.5 Stream Profile Photo
- **Method:** `GET`
- **URL:** `/api/v1/public/media/candidate/{candidateId}/photos/{filename}`
- **Auth:** Public

#### Response
- **`200 OK`**: Image binary stream (`image/png`, `image/jpeg`, or `image/webp`).
- **`404 Not Found`**: File or candidate photo does not exist.
