# TASK-U1 QA Review — Money/date form correctness

**Date:** 2026-07-28  
**Verdict:** PASS WITH NOTES

All four acceptance criteria are satisfied. No production-code change was required.

## Scope reviewed

- `TransactionFormViewModel`, `TransactionFormScreen`, and localized transaction/split validators
- `BudgetExpenseForm`, `BudgetsViewModel`, and `BudgetDetailViewModel`
- `AccountFormViewModel` and `AccountFormScreen`
- `DatePickerDates` and date-picker wiring in transaction and API-session screens
- `TransactionFormViewModelTest`, `TransactionSplitValidationTest`,
  `DatePickerDatesTest`, `BudgetExpenseFormTest`, and `AccountFormViewModelTest`

## Acceptance evidence

| Criterion | Result | Evidence |
|---|---|---|
| Reject Infinity, NaN, zero, and negative values where positive money is required | PASS | Transaction amounts and split amounts use production `positiveMoneyOrNull`, which requires successful parsing, finiteness, and `> 0`; this gates field errors, `isValid`, `canSubmit`, split validation, and split conversion. `TransactionFormViewModelTest` rejects all four values. Budget expense validation independently requires a finite value `> 0`, and `BudgetExpenseFormTest` rejects all four. Budget income deliberately allows zero but rejects negative/non-finite values because that field is defined as non-negative. Account opening balance deliberately allows any finite signed value, including zero, and rejects non-finite input before repository submission. |
| Reject transfer with identical source/destination | PASS | `TransactionFormUiState.isValid` requires unequal account IDs for `TRANSFER`; `canSubmit` and `submit` use that result. The screen also reports the same-account field error. `transfer between the same account is invalid` exercises the production state properties. |
| Preserve selected calendar day in UTC−8 | PASS | `DatePickerDates` converts both directions with `ZoneOffset.UTC`. `TransactionFormScreen` uses these helpers for initial and confirmed picker values; `ApiSessionsScreen` uses the same path. `DatePickerDatesTest` changes the JVM default timezone to GMT−08:00 and verifies both selected-day conversion and initial UTC-midnight conversion. |
| Split tests exercise production paths | PASS | `TransactionSplitValidationTest` calls the production `TransactionFormUiState.splitValidationIssue()` for matching totals, mismatched totals, and non-finite line amounts, and calls production `toDomainSplits()` for blank-line conversion. The same validator directly gates `canSubmit` and supplies the screen error. |

## Verification

Focused command:

```text
./gradlew testDebugUnitTest --no-daemon \
  --tests 'com.pledgerio.app.ui.transactions.TransactionFormViewModelTest' \
  --tests 'com.pledgerio.app.ui.transactions.TransactionSplitValidationTest' \
  --tests 'com.pledgerio.app.ui.util.DatePickerDatesTest' \
  --tests 'com.pledgerio.app.ui.budgets.BudgetExpenseFormTest' \
  --tests 'com.pledgerio.app.ui.accounts.AccountFormViewModelTest'
```

Result: `BUILD SUCCESSFUL`; 32 tests executed, 0 failures, 0 errors, 0 skipped.

Regression command:

```text
./gradlew testDebugUnitTest --no-daemon --rerun-tasks
```

Result: `BUILD SUCCESSFUL`; the complete debug JVM unit-test task executed successfully.

## Residual risks

- Money remains represented as `Double`, so binary rounding, currency scale, scientific notation, and extremely large finite values are not constrained. This does not violate TASK-U1 but remains a financial-data correctness risk; fixed-scale decimal validation would be stronger.
- The split regression suite covers `Infinity` directly but not `NaN`, zero, and negative split lines individually. All use the same tested production `positiveMoneyOrNull` path, so this is a low coverage gap rather than a functional defect.
- The UTC−8 test uses a fixed-offset timezone and directly tests the conversion helper. It does not run the Material date picker or a DST-observing zone such as `America/Los_Angeles`; screen wiring was verified statically because this VM cannot run an Android emulator.
- `AccountFormViewModelTest` directly covers `Infinity`, while rejection of `NaN` follows from the same `isFinite()` guard. Zero and negative opening balances are intentionally valid signed balances, not positive-money fields.
