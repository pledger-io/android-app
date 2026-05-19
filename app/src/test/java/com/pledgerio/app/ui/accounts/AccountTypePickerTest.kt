package com.pledgerio.app.ui.accounts

import com.pledgerio.app.domain.model.AccountTypeOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTypePickerTest {

  private fun ownedOptions(vararg codes: Pair<String, String>) = codes.map { (code, name) ->
    AccountTypeOption(code = code, displayName = name)
  }

  @Test
  fun `ownedPickerEntries merges checking and joined into one row`() {
    val entries = AccountTypePicker.ownedPickerEntries(
      ownedOptions("default" to "Checking", "joined" to "Joint checking", "cash" to "Cash"),
    )

    val checking = entries.single { it.label is AccountPickerLabel.Checking }
    assertEquals("default", checking.soloTypeCode)
    assertEquals("joined", checking.jointTypeCode)
    assertEquals(AccountTypeFamily.CHECKING, checking.family)
    assertEquals(2, entries.size)
    assertTrue(entries.any { (it.label as? AccountPickerLabel.Custom)?.value == "Cash" })
  }

  @Test
  fun `ownedPickerEntries merges savings variants into one row`() {
    val entries = AccountTypePicker.ownedPickerEntries(
      ownedOptions("savings" to "Savings", "joined_savings" to "Joint savings"),
    )

    val savings = entries.single { it.label is AccountPickerLabel.Savings }
    assertEquals("savings", savings.soloTypeCode)
    assertEquals("joined_savings", savings.jointTypeCode)
    assertEquals(AccountTypeFamily.SAVINGS, savings.family)
  }

  @Test
  fun `ownedPickerEntries keeps credit card as separate row`() {
    val entries = AccountTypePicker.ownedPickerEntries(
      ownedOptions("default" to "Checking", "credit_card" to "Credit card"),
    )

    assertEquals(2, entries.size)
    val card = entries.single { it.soloTypeCode == "credit_card" }
    assertTrue(card.label is AccountPickerLabel.Custom)
    assertEquals("Credit card", (card.label as AccountPickerLabel.Custom).value)
    assertNull(card.jointTypeCode)
    assertNull(card.family)
    assertEquals("credit_card", card.soloTypeCode)
  }

  @Test
  fun `ownedPickerEntries returns empty for empty input`() {
    assertTrue(AccountTypePicker.ownedPickerEntries(emptyList()).isEmpty())
  }

  @Test
  fun `variantChoice returns personal checking`() {
    val owned = ownedOptions("default" to "Checking", "joined" to "Joint")
    val variant = AccountTypePicker.variantChoice("default", owned)

    assertNotNull(variant)
    assertEquals(AccountTypeFamily.CHECKING, variant!!.family)
    assertEquals("default", variant.soloTypeCode)
    assertEquals("joined", variant.jointTypeCode)
    assertFalse(variant.isJoint)
  }

  @Test
  fun `variantChoice returns joint checking`() {
    val owned = ownedOptions("default" to "Checking", "joined" to "Joint")
    val variant = AccountTypePicker.variantChoice("joined", owned)

    assertNotNull(variant)
    assertTrue(variant!!.isJoint)
  }

  @Test
  fun `variantChoice returns null when joint type not on server`() {
    val owned = ownedOptions("default" to "Checking")
    assertNull(AccountTypePicker.variantChoice("default", owned))
  }

  @Test
  fun `variantChoice returns joint savings`() {
    val owned = ownedOptions("savings" to "Savings", "joined_savings" to "Joint savings")
    val variant = AccountTypePicker.variantChoice("joined_savings", owned)

    assertNotNull(variant)
    assertEquals(AccountTypeFamily.SAVINGS, variant!!.family)
    assertTrue(variant.isJoint)
  }

  @Test
  fun `variantChoice returns null for credit card`() {
    val owned = ownedOptions("credit_card" to "Card")
    assertNull(AccountTypePicker.variantChoice("credit_card", owned))
  }
}
