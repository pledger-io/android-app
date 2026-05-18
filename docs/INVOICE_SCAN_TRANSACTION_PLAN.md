# Invoice and bill scan to transaction: design plan

This document proposes an end-to-end flow for scanning invoices/bills, extracting text, and turning that into a prefilled transaction.

## 1) Goal

Allow users to:

1. Capture or import an invoice/bill image.
2. Extract readable text from it.
3. Reuse the existing backend API that converts text into transaction data.
4. Open the existing transaction form with prefilled values for user confirmation.
5. Save as a normal transaction via `POST /v2/api/transactions`.

This keeps final user control and avoids silent bookkeeping mistakes.

---

## 2) Product flow (user journey)

### Entry points

- Dashboard FAB menu: add a third action, **Scan invoice/bill**.
- Transaction list screen (optional): toolbar action for scan.

### Happy path

1. User taps **Scan invoice/bill**.
2. User chooses **Camera** or **Import from gallery/PDF**.
3. App performs OCR and shows progress.
4. App sends extracted text to the existing backend text-to-transaction endpoint.
5. App opens a **Review draft** step:
   - amount
   - date
   - merchant/payee
   - description
   - suggested type (expense/income/transfer)
   - currency
   - confidence and missing fields
6. User confirms or edits fields.
7. App opens `TransactionFormScreen` prefilled.
8. User taps **Create transaction** (existing behavior).

### Failure path

- If OCR fails: user can retry capture/import or switch to manual transaction.
- If extraction API fails: keep OCR text visible and allow manual entry with text copy support.
- If confidence is low: flag uncertain fields and require manual confirmation.

---

## 3) Architecture fit (Clean Architecture)

## UI layer

Add:

- `InvoiceScanScreen` (capture/import, progress, retry)
- `InvoiceScanReviewScreen` (draft review and corrections)
- Navigation route(s):
  - `transaction/scan`
  - `transaction/scan/review`

Reuse:

- Existing `TransactionFormScreen` for final creation.
- Existing account pickers/autocomplete for source/destination corrections.

## Domain layer

Add use cases:

- `ExtractTextFromDocumentUseCase` (OCR abstraction)
- `ExtractTransactionFromTextUseCase` (calls existing backend extraction API)
- `BuildTransactionDraftUseCase` (maps OCR + API output to form state)

Add models:

- `ScannedDocument` (uri/pages, optional image hash, source)
- `OcrResult` (fullText, line blocks, locale hint)
- `TransactionExtractionDraft` (prefill candidates + confidence per field)

## Data layer

Add repository interfaces + implementations:

- `DocumentTextExtractor`:
  - on-device OCR engine (ML Kit Text Recognition)
- `TransactionExtractionRepository`:
  - Retrofit call to existing backend text-extraction endpoint

Keep image handling local by default. Send text only unless backend image upload becomes mandatory.

---

## 4) API integration plan

The backend capability already exists: "extract transaction information from text".
The Android client should integrate it as a draft-generation step, not an auto-save step.

### Request payload (proposed)

Use extracted OCR text and optional context:

- `text` (required)
- `locale` (optional)
- `defaultCurrency` (optional)
- `timezone` (optional)
- `hints` (optional; e.g., expected type = expense)

### Response mapping (proposed)

Expected output fields mapped into existing transaction form state:

- amount
- currency
- date
- description
- merchant / counterparty name
- transaction type
- optional category/tag suggestions
- per-field confidence

### Retrofit addition

Add a new method to `PledgerApiService` for the existing extraction endpoint and wire it through `TransactionRepository` (or a dedicated extraction repository) so ViewModels stay thin.

---

## 5) OCR strategy

### Phase 1 choice: on-device OCR first

Use ML Kit text recognition locally:

- Better privacy (no image upload)
- Fast feedback
- Works with flaky connectivity (only extraction API call needs network)

### Preprocessing for quality

Before OCR:

- auto-crop document bounds (if available)
- perspective correction
- grayscale/contrast normalization
- rotate by EXIF + orientation detection

These steps significantly improve extraction quality on receipts.

---

## 6) Confidence and validation strategy

Never create transactions automatically from scan results.

Rules:

- Always require user confirmation before save.
- Highlight uncertain fields (confidence below threshold).
- If critical fields are missing (`amount`, `date`, or payee/account side), block one-tap continue and prompt completion.
- Keep raw OCR text expandable so user can verify.

Suggested thresholds:

- `>= 0.85`: prefill silently
- `0.60 - 0.84`: prefill with warning badge
- `< 0.60`: leave empty + ask user input

---

## 7) UX details

- Progress states:
  - "Preparing image"
  - "Reading text"
  - "Extracting transaction details"
- Show editable preview cards for key fields before entering full form.
- Keep "Manual entry instead" action visible at all times.
- Persist last scan draft in `SavedStateHandle` during process death to avoid user frustration.

---

## 8) Security and privacy

- Default mode: images remain on device; send text only to backend.
- Do not store raw document images in Room.
- If temporary files are created, delete them after review/save/cancel.
- Redact sensitive numbers in logs.
- Respect existing session auth pipeline (JWT via `AuthInterceptor`).

---

## 9) Telemetry and quality metrics

Track (locally or analytics backend if enabled):

- scan started/completed
- OCR success rate
- extraction API success rate
- percentage of drafts accepted with no edits
- average number of edited fields before save
- drop-off step (scan, extraction, review, create)

These metrics identify whether OCR or mapping is the bottleneck.

---

## 10) Test plan

### Unit tests

- `BuildTransactionDraftUseCase` mapping and confidence logic.
- Validation rules for required fields.
- Fallback behavior when extraction API errors.

### Integration tests

- ViewModel state progression (idle -> scanning -> extracted -> review -> form).
- Retry flow after OCR/API failure.

### UI tests

- End-to-end happy path with mocked OCR/API.
- Low-confidence highlighting and manual correction path.

---

## 11) Delivery phases

### Phase A (MVP)

- Scan/import single image.
- Local OCR.
- Call existing text extraction endpoint.
- Review screen.
- Prefill and handoff to current `TransactionFormScreen`.

### Phase B (quality)

- Better preprocessing/crop.
- Multi-page document support.
- Improved confidence display and field-level explanations.

### Phase C (advanced)

- Optional PDF import and page selection.
- Learning from user corrections (future backend support).
- Optional category/tag auto-apply when confidence is high.

---

## 12) Open decisions

1. Confirm backend endpoint path and DTO schema for text extraction in Android client.
2. Decide whether scan entry belongs only in dashboard FAB or also transaction list.
3. Confirm whether PDF scanning is MVP or Phase B.
4. Confirm privacy policy wording for OCR + text extraction processing.
