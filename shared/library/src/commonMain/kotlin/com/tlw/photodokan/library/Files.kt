package com.tlw.photodokan.library

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readByteArray

internal fun FileSystem.readBytes(path: Path): ByteArray = source(path).buffered().use { it.readByteArray() }

/** Writes to a temporary file first, so a crash or power cut never leaves a half-written file. */
internal fun FileSystem.writeAtomically(path: Path, bytes: ByteArray) {
    val temp = Path(path.parent!!, ".${path.name}.tmp")
    sink(temp).buffered().use { it.write(bytes) }
    atomicMove(temp, path)
}
