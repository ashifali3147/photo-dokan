package com.tlw.photodokan.core.model

/** Immutable undo/redo stack. Each change returns a new history. */
data class EditHistory<T>(
    val past: List<T>,
    val present: T,
    val future: List<T>,
    val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(limit > 0) { "History limit must be positive" }
    }

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun apply(next: T): EditHistory<T> {
        if (next == present) return this
        return copy(past = (past + present).takeLast(limit), present = next, future = emptyList())
    }

    fun undo(): EditHistory<T> {
        if (!canUndo) return this
        return copy(past = past.dropLast(1), present = past.last(), future = listOf(present) + future)
    }

    fun redo(): EditHistory<T> {
        if (!canRedo) return this
        return copy(past = past + present, present = future.first(), future = future.drop(1))
    }

    /** Back to [original], keeping the current state undoable. */
    fun reset(original: T): EditHistory<T> = apply(original)

    companion object {
        const val DEFAULT_LIMIT = 50

        fun <T> start(initial: T, limit: Int = DEFAULT_LIMIT): EditHistory<T> =
            EditHistory(emptyList(), initial, emptyList(), limit)
    }
}
