package com.tlw.photodokan.core.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
