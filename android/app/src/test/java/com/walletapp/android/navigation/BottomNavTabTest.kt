package com.walletapp.android.navigation

import com.walletapp.android.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomNavTabTest {

    @Test
    fun `accounts screens map to the Accounts tab`() {
        assertEquals(BottomNavTab.Accounts, Screen.AccountsList.toBottomNavTab())
        assertEquals(BottomNavTab.Accounts, Screen.AccountForm().toBottomNavTab())
    }

    @Test
    fun `transactions screens map to the Transactions tab`() {
        assertEquals(BottomNavTab.Transactions, Screen.TransactionsList.toBottomNavTab())
        assertEquals(BottomNavTab.Transactions, Screen.TransactionForm().toBottomNavTab())
    }

    @Test
    fun `categories screens map to the Categories tab`() {
        assertEquals(BottomNavTab.Categories, Screen.CategoriesList.toBottomNavTab())
        assertEquals(BottomNavTab.Categories, Screen.CategoryForm().toBottomNavTab())
    }

    @Test
    fun `login and register hide the bottom navigation bar`() {
        assertNull(Screen.Login.toBottomNavTab())
        assertNull(Screen.Register.toBottomNavTab())
    }
}
