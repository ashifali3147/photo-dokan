package com.tlw.photodokan.core.library

import kotlinx.serialization.Serializable

@Serializable
data class StudioDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(year in 2000..2999) { "Year out of range: $year" }
        require(month in 1..12) { "Month must be 1..12, was $month" }
        require(day in 1..daysIn(year, month)) { "Day $day is not valid for $year-$month" }
    }

    companion object {
        fun daysIn(year: Int, month: Int): Int = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
}

/** A customer is numbered per day (0001, 0002 …); name and phone are optional. */
@Serializable
data class Customer(
    val date: StudioDate,
    val number: Int,
    val name: String? = null,
    val phone: String? = null,
) {
    init {
        require(number in 1..9999) { "Customer number must be 1..9999, was $number" }
    }
}

/**
 * Builds library folder paths, relative to the library root, using "/" as the separator.
 * The day-folder pattern is configurable so it can match the studio's existing folders.
 *
 * Tokens: {yyyy} year, {MM} two-digit month, {M} month, {MMM} short month name,
 * {dd} two-digit day, {d} day.
 */
class LibraryLayout(val dayPattern: String = DEFAULT_PATTERN) {

    fun dayFolder(date: StudioDate): String =
        dayPattern
            .replace("{yyyy}", date.year.toString())
            .replace("{MMM}", MONTHS[date.month - 1])
            .replace("{MM}", date.month.toString().padStart(2, '0'))
            .replace("{M}", date.month.toString())
            .replace("{dd}", date.day.toString().padStart(2, '0'))
            .replace("{d}", date.day.toString())

    fun customerFolder(customer: Customer): String {
        val number = customer.number.toString().padStart(4, '0')
        val name = customer.name?.let(::sanitize)?.takeIf { it.isNotEmpty() }
        return dayFolder(customer.date) + "/" + if (name == null) number else "${number}_$name"
    }

    companion object {
        const val DEFAULT_PATTERN = "{yyyy}/{MM}/{dd}"
        const val ORIGINAL_FILE = "original.jpg"
        const val RECIPE_FILE = "recipe.json"
        const val FINAL_FILE = "final.jpg"

        private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        private const val MAX_NAME_LENGTH = 40

        /** Keeps a name safe for Windows and Android file systems. */
        fun sanitize(name: String): String =
            name.trim()
                .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '.' }
                .replace(Regex("\\s+"), "-")
                .trim('.', '-')
                .take(MAX_NAME_LENGTH)

        /** Next daily number, given the customer folder names already in a day folder. */
        fun nextCustomerNumber(existingFolderNames: List<String>): Int {
            val highest = existingFolderNames
                .mapNotNull { Regex("^(\\d{4})(?:_.*)?$").find(it)?.groupValues?.get(1)?.toInt() }
                .maxOrNull() ?: 0
            return highest + 1
        }
    }
}
