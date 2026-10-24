package com.tlw.photodokan.ui.customers

import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.library.LibraryLayout
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.EditRecipe

/** Test double for screens: the real folder library has its own tests. */
class InMemoryPhotoLibrary : PhotoLibrary {
    val originals = mutableMapOf<Customer, ByteArray>()
    val recipes = mutableMapOf<Customer, EditRecipe>()
    val finals = mutableMapOf<Customer, ByteArray>()

    override fun nextNumber(date: StudioDate) = (originals.keys.filter { it.date == date }.maxOfOrNull { it.number } ?: 0) + 1

    override fun create(date: StudioDate, name: String?, phone: String?, original: ByteArray): Customer {
        val number = nextNumber(date)
        return Customer(date, number, name?.trim()?.ifEmpty { null }, phone?.trim()?.ifEmpty { null }).also { originals[it] = original }
    }

    override fun saveEdit(customer: Customer, recipe: EditRecipe, finalJpeg: ByteArray) {
        recipes[customer] = recipe
        finals[customer] = finalJpeg
    }

    override fun customersOn(date: StudioDate) = originals.keys.filter { it.date == date }.sortedBy { it.number }

    override fun search(query: String) =
        originals.keys.filter { it.matches(query) }.sortedWith(compareByDescending<Customer> { it.date }.thenByDescending { it.number })

    override fun readOriginal(customer: Customer) = originals.getValue(customer)
    override fun readRecipe(customer: Customer) = recipes[customer]
    override fun readFinal(customer: Customer) = finals[customer]

    /** Filed sheet PDFs by path, as the real library would name them. */
    val sheets = linkedMapOf<String, ByteArray>()

    override fun fileSheet(date: StudioDate, fileName: String, pdf: ByteArray): String {
        val layout = LibraryLayout()
        val path = generateSequence(1) { it + 1 }.map { "${layout.sheetsFolder(date)}/${layout.sheetFileName(fileName, it)}" }.first { it !in sheets }
        sheets[path] = pdf
        return path
    }
}
