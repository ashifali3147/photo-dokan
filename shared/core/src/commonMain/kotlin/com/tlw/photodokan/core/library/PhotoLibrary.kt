package com.tlw.photodokan.core.library

import com.tlw.photodokan.core.model.EditRecipe
import kotlinx.serialization.Serializable

@Serializable
data class StudioDate(val year: Int, val month: Int, val day: Int) : Comparable<StudioDate> {
    init {
        require(year in 2000..2999) { "Year out of range: $year" }
        require(month in 1..12) { "Month must be 1..12, was $month" }
        require(day in 1..daysIn(year, month)) { "Day $day is not valid for $year-$month" }
    }

    override fun compareTo(other: StudioDate): Int =
        compareValuesBy(this, other, StudioDate::year, StudioDate::month, StudioDate::day)

    fun previous(): StudioDate = when {
        day > 1 -> copy(day = day - 1)
        month > 1 -> StudioDate(year, month - 1, daysIn(year, month - 1))
        else -> StudioDate(year - 1, 12, 31)
    }

    fun next(): StudioDate = when {
        day < daysIn(year, month) -> copy(day = day + 1)
        month < 12 -> StudioDate(year, month + 1, 1)
        else -> StudioDate(year + 1, 1, 1)
    }

    /** As the studio writes it: 22/10/2026. */
    override fun toString(): String = "${day.pad2()}/${month.pad2()}/$year"

    companion object {
        private fun Int.pad2() = toString().padStart(2, '0')

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

    /** The number as printed and spoken in the studio: 0007. */
    val label: String get() = number.toString().padStart(4, '0')

    /**
     * Search used by the library: digits match the customer number, and from 4 digits on also part
     * of the phone number (so "8" doesn't match every phone with an 8). Anything else matches part
     * of the name, ignoring case. A blank query matches everyone.
     */
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (q.all { it.isDigit() }) {
            return q.toIntOrNull() == number || (q.length >= 4 && phone?.filter { it.isDigit() }?.contains(q) == true)
        }
        return name?.contains(q, ignoreCase = true) == true
    }
}

/**
 * Customers filed on disk as {root}/{day folder}/{0001_Name}/ with the untouched original, the
 * edit recipe, the final image and the customer's details. Implementations do the I/O.
 */
interface PhotoLibrary {
    /** The number the next new customer on [date] will get. Customers are known by number first. */
    fun nextNumber(date: StudioDate): Int

    /** Files a new customer under the next free number for [date], storing [original] exactly as given. */
    fun create(date: StudioDate, name: String?, phone: String?, original: ByteArray): Customer

    /** Saves the latest edit and its rendered result. The original is never touched. */
    fun saveEdit(customer: Customer, recipe: EditRecipe, finalJpeg: ByteArray)

    /** The day's customers in number order. */
    fun customersOn(date: StudioDate): List<Customer>

    /** Customers matching [Customer.matches] across all days, newest first. */
    fun search(query: String): List<Customer>

    fun readOriginal(customer: Customer): ByteArray
    fun readRecipe(customer: Customer): EditRecipe?
    fun readFinal(customer: Customer): ByteArray?

    /**
     * Keeps a printed sheet PDF that came from a phone, in [date]'s folder, as [fileName] (e.g.
     * "Photo Dokan 0001 0002 14.30.pdf") or with " (2)" added when that is taken. Returns the
     * path it was saved at, relative to the library.
     */
    fun fileSheet(date: StudioDate, fileName: String, pdf: ByteArray): String
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

    /** Where sheets printed from a phone are kept: inside the day, apart from its customers. */
    fun sheetsFolder(date: StudioDate): String = dayFolder(date) + "/" + SHEETS_FOLDER

    /**
     * [name] made safe as a file name on Windows and Android, keeping spaces and dots, ending in
     * ".pdf"; [copy] 2 and up add " (2)" and so on before it.
     */
    fun sheetFileName(name: String, copy: Int = 1): String {
        val base = name.removeSuffix(".pdf").filter { it.isLetterOrDigit() || it in " -._()" }.trim(' ', '.').take(MAX_SHEET_NAME_LENGTH).ifEmpty { "Sheet" }
        return if (copy <= 1) "$base.pdf" else "$base ($copy).pdf"
    }

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
        const val CUSTOMER_FILE = "customer.json"
        const val SHEETS_FOLDER = "Phone sheets"
        private const val MAX_SHEET_NAME_LENGTH = 80

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
