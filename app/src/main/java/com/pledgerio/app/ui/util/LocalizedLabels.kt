package com.pledgerio.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.AppLocale
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.domain.model.ThemeMode
import com.pledgerio.app.ui.reports.ReportType

@Composable
fun FinanceExperienceMode.formBannerTitle(): String = stringResource(
    when (this) {
        FinanceExperienceMode.GUIDED -> R.string.transaction_form_guided_banner_title
        FinanceExperienceMode.POWER -> R.string.transaction_form_power_banner_title
    },
)

@Composable
fun FinanceExperienceMode.formBannerHint(): String = stringResource(
    when (this) {
        FinanceExperienceMode.GUIDED -> R.string.transaction_form_guided_banner_hint
        FinanceExperienceMode.POWER -> R.string.transaction_form_power_banner_hint
    },
)

@Composable
fun ThemeMode.localizedName(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
fun FinanceExperienceMode.localizedName(): String = stringResource(
    when (this) {
        FinanceExperienceMode.GUIDED -> R.string.experience_guided_name
        FinanceExperienceMode.POWER -> R.string.experience_power_name
    },
)

@Composable
fun FinanceExperienceMode.localizedDescription(): String = stringResource(
    when (this) {
        FinanceExperienceMode.GUIDED -> R.string.experience_guided_description
        FinanceExperienceMode.POWER -> R.string.experience_power_description
    },
)

@Composable
fun AppLocale.localizedName(): String = stringResource(
    when (this) {
        AppLocale.SYSTEM -> R.string.language_system
        AppLocale.ENGLISH -> R.string.language_english
        AppLocale.DUTCH -> R.string.language_dutch
        AppLocale.GERMAN -> R.string.language_german
    },
)

@Composable
fun ReportType.localizedTitle(): String = stringResource(
    when (this) {
        ReportType.INCOME_EXPENSE -> R.string.report_type_income_expense
        ReportType.CATEGORY -> R.string.report_type_category
        ReportType.BUDGET -> R.string.report_type_budget
        ReportType.NET_WORTH -> R.string.report_type_net_worth
        ReportType.BALANCE -> R.string.report_type_balance
    },
)
