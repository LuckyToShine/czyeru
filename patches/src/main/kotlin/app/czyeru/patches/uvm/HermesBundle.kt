package app.czyeru.patches.uvm

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** The bundle is not the one these patches were written for, or it is damaged. */
internal class BundleException(message: String) : Exception(message)

internal inline fun ensure(condition: Boolean, message: () -> String) {
    if (!condition) throw BundleException(message())
}

/** Value tags of the literal buffers. */
internal object Tag {
    const val NUMBER = 3
    const val LONG_STRING = 4
    const val SHORT_STRING = 5
    const val BYTE_STRING = 6
    const val INTEGER = 7
}

/** Where a string is stored. [length] counts characters, not bytes. */
internal class StringLocation(val utf16: Boolean, val offset: Int, val length: Int) {
    val byteLength get() = if (utf16) length * 2 else length
}

/** One value of a literal buffer. [position] is the offset of its payload in the file. */
internal class Literal(val tag: Int, val value: Int, val position: Int) {
    val isString get() = tag == Tag.SHORT_STRING || tag == Tag.LONG_STRING
}

/**
 * A `NewObjectWithBuffer` instruction: an object written as a literal, such as a style. [keyOffset] and
 * [valueOffset] say where its keys and its values start in the literal buffers. [isLong] is the form of
 * the instruction with 32-bit offsets.
 */
internal class ObjectSite(
    val function: Int,
    val offset: Int,
    val entries: List<Pair<String?, Literal>>,
    val isLong: Boolean,
    val keyOffset: Int,
    val valueOffset: Int,
)

/** A `NewArrayWithBuffer` instruction: an array written as a literal. */
internal class ArraySite(val function: Int, val offset: Int, val entries: List<Literal>)

/** One run of equal values in the value buffer. [offset] is relative to the buffer, [payload] is in the file. */
internal class ValueRun(val offset: Int, val tag: Int, val length: Int, val payload: Int)

/**
 * A Hermes bytecode file (React Native's compiled JavaScript), version 96. Only what the patch needs:
 * the string table, the literal buffers and the instructions. The bytes are edited in place.
 *
 * Format: https://github.com/facebook/hermes/blob/main/include/hermes/BCGen/HBC/BytecodeFileFormat.h
 */
internal class HermesBundle(val bytes: ByteArray) {
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun u8(offset: Int) = bytes[offset].toInt() and 0xFF
    private fun u16(offset: Int) = buffer.getShort(offset).toInt() and 0xFFFF
    /** A 32-bit word whose bits are fields. Use [u32] for offsets, sizes and counts. */
    private fun word(offset: Int) = buffer.getInt(offset)

    private fun u32(offset: Int): Int {
        val value = buffer.getInt(offset).toLong() and 0xFFFFFFFFL
        ensure(value <= Int.MAX_VALUE) { "Value out of range at $offset" }
        return value.toInt()
    }

    /** The SHA-1 of the JavaScript source, which identifies the build of the app. */
    val sourceHash: String

    val functionCount: Int
    val stringCount: Int
    private val kindCount: Int
    private val overflowCount: Int
    private val functionHeaders: Int
    private val kindTable: Int
    private val smallStrings: Int
    private val overflowStrings: Int
    private val storage: Int
    private val arrayBuffer: Int
    private val arrayBufferSize: Int
    private val keyBuffer: Int
    private val keyBufferSize: Int
    private val valueBuffer: Int
    private val valueBufferSize: Int

    init {
        ensure(bytes.size > 256 && buffer.getLong(0) == MAGIC) { "This is not a Hermes bytecode file" }
        val version = u32(8)
        ensure(version == VERSION) { "Unsupported Hermes bytecode version $version" }
        ensure(u32(32) == bytes.size) { "The bytecode file has the wrong length" }
        sourceHash = bytes.copyOfRange(12, 32).joinToString("") { "%02x".format(it) }

        functionCount = u32(40)
        kindCount = u32(44)
        val identifierCount = u32(48)
        stringCount = u32(52)
        overflowCount = u32(56)
        val storageSize = u32(60)
        val arrayBufferSize = u32(80)
        keyBufferSize = u32(84)
        valueBufferSize = u32(88)

        fun align(value: Int) = (value + 3) and 3.inv()
        var p = HEADER_SIZE
        functionHeaders = p
        p = align(p + FUNCTION_HEADER_SIZE * functionCount)
        kindTable = p
        p = align(p + 4 * kindCount)
        p = align(p + 4 * identifierCount)
        smallStrings = p
        p = align(p + 4 * stringCount)
        overflowStrings = p
        p = align(p + 8 * overflowCount)
        storage = p
        p = align(p + storageSize)
        arrayBuffer = p
        this.arrayBufferSize = arrayBufferSize
        p = align(p + arrayBufferSize)
        keyBuffer = p
        p = align(p + keyBufferSize)
        valueBuffer = p
        ensure(valueBuffer + valueBufferSize <= bytes.size - FOOTER_SIZE) { "The bytecode file layout is not as expected" }
    }

