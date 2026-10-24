package com.tlw.photodokan.library

import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.library.LibraryLayout
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.EditRecipe
import kotlinx.io.IOException
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json

/**
 * The photo library as folders: {root}/{day folder}/{0001_Name}/ holding the original,
 * recipe.json, final.jpg and customer.json. Works the same on Windows and Android.
 */
class FilePhotoLibrary(
    private val files: LibraryFiles,
    private val layout: LibraryLayout = LibraryLayout(),
) : PhotoLibrary {

    /** The library in a folder on disk. */
    constructor(root: Path, layout: LibraryLayout = LibraryLayout(), fs: FileSystem = SystemFileSystem) :
        this(SystemLibraryFiles(root, fs), layout)

    override fun nextNumber(date: StudioDate): Int =
        LibraryLayout.nextCustomerNumber(files.list(layout.dayFolder(date)))

    override fun create(date: StudioDate, name: String?, phone: String?, original: ByteArray): Customer {
        files.createDirectories(layout.dayFolder(date))
        var number = nextNumber(date)
        while (true) {
            val customer = Customer(date, number, name?.trim()?.ifEmpty { null }, phone?.trim()?.ifEmpty { null })
            val dir = layout.customerFolder(customer)
            if (!files.createDirectory(dir)) { number++; continue } // taken a moment ago: use the next number
            files.write("$dir/${originalFileName(original)}", original)
            files.write("$dir/${LibraryLayout.CUSTOMER_FILE}", json.encodeToString(customer).encodeToByteArray())
            return customer
        }
    }

    override fun saveEdit(customer: Customer, recipe: EditRecipe, finalJpeg: ByteArray) {
        val dir = layout.customerFolder(customer)
        require(files.isDirectory(dir)) { "Customer ${customer.label} on ${customer.date} is not in the library" }
        files.write("$dir/${LibraryLayout.RECIPE_FILE}", json.encodeToString(recipe).encodeToByteArray())
        files.write("$dir/${LibraryLayout.FINAL_FILE}", finalJpeg)
    }

    override fun customersOn(date: StudioDate): List<Customer> {
        val dayDir = layout.dayFolder(date)
        return files.list(dayDir).mapNotNull { readCustomer("$dayDir/$it") }.sortedBy { it.number }
    }

    override fun search(query: String): List<Customer> {
        val depth = layout.dayPattern.split('/').size + 1
        return customerDirs("", depth).mapNotNull(::readCustomer)
            .filter { it.matches(query) }
            .sortedWith(compareByDescending<Customer> { it.date }.thenByDescending { it.number })
    }

    override fun readOriginal(customer: Customer): ByteArray {
        val dir = layout.customerFolder(customer)
        val file = ORIGINAL_NAMES.map { "$dir/$it" }.firstOrNull(files::exists)
            ?: throw IOException("No original photo for customer ${customer.label} on ${customer.date}")
        return files.read(file)
    }

    override fun readRecipe(customer: Customer): EditRecipe? =
        "${layout.customerFolder(customer)}/${LibraryLayout.RECIPE_FILE}".takeIf(files::exists)
            ?.let { runCatching { json.decodeFromString<EditRecipe>(files.read(it).decodeToString()) }.getOrNull() }

    override fun readFinal(customer: Customer): ByteArray? =
        "${layout.customerFolder(customer)}/${LibraryLayout.FINAL_FILE}".takeIf(files::exists)?.let(files::read)

    /** Folders that may hold a customer.json, [depth] levels below [dir]. */
    private fun customerDirs(dir: String, depth: Int): List<String> {
        if (depth == 0) return listOf(dir)
        return files.list(dir).flatMap { customerDirs(if (dir.isEmpty()) it else "$dir/$it", depth - 1) }
    }

    /** Folders without a readable customer.json (e.g. the studio's older folders) are skipped. */
    private fun readCustomer(dir: String): Customer? {
        val file = "$dir/${LibraryLayout.CUSTOMER_FILE}"
        if (!files.exists(file)) return null
        return runCatching { json.decodeFromString<Customer>(files.read(file).decodeToString()) }.getOrNull()
    }

    companion object {
        private val ORIGINAL_NAMES = listOf(LibraryLayout.ORIGINAL_FILE, "original.png")

        /** Newer app versions may add fields; older ones must still open those files. */
        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

        /** Keeps the original's real format in its name: a PNG is not renamed to .jpg. */
        internal fun originalFileName(original: ByteArray): String =
            if (original.size >= 4 && original.copyOf(4).contentEquals(PNG_SIGNATURE)) "original.png" else LibraryLayout.ORIGINAL_FILE
    }
}
