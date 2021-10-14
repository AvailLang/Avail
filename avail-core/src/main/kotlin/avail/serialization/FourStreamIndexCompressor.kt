/*
 * FourStreamIndexCompressor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package avail.serialization

/**
 * In the same way that a modern CPU manages a small number of prefetch streams,
 * we keep object references short (ideally one byte) by managing a small array
 * of [Int]s that point to recently accessed referenced objects.  During
 * serialization, an algorithm decides whether to reuse an existing pointer,
 * adjusting it by a small offset, or jump to a new absolute position because
 * there were no pointers close enough to produce an efficient relative
 * adjustment.  During deserialization, the pointers are used to reconstruct
 * each offset or new absolute position, and the appropriate pointer is updated
 * in exactly same way as occurred during serialization.
 *
 * When an absolute pointer is used, a [0..3] counter indicates which of the
 * four pointers should be replaced.  That counter is then incremented (mod 4)
 * to the next pointer that will be replaced.
 */
class FourStreamIndexCompressor : IndexCompressor
{
	/**
	 * The pointers used to compress indices.  Indices sufficiently near one of
	 * these values can be written compactly, while also adjusting the pointer.
	 */
	private val pointers = IntArray(4) { 0 }

	/**
	 * A mapping from each stream index to its next-most-recently accessed
	 * stream's index.  Augmented with element `[4]` as a ring head.
	 */
	private val successors = IntArray(5) { (it + 1) % 5 }

	/**
	 * A mapping from each stream index to its more-recently accessed stream's
	 * index.  Augmented with element `[4]` as a ring head.
	 */
	private val predecessors = IntArray(5) { (it + 4) % 5 }

	/**
	 * The current index, tracked to allow a negative relative offset to be used
	 * when the requested index is too far from any of the pointers.
	 */
	private var currentIndex = 0

	/**
	 * Check if we're between 16 before and 15 after one of the pointers,
	 * (always starting with the 0th one).  If so, bias the delta to be 0..31,
	 * shift it left twice, and add the pointer number [0..3] that was used.
	 * Also adjust that pointer to the new value.  That produces a value in
	 * [0..127], which gets written in one byte by the serializer.
	 *
	 * Otherwise, replace the least recently used pointer, marking it as most
	 * recently used in the ring, and answering the index + 128.
	 *
	 * NOTE: This implementation requires inputs to be in the range
	 * `[0..Int.MAX_VALUE - 128]`.
	 */
	override fun compress(index: Int): Int
	{
		for (pointerNumber in 0..3)
		{
			val delta = index - pointers[pointerNumber]
			if (delta in -16..15)
			{
				pointers[pointerNumber] = index
				moveToHead(pointerNumber)
				return (delta + 16 shl 2) + pointerNumber
			}
		}
		// It's not close enough to any of the pointers.  Replace the least
		// recently used pointer, move it to the head of the augmented queue,
		// and answer 128 plus the distance backward from the currentIndex to
		// the given index.
		val victimPointer = predecessors[4]
		pointers[victimPointer] = index
		moveToHead(victimPointer)
		return (currentIndex - index) + 128
	}

	/**
	 * Do the reverse of [compress()][compress].
	 */
	override fun decompress(index: Int): Int
	{
		return if (index < 128)
		{
			val victimPointer = index and 3
			val delta = (index shr 2) - 16
			pointers[victimPointer] += delta
			moveToHead(victimPointer)
			pointers[victimPointer]
		}
		else
		{
			// Choose the same victim pointer that compress did earlier.
			val victimPointer = predecessors[4]
			val absoluteIndex = currentIndex - (index - 128)
			pointers[victimPointer] = absoluteIndex
			moveToHead(victimPointer)
			absoluteIndex
		}
	}

	/**
	 * The current index has just advanced.  Track it to allow relative backward
	 * offsets to be used when necessary.
	 */
	override fun incrementIndex()
	{
		currentIndex++
	}

	/**
	 * The next value that would be used as an index.  Do not change the
	 * currentIndex.
	 */
	override fun currentIndex(): Int = currentIndex

	/**
	 * Move the indicated stream to the head of the (augmented) ring.  While
	 * there are several reads and writes here, this will either stay hot in the
	 * CPU's caches or not be invoked frequently enough to matter.  The reads
	 * and writes are mostly independent of other actions, other than when
	 * encountering an index that's not compressible.
	 */
	private fun moveToHead(pointer: Int)
	{
		// Link around it to remove it.
		val oldPredecessor = predecessors[pointer]
		val oldSuccessor = successors[pointer]
		successors[oldPredecessor] = oldSuccessor
		predecessors[oldSuccessor] = oldPredecessor
		// Insert at the head.  First the outward links to the pointer.
		val oldHead = successors[4]
		successors[pointer] = oldHead
		predecessors[pointer] = 4
		// Now update the links to the pointer.
		predecessors[oldHead] = pointer
		successors[4] = pointer
	}
}