    fun stringLocation(index: Int): StringLocation {
        val word = word(smallStrings + 4 * index)
        val utf16 = (word and 1) == 1
        var offset = (word ushr 1) and 0x7FFFFF
        var length = (word ushr 24) and 0xFF
        if (length == 0xFF) {
            val entry = overflowStrings + 8 * offset
            offset = u32(entry)
            length = u32(entry + 4)
        }
        return StringLocation(utf16, storage + offset, length)
    }

    fun string(index: Int): String {
        val location = stringLocation(index)
        val charset = if (location.utf16) Charsets.UTF_16LE else Charsets.ISO_8859_1
        return String(bytes, location.offset, location.byteLength, charset)
    }

    /** 0 for a plain string and 1 for an identifier, for every string. Identifiers carry a hash. */
    fun stringKinds(): IntArray {
        val kinds = IntArray(stringCount)
        var next = 0
        for (i in 0 until kindCount) {
            val word = word(kindTable + 4 * i)
            val count = word and 0x7FFFFFFF
            ensure(next + count <= stringCount) { "The string kind table is not as expected" }
            kinds.fill(word ushr 31, next, next + count)
            next += count
        }
        ensure(next == stringCount) { "The string kind table is not as expected" }
        return kinds
    }

    /** Offset and size of the bytecode of function [index]. */
    fun function(index: Int): Pair<Int, Int> {
        val header = functionHeaders + FUNCTION_HEADER_SIZE * index
        val word0 = word(header)
        val word1 = word(header + 4)
        val word2 = word(header + 8)
        val flags = word(header + 12) ushr 24
        if ((flags ushr 5) and 1 == 1) {
            // Too large for the small header. The real one is stored elsewhere.
            val large = ((word2 and 0x1FFFFFF) shl 16) or (word0 and 0x1FFFFFF)
            return u32(large) to u32(large + 8)
        }
        return (word0 and 0x1FFFFFF) to (word1 and 0x7FFF)
    }

    /** Calls [action] with the function, the offset and the opcode of every instruction. */
    fun forEachInstruction(action: (function: Int, offset: Int, opcode: Int) -> Unit) {
        for (index in 0 until functionCount) {
            val (start, size) = function(index)
            var limit = start + size
            var p = start
            while (p < limit) {
                val opcode = u8(p)
                ensure(opcode < INSTRUCTION_SIZES.size) { "Unknown instruction $opcode in function $index" }
                // A switch keeps its jump table after the last instruction.
                if (opcode == SWITCH_IMM) limit = minOf(limit, p + u32(p + 2))
                action(index, p, opcode)
                p += INSTRUCTION_SIZES[opcode]
            }
            ensure(p == limit) { "Function $index does not end on an instruction" }
        }
    }

    /** Reads [count] values of a literal buffer, starting at [offset] inside it. */
    private fun readLiterals(base: Int, size: Int, offset: Int, count: Int): List<Literal> {
        val values = ArrayList<Literal>(count)
        var p = base + offset
        val end = base + size
        while (values.size < count) {
            ensure(p < end) { "A literal buffer ends too early" }
            val header = u8(p)
            val tag = (header ushr 4) and 7
            val run: Int
            if (header ushr 7 == 1) {
                run = ((header and 15) shl 8) or u8(p + 1)
                p += 2
            } else {
                run = header and 15
                p += 1
            }
            repeat(run) {
                if (values.size < count) {
                    val width = payloadWidth(tag)
                    val value = when (tag) {
                        Tag.SHORT_STRING -> u16(p)
                        Tag.LONG_STRING -> u32(p)
                        Tag.BYTE_STRING -> u8(p)
                        else -> 0
                    }
                    values.add(Literal(tag, value, p))
                    p += width
                }
            }
        }
        return values
    }

    /** Every object literal of the program, with its keys. */
    fun objectSites(): List<ObjectSite> {
        val sites = ArrayList<ObjectSite>()
        forEachInstruction { function, p, opcode ->
            val count: Int
            val keyOffset: Int
            val valueOffset: Int
            val isLong = opcode == NEW_OBJECT_WITH_BUFFER_LONG
            when (opcode) {
                NEW_OBJECT_WITH_BUFFER -> {
                    count = u16(p + 4)
                    keyOffset = u16(p + 6)
                    valueOffset = u16(p + 8)
                }
                NEW_OBJECT_WITH_BUFFER_LONG -> {
                    count = u16(p + 4)
                    keyOffset = u32(p + 6)
                    valueOffset = u32(p + 10)
                }
                else -> return@forEachInstruction
            }
            val keys = readLiterals(keyBuffer, keyBufferSize, keyOffset, count)
            val values = readLiterals(valueBuffer, valueBufferSize, valueOffset, count)
            val names = keys.map { if (it.tag == Tag.SHORT_STRING || it.tag == Tag.LONG_STRING || it.tag == Tag.BYTE_STRING) string(it.value) else null }
            sites.add(ObjectSite(function, p, names.zip(values), isLong, keyOffset, valueOffset))
        }
        return sites
    }

