package com.pledgerio.app.ui.accounts

import com.pledgerio.app.domain.model.AccountTypeCatalog
import com.pledgerio.app.domain.model.AccountTypeOption

enum class AccountTypeFamily {
    CHECKING,
    SAVINGS,
}

/** One row in the add-account menu or the form type dropdown (checking/savings are merged). */
data class AccountTypePickerEntry(
    val label: AccountPickerLabel,
    val description: AccountPickerDescription,
    val iconTypeCode: String,
    val soloTypeCode: String,
    val jointTypeCode: String?,
    val family: AccountTypeFamily?,
)

data class AccountTypeVariantChoice(
    val family: AccountTypeFamily,
    val soloTypeCode: String,
    val jointTypeCode: String,
    val isJoint: Boolean,
)

object AccountTypePicker {

    private val checkingSoloCodes = setOf("default", "checking")
    private val checkingJointCodes = setOf("joined")
    private val savingsSoloCodes = setOf("savings", "saving")
    private val savingsJointCodes = setOf("joined_savings")

    fun ownedPickerEntries(ownedTypes: List<AccountTypeOption>): List<AccountTypePickerEntry> {
        if (ownedTypes.isEmpty()) return emptyList()

        val byCode = ownedTypes.associateBy { it.code.lowercase() }
        val consumed = mutableSetOf<String>()
        val entries = mutableListOf<AccountTypePickerEntry>()

        val checkingSolo = checkingSoloCodes.firstNotNullOfOrNull { byCode[it] }
        val checkingJoint = checkingJointCodes.firstNotNullOfOrNull { byCode[it] }
        if (checkingSolo != null || checkingJoint != null) {
            checkingSoloCodes.plus(checkingJointCodes).forEach { consumed.add(it) }
            val soloCode = checkingSolo?.code ?: checkingJoint!!.code
            entries.add(
                AccountTypePickerEntry(
                    label = AccountPickerLabel.Checking,
                    description = AccountPickerDescription.Checking,
                    iconTypeCode = soloCode,
                    soloTypeCode = soloCode,
                    jointTypeCode = checkingJoint?.code,
                    family = AccountTypeFamily.CHECKING,
                ),
            )
        }

        val savingsSolo = savingsSoloCodes.firstNotNullOfOrNull { byCode[it] }
        val savingsJoint = savingsJointCodes.firstNotNullOfOrNull { byCode[it] }
        if (savingsSolo != null || savingsJoint != null) {
            savingsSoloCodes.plus(savingsJointCodes).forEach { consumed.add(it) }
            val soloCode = savingsSolo?.code ?: savingsJoint!!.code
            entries.add(
                AccountTypePickerEntry(
                    label = AccountPickerLabel.Savings,
                    description = AccountPickerDescription.Savings,
                    iconTypeCode = soloCode,
                    soloTypeCode = soloCode,
                    jointTypeCode = savingsJoint?.code,
                    family = AccountTypeFamily.SAVINGS,
                ),
            )
        }

        ownedTypes
            .filter { it.code.lowercase() !in consumed }
            .forEach { option ->
                entries.add(
                    AccountTypePickerEntry(
                        label = AccountPickerLabel.Custom(option.displayName),
                        description = AccountPickerDescription.Custom(
                            option.description.ifBlank {
                                AccountTypeCatalog.metadataFor(option.code).description
                            },
                        ),
                        iconTypeCode = option.code,
                        soloTypeCode = option.code,
                        jointTypeCode = null,
                        family = null,
                    ),
                )
            }

        return entries
    }

    fun variantChoice(typeCode: String, ownedTypes: List<AccountTypeOption>): AccountTypeVariantChoice? {
        val key = typeCode.lowercase()
        val byCode = ownedTypes.associateBy { it.code.lowercase() }

        when {
            key in checkingSoloCodes || key in checkingJointCodes -> {
                val solo = checkingSoloCodes.firstNotNullOfOrNull { byCode[it] }?.code
                    ?: return null
                val joint = checkingJointCodes.firstNotNullOfOrNull { byCode[it] }?.code
                    ?: return null
                return AccountTypeVariantChoice(
                    family = AccountTypeFamily.CHECKING,
                    soloTypeCode = solo,
                    jointTypeCode = joint,
                    isJoint = key in checkingJointCodes,
                )
            }
            key in savingsSoloCodes || key in savingsJointCodes -> {
                val solo = savingsSoloCodes.firstNotNullOfOrNull { byCode[it] }?.code
                    ?: return null
                val joint = savingsJointCodes.firstNotNullOfOrNull { byCode[it] }?.code
                    ?: return null
                return AccountTypeVariantChoice(
                    family = AccountTypeFamily.SAVINGS,
                    soloTypeCode = solo,
                    jointTypeCode = joint,
                    isJoint = key in savingsJointCodes,
                )
            }
        }
        return null
    }

}
