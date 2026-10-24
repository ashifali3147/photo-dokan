package com.tlw.photodokan.core.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoLibraryTest {

    private val date = StudioDate(2026, 10, 22)

    @Test
    fun defaultLayoutIsYearMonthDay() {
        assertEquals("2026/10/22", LibraryLayout().dayFolder(date))
    }

    @Test
    fun customPatternsMatchTheStudiosFolders() {
        assertEquals("2026/Oct/22-10-2026", LibraryLayout("{yyyy}/{MMM}/{dd}-{MM}-{yyyy}").dayFolder(date))
        assertEquals("2026/3/5", LibraryLayout("{yyyy}/{M}/{d}").dayFolder(StudioDate(2026, 3, 5)))
    }

    @Test
    fun customerFolderUsesDailyNumberAndName() {
        val layout = LibraryLayout()
        assertEquals("2026/10/22/0001", layout.customerFolder(Customer(date, 1)))
        assertEquals("2026/10/22/0042_Ramesh-Kumar", layout.customerFolder(Customer(date, 42, "  Ramesh  Kumar ")))
    }

    @Test
    fun phoneSheetsAreKeptInTheDayApartFromCustomers() {
        val layout = LibraryLayout()
        assertEquals("2026/10/24/Phone sheets", layout.sheetsFolder(StudioDate(2026, 10, 24)))
        assertEquals("Photo Dokan 0001 0002 14.30.pdf", layout.sheetFileName("Photo Dokan 0001 0002 14.30"))
        assertEquals("Photo Dokan 0001 0002 14.30 (2).pdf", layout.sheetFileName("Photo Dokan 0001 0002 14.30.pdf", copy = 2))
        assertEquals("a....b.pdf", layout.sheetFileName("../a/../..b"), "no slashes: no way out of the folder")
        assertEquals("Sheet.pdf", layout.sheetFileName("::"))
        assertEquals(1, LibraryLayout.nextCustomerNumber(listOf(LibraryLayout.SHEETS_FOLDER)), "not taken for a customer")
    }

    @Test
    fun namesAreSafeForWindows() {
        assertEquals("ab-c", LibraryLayout.sanitize("a<b>: c?*"))
        assertEquals("", LibraryLayout.sanitize("///"))
    }

    @Test
    fun nextNumberFollowsTheHighestExisting() {
        assertEquals(1, LibraryLayout.nextCustomerNumber(emptyList()))
        assertEquals(43, LibraryLayout.nextCustomerNumber(listOf("0001", "0042_Ramesh", "notes", "0007")))
    }

    @Test
    fun rejectsImpossibleDates() {
        assertFailsWith<IllegalArgumentException> { StudioDate(2026, 2, 29) }
        StudioDate(2028, 2, 29)
    }
}

class CustomerSearchTest {

    private val ramesh = Customer(StudioDate(2026, 10, 22), 7, "Ramesh Kumar", "+91 98765 43210")
    private val anonymous = Customer(StudioDate(2026, 10, 22), 12)

    @Test
    fun numberMatchesWithOrWithoutLeadingZeros() {
        assertTrue(ramesh.matches("7"))
        assertTrue(ramesh.matches("0007"))
        assertFalse(ramesh.matches("8"))
    }

    @Test
    fun fourOrMoreDigitsAlsoMatchPartOfThePhoneIgnoringSpaces() {
        assertTrue(ramesh.matches("3210"))
        assertTrue(ramesh.matches("6543"))
        assertFalse(ramesh.matches("654"))
        assertTrue(ramesh.matches("9876543210"))
        assertFalse(anonymous.matches("6543"))
    }

    @Test
    fun textMatchesPartOfTheNameIgnoringCase() {
        assertTrue(ramesh.matches("ramesh"))
        assertTrue(ramesh.matches(" KUMAR "))
        assertFalse(ramesh.matches("suresh"))
        assertFalse(anonymous.matches("ramesh"))
    }

    @Test
    fun blankQueryMatchesEveryone() {
        assertTrue(anonymous.matches(""))
        assertTrue(anonymous.matches("   "))
    }

    @Test
    fun labelIsFourDigits() {
        assertEquals("0007", ramesh.label)
    }
}

class StudioDateTest {

    @Test
    fun ordersByYearThenMonthThenDay() {
        val dates = listOf(StudioDate(2026, 10, 22), StudioDate(2025, 12, 31), StudioDate(2026, 1, 5), StudioDate(2026, 10, 3))
        assertEquals(
            listOf(StudioDate(2025, 12, 31), StudioDate(2026, 1, 5), StudioDate(2026, 10, 3), StudioDate(2026, 10, 22)),
            dates.sorted(),
        )
    }

    @Test
    fun stepsAcrossMonthsYearsAndLeapDays() {
        assertEquals(StudioDate(2026, 10, 23), StudioDate(2026, 10, 22).next())
        assertEquals(StudioDate(2026, 11, 1), StudioDate(2026, 10, 31).next())
        assertEquals(StudioDate(2027, 1, 1), StudioDate(2026, 12, 31).next())
        assertEquals(StudioDate(2028, 2, 29), StudioDate(2028, 2, 28).next())
        assertEquals(StudioDate(2026, 2, 28), StudioDate(2026, 3, 1).previous())
        assertEquals(StudioDate(2025, 12, 31), StudioDate(2026, 1, 1).previous())
        var d = StudioDate(2026, 1, 1)
        repeat(365) { d = d.next() }
        assertEquals(StudioDate(2027, 1, 1), d)
    }

    @Test
    fun printsTheWayTheStudioWritesDates() {
        assertEquals("05/03/2026", StudioDate(2026, 3, 5).toString())
    }
}