    /** Every array literal of the program. */
    fun arraySites(): List<ArraySite> {
        val sites = ArrayList<ArraySite>()
        forEachInstruction { function, p, opcode ->
            val count = u16(p + 4)
            val index = when (opcode) {
                NEW_ARRAY_WITH_BUFFER -> u16(p + 6)
                NEW_ARRAY_WITH_BUFFER_LONG -> u32(p + 6)
                else -> return@forEachInstruction
            }
            sites.add(ArraySite(function, p, readLiterals(arrayBuffer, arrayBufferSize, index, count)))
        }
        return sites
    }

    /** The runs of the value buffer, in order. Empty runs are left out. */
    fun valueRuns(): List<ValueRun> {
        val runs = ArrayList<ValueRun>()
        var p = valueBuffer
        val end = valueBuffer + valueBufferSize
        while (p < end) {
            val header = u8(p)
            val tag = (header ushr 4) and 7
            val length: Int
            val payload: Int
            if (header ushr 7 == 1) {
                length = ((header and 15) shl 8) or u8(p + 1)
                payload = p + 2
            } else {
                length = header and 15
                payload = p + 1
            }
            if (length > 0) runs.add(ValueRun(p - valueBuffer, tag, length, payload))
            p = payload + length * payloadWidth(tag)
        }
        ensure(p == end) { "The value buffer does not end on a run" }
        return runs
    }

    /** The string id stored at [position] by a value of the given string [tag]. */
    fun stringIdAt(tag: Int, position: Int): Int = when (tag) {
        Tag.SHORT_STRING -> u16(position)
        Tag.LONG_STRING -> u32(position)
        Tag.BYTE_STRING -> u8(position)
        else -> throw BundleException("Tag $tag is not a string")
    }

    /** The bytes one value of [tag] takes in a literal buffer. */
    fun valueWidth(tag: Int) = payloadWidth(tag)

    private fun payloadWidth(tag: Int) = when (tag) {
        Tag.NUMBER -> 8
        Tag.LONG_STRING, Tag.INTEGER -> 4
        Tag.SHORT_STRING -> 2
        Tag.BYTE_STRING -> 1
        else -> 0
    }

    fun putU16(offset: Int, value: Int) {
        buffer.putShort(offset, value.toShort())
    }

    fun putU32(offset: Int, value: Int) {
        buffer.putInt(offset, value)
    }

    fun readU8(offset: Int) = u8(offset)
    fun readU16(offset: Int) = u16(offset)

    /** The file ends with the SHA-1 of everything before it. */
    fun refreshFooter() {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(bytes, 0, bytes.size - FOOTER_SIZE)
        digest.digest().copyInto(bytes, bytes.size - FOOTER_SIZE)
    }

    companion object {
        const val FOOTER_SIZE = 20

        private const val MAGIC = 0x1F1903C103BC1FC6L
        private const val VERSION = 96
        private const val HEADER_SIZE = 128
        private const val FUNCTION_HEADER_SIZE = 16

        private const val NEW_OBJECT_WITH_BUFFER = 1
        private const val NEW_OBJECT_WITH_BUFFER_LONG = 2
        private const val NEW_ARRAY_WITH_BUFFER = 5
        private const val NEW_ARRAY_WITH_BUFFER_LONG = 6
        private const val SWITCH_IMM = 133

        /** Size in bytes of each instruction, by opcode. */
        private val INSTRUCTION_SIZES = intArrayOf(
        1, 10, 14, 2, 3, 8, 10, 4, 3, 9, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
        4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 4, 4, 3, 4, 5, 4, 5, 4, 5, 2, 2, 2, 7,
        5, 5, 5, 6, 8, 6, 8, 6, 8, 6, 8, 4, 5, 7, 5, 7, 4, 7, 5, 5, 7, 4, 4, 4, 6, 5,
        6, 4, 4, 4, 5, 5, 6, 7, 7, 7, 7, 4, 7, 3, 2, 2, 4, 2, 3, 1, 1, 3, 5, 7, 5, 7,
        5, 7, 4, 4, 3, 6, 3, 6, 10, 4, 6, 4, 6, 2, 2, 2, 2, 2, 2, 3, 2, 3, 3, 3, 3, 4,
        3, 2, 14, 18, 1, 3, 1, 5, 7, 3, 4, 3, 2, 5, 3, 6, 3, 6, 3, 6, 2, 5, 4, 7, 4, 7,
        4, 7, 4, 7, 4, 7, 4, 7, 4, 7, 4, 7, 4, 7, 4, 7, 4, 7, 4, 7, 4, 7, 4, 7, 4, 7,
        4, 7, 4, 7, 4, 7, 4, 7, 4, 7, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
        )
    }
}
