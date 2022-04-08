/*
 * P_FileWrite.kt
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
package avail.interpreter.primitive.files

import avail.AvailRuntime.Companion.currentRuntime
import avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.FILE_KEY
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.fiber.A_Fiber.Companion.heritableFiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.textInterface
import avail.descriptor.fiber.FiberDescriptor.Companion.newFiber
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.isLong
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.byteArray
import avail.descriptor.tuples.A_Tuple.Companion.byteBuffer
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.transferIntoByteBuffer
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FiberTypeDescriptor.Companion.fiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.bytes
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import avail.exceptions.AvailErrorCode.E_EXCEEDS_VM_LIMIT
import avail.exceptions.AvailErrorCode.E_INVALID_HANDLE
import avail.exceptions.AvailErrorCode.E_IO_ERROR
import avail.exceptions.AvailErrorCode.E_NOT_OPEN_FOR_WRITE
import avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.runOutermostFunction
import avail.io.IOSystem.BufferKey
import avail.io.IOSystem.FileHandle
import avail.io.SimpleCompletionHandler
import avail.utility.evaluation.Combinator.recurse
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.util.Collections.emptyList
import kotlin.math.min

/**
 * **Primitive:** Write the specified [tuple][TupleDescriptor] to the
 * [file][AsynchronousFileChannel] associated with the [handle][AtomDescriptor].
 * Writing begins at the specified one-based position of the file.
 *
 *
 *
 * Answer a new fiber which, if the write is eventually successful, will be
 * started to run the success [function][FunctionDescriptor].  If the write is
 * unsuccessful, the fiber will be started to apply the failure `function` to
 * the error code.  The fiber runs at the specified priority.
 *
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_FileWrite : Primitive(6, CanInline, HasSideEffect)
{
	/**
	 * The maximum transfer size when writing to a file.  Attempts to write
	 * more bytes than this may be broken down internally into transfers that
	 * are this small, possibly recycling the same buffer.
	 */
	const val MAX_WRITE_BUFFER_SIZE = 4194304

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(6)
		val positionObject = interpreter.argument(0)
		val bytes = interpreter.argument(1)
		val atom = interpreter.argument(2)
		val succeed = interpreter.argument(3)
		val fail = interpreter.argument(4)
		val priority = interpreter.argument(5)

		val pojo = atom.getAtomProperty(FILE_KEY.atom)
		if (pojo.isNil)
		{
			return interpreter.primitiveFailure(
				if (atom.isAtomSpecial)
				{
					E_SPECIAL_ATOM
				}
				else
				{
					E_INVALID_HANDLE
				})
		}
		val handle = pojo.javaObjectNotNull<FileHandle>()
		if (!handle.canWrite)
		{
			return interpreter.primitiveFailure(E_NOT_OPEN_FOR_WRITE)
		}
		val fileChannel = handle.channel
		if (!positionObject.isLong)
		{
			return interpreter.primitiveFailure(E_EXCEEDS_VM_LIMIT)
		}
		val alignment = handle.alignment
		val runtime = currentRuntime()
		val ioSystem = runtime.ioSystem
		val oneBasedPositionLong = positionObject.extractLong
		// Guaranteed positive by argument constraint.
		assert(oneBasedPositionLong > 0L)
		// Write the tuple of bytes, possibly split up into manageable sections.
		// Also update the buffer cache to reflect the modified file content.
		val current = interpreter.fiber()
		val newFiber = newFiber(
			runtime,
			succeed.kind().returnType.typeUnion(fail.kind().returnType),
			priority.extractInt)
		{
			stringFrom("Asynchronous file write, ${handle.filename}")
		}
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		newFiber.availLoader = current.availLoader
		// Share and inherit any heritable variables.
		newFiber.heritableFiberGlobals =
			current.heritableFiberGlobals.makeShared()
		// Inherit the fiber's text interface.
		newFiber.textInterface = current.textInterface
		// Share everything that will potentially be visible to the fiber.
		newFiber.makeShared()
		succeed.makeShared()
		fail.makeShared()

		// The iterator produces non-empty ByteBuffers, possibly the same one
		// multiple times, refilling it each time.
		val totalBytes = bytes.tupleSize
		val bufferIterator: Iterator<ByteBuffer> = when {
			bytes.isByteBufferTuple -> {
				val buffer = bytes.byteBuffer.slice()
				listOf(buffer).iterator()
			}
			bytes.isByteArrayTuple -> {
				val buffer = ByteBuffer.wrap(bytes.byteArray)
				listOf(buffer).iterator()
			}
			else -> object : MutableIterator<ByteBuffer> {
				/** The buffer to reuse for writing. */
				val buffer = ByteBuffer.allocateDirect(
					min(totalBytes, MAX_WRITE_BUFFER_SIZE))

				/**
				 * The position in the bytes tuple corresponding with the
				 * current buffer start.
				 */
				var nextSubscript = 1

				override fun hasNext(): Boolean {
					return nextSubscript <= totalBytes
				}

				override fun next(): ByteBuffer {
					if (!hasNext()) {
						throw NoSuchElementException()
					}
					buffer.clear()
					var count = nextSubscript + buffer.limit() - 1
					if (count >= totalBytes)
					{
						// All the rest.
						count = totalBytes
					}
					else
					{
						// It's not all the rest, so round down to the nearest
						// alignment boundary for performance.
						val zeroBasedSubscriptAfterBuffer =
							oneBasedPositionLong + nextSubscript - 2 + count
						val modulus =
							zeroBasedSubscriptAfterBuffer % alignment
						assert(modulus == modulus.toInt().toLong())
						if (modulus < count) {
							// Shorten this buffer so it ends at an alignment
							// boundary of the file, but only if it remains
							// non-empty.  Not only will this improve throughput
							// by allowing the operating system to avoid reading
							// a buffer so it can partially overwrite it, but it
							// also makes our own buffer overwriting code more
							// efficient.
							count -= modulus.toInt()
						}
					}
					bytes.transferIntoByteBuffer(
						nextSubscript, nextSubscript + count - 1, buffer)
					buffer.flip()
					assert(buffer.limit() == count)
					nextSubscript += count
					return buffer
				}

				override fun remove() {
					throw UnsupportedOperationException()
				}
			}
		}
		var nextPosition = oneBasedPositionLong - 1
		var currentBuffer = bufferIterator.next()
		recurse { continueWriting ->
			if (!currentBuffer.hasRemaining())
			{
				if (bufferIterator.hasNext())
				{
					currentBuffer = bufferIterator.next()
					assert(currentBuffer.hasRemaining())
				}
			}
			if (currentBuffer.hasRemaining())
			{
				SimpleCompletionHandler<Int>(
					{
						nextPosition += value
						continueWriting()
					},
					{
						// Invalidate *all* pages for this file to ensure
						// subsequent I/O has a proper opportunity to
						// re-encounter problems like read faults and whatnot.
						synchronized(handle.bufferKeys) {
							for (key in ArrayList(handle.bufferKeys.keys))
							{
								ioSystem.discardBuffer(key)
							}
						}
						runOutermostFunction(
							runtime,
							newFiber,
							fail,
							listOf(E_IO_ERROR.numericCode()))
					}
				).guardedDo {
					fileChannel.write(
						currentBuffer, nextPosition, Unit, handler)
				}
			}
			else
			{
				// Just finished the entire write.  Transfer the data onto
				// any affected cached pages.
				assert(nextPosition == oneBasedPositionLong + totalBytes - 1)
				var subscriptInTuple = 1
				var startOfBuffer = (oneBasedPositionLong - 1) / alignment * alignment + 1
				var offsetInBuffer = (oneBasedPositionLong - startOfBuffer + 1).toInt()
				// Skip this if the file isn't also open for read access.
				if (!handle.canRead)
				{
					subscriptInTuple = totalBytes + 1
				}
				while (subscriptInTuple <= totalBytes)
				{
					// Update one buffer.
					val consumedThisTime = min(
						alignment - offsetInBuffer,
						totalBytes - subscriptInTuple) + 1
					val key = BufferKey(
						handle, startOfBuffer)
					val bufferHolder = ioSystem.getBuffer(key)
					var tuple = bufferHolder.value
					if (offsetInBuffer == 1 && consumedThisTime == alignment)
					{
						tuple = bytes.copyTupleFromToCanDestroy(
							subscriptInTuple,
							subscriptInTuple + consumedThisTime - 1,
							false)
					}
					else if (tuple !== null)
					{
						// Update the cached tuple.
						assert(tuple.tupleSize == alignment)
						val parts = mutableListOf<A_Tuple>()
						if (offsetInBuffer > 1)
						{
							parts.add(
								tuple.copyTupleFromToCanDestroy(
									1, offsetInBuffer - 1, false))
						}
						parts.add(
							bytes.copyTupleFromToCanDestroy(
								subscriptInTuple,
								subscriptInTuple + consumedThisTime - 1,
								false))
						val endInBuffer = offsetInBuffer + consumedThisTime - 1
						if (endInBuffer < alignment)
						{
							parts.add(
								tuple.copyTupleFromToCanDestroy(
									endInBuffer + 1, alignment, false))
						}
						assert(parts.size > 1)
						tuple = parts.removeAt(0)
						while (parts.isNotEmpty())
						{
							tuple = tuple!!.concatenateWith(
								parts.removeAt(0), true)
						}
						assert(tuple!!.tupleSize == alignment)
					}
					// Otherwise we're attempting to update a subregion of
					// an uncached buffer.  Just drop it in that case and
					// let the OS cache pick up the slack.
					if (tuple !== null)
					{
						tuple = tuple.makeShared()
						synchronized(bufferHolder) {
							bufferHolder.value = tuple
						}
					}
					subscriptInTuple += consumedThisTime
					startOfBuffer += alignment.toLong()
					offsetInBuffer = 1
				}
				assert(subscriptInTuple == totalBytes + 1)
				runOutermostFunction(runtime, newFiber, succeed, emptyList())
			}
		}
		return interpreter.primitiveSuccess(newFiber)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tupleFromArray(
				naturalNumbers,
				oneOrMoreOf(bytes),
				ATOM.o,
				functionType(emptyTuple, TOP.o),
				functionType(
					tuple(instanceType(E_IO_ERROR.numericCode())), TOP.o),
				bytes),
			fiberType(TOP.o))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_NOT_OPEN_FOR_WRITE,
				E_EXCEEDS_VM_LIMIT))
}
