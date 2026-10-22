package com.tlw.photodokan.library

import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.library.LibraryLayout
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.EditRecipe
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json

/**
 * The photo library as folders on disk: {root}/{day folder}/{0001_Name}/ holding the original,
 * recipe.json, final.jpg and customer.json. Works the same on Windows and Android.
 */
class FilePhotoLibrary(
    private val root: Path,
    private val layout: LibraryLayout = LibraryLayout(),
    private val fs: FileSystem = SystemFileSystem,
) : PhotoLibrary {

    override fun nextNumber(date: StudioDate): Int {
        val dayDir = resolve(layout.dayFolder(date))
        return if (fs.exists(dayDir)) LibraryLayout.nextCustomerNumber(fs.list(dayDir).map { it.name }) else 1
    }

    override fun create(date: StudioDate, name: String?, phone: String?, original: ByteArray): Customer {
        fs.createDirectories(resolve(layout.dayFolder(date)))
        var number = nextNumber(date)
        while (true) {
            val customer = Customer(date, number, name?.trim()?.ifEmpty { null }, phone?.trim()?.ifEmpty { null })
            val dir = folderOf(customer)
            try {
                fs.createDirectories(dir, mustCreate = true)
            } catch (e: IOException) {
                if (fs.exists(dir)) { number++; continue } // taken a moment ago: use the next number
                throw e
            }
            write(Path(dir, originalFileName(original)), original)
            write(Path(dir, LibraryLayout.CUSTOMER_FILE), json.encodeToString(customer).encodeToByteArray())
            return customer
        }
    }

    override fun saveEdit(customer: Customer, recipe: EditRecipe, finalJpeg: ByteArray) {
        val dir = folderOf(customer)
        require(fs.exists(dir)) { "Customer ${customer.label} on ${customer.date} is not in the library" }
        write(Path(dir, LibraryLayout.RECIPE_FILE), json.encodeToString(recipe).encodeToByteArray())
        write(Path(dir, LibraryLayout.FINAL_FILE), finalJpeg)
    }

    override fun customersOn(date: StudioDate): List<Customer> {
        val dayDir = resolve(layout.dayFolder(date))
        if (!fs.exists(dayDir)) return emptyList()
        return fs.list(dayDir).mapNotNull(::readCustomer).sortedBy { it.number }
    }

    override fun search(query: String): List<Customer> {
        val depth = layout.dayPattern.split('/').size + 1
        return customerDirs(root, depth).mapNotNull(::readCustomer)
            .filter { it.matches(query) }
            .sortedWith(compareByDescending<Customer> { it.date }.thenByDescending { it.number })
    }

    override fun readOriginal(customer: Customer): ByteArray {
        val dir = folderOf(customer)
        val file = ORIGINAL_NAMES.map { Path(dir, it) }.firstOrNull(fs::exists)
            ?: throw IOException("No original photo for customer ${customer.label} on ${customer.date}")
        return read(file)
    }

    override fun readRecipe(customer: Customer): EditRecipe? =
        Path(folderOf(customer), LibraryLayout.RECIPE_FILE).takeIf(fs::exists)
            ?.let { runCatching { json.decodeFromString<EditRecipe>(read(it).decodeToString()) }.getOrNull() }

    override fun readFinal(customer: Customer): ByteArray? =
        Path(folderOf(customer), LibraryLayout.FINAL_FILE).takeIf(fs::exists)?.let(::read)

    private fun resolve(relative: String) = Path(root, *relative.split('/').toTypedArray())

    private fun folderOf(customer: Customer) = resolve(layout.customerFolder(customer))

    /** Folders that hold a customer.json, [depth] levels below [dir]. */
    private fun customerDirs(dir: Path, depth: Int): List<Path> {
        if (fs.metadataOrNull(dir)?.isDirectory != true) return emptyList()
        if (depth == 0) return listOf(dir)
        return fs.list(dir).flatMap { customerDirs(it, depth - 1) }
    }

    /** Folders without a readable customer.json (e.g. the studio's older folders) are skipped. */
    private fun readCustomer(dir: Path): Customer? {
        val file = Path(dir, LibraryLayout.CUSTOMER_FILE)
        if (!fs.exists(file)) return null
        return runCatching { json.decodeFromString<Customer>(read(file).decodeToString()) }.getOrNull()
    }

    private fun read(path: Path): ByteArray = fs.source(path).buffered().use { it.readByteArray() }

    /** Writes to a temporary file first, so a crash or power cut never leaves a half-written file. */
    private fun write(path: Path, bytes: ByteArray) {
        val temp = Path(path.parent!!, ".${path.name}.tmp")
        fs.sink(temp).buffered().use { it.write(bytes) }
        fs.atomicMove(temp, path)
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
