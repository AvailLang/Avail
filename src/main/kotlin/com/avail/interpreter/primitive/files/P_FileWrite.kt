/*
 * P_FileWrite.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.files

import com.avail.AvailRuntime.currentRuntime
import com.avail.descriptor.A_Tuple
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.AtomDescriptor
import com.avail.descriptor.AtomDescriptor.SpecialAtom.FILE_KEY
import com.avail.descriptor.FiberDescriptor.newFiber
import com.avail.descriptor.FiberTypeDescriptor.fiberType
import com.avail.descriptor.FunctionDescriptor
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceTypeDescriptor.instanceType
import com.avail.descriptor.IntegerRangeTypeDescriptor.bytes
import com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.StringDescriptor.formatString
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.oneOrMoreOf
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.*
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Interpreter.runOutermostFunction
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.io.IOSystem.BufferKey
import com.avail.io.IOSystem.FileHandle
import com.avail.io.SimpleCompletionHandler
import com.avail.utility.Mutable
import com.avail.utility.MutableLong
import com.avail.utility.evaluation.Combinator.recurse
import java.lang.Math.min
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.util.*
import java.util.Collections.emptyList

/**
 * **Primitive:** Write the specified [ ] to the [file][AsynchronousFileChannel] associated with the [handle][AtomDescriptor]. Writing
 * begins at the specified one-based position of the file.
 *
 *
 *
 * Answer a new fiber which, if the write is eventually successful, will be
 * started to run the success [function][FunctionDescriptor].  If the
 * write is unsuccessful, the fiber will be started to apply the failure `function` to the error code.  The fiber runs at the specified priority.
 *
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_FileWrite : Primitive(6, CanInline, HasSideEffect)
{
	/**
	 * The maximum transfer size when writing to a file.  Attempts to write
	 * more bytes than this may be broken down internally into transfers that
	 * are this small, possibly recycling the same buffer.
	 */
	val MAX_WRITE_BUFFER_SIZE = 4194304

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(6)
		val positionObject = interpreter.argument(0)
		val bytes = interpreter.argument(1)
		val atom = interpreter.argument(2)
		val succeed = interpreter.argument(3)
		val fail = interpreter.argument(4)
		val priority = interpreter.argument(5)

		val pojo = atom.getAtomProperty(FILE_KEY.atom)
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				if (atom.isAtomSpecial) E_SPECIAL_ATOM else E_INVALID_HANDLE)
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
		val ioSystem = runtime.ioSystem()
		val oneBasedPositionLong = positionObject.extractLong()
		// Guaranteed positive by argument constraint.
		assert(oneBasedPositionLong > 0L)
		// Write the tuple of bytes, possibly split up into manageable sections.
		// Also update the buffer cache to reflect the modified file content.
		val current = interpreter.fiber()
		val newFiber = newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priority.extractInt()
		) {
			formatString("Asynchronous file write, %s",
			             handle.filename)
		}
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		newFiber.availLoader(current.availLoader())
		// Share and inherit any heritable variables.
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared())
		// Inherit the fiber's text interface.
		newFiber.textInterface(current.textInterface())
		// Share everything that will potentially be visible to the fiber.
		newFiber.makeShared()
		succeed.makeShared()
		fail.makeShared()

		// The iterator produces non-empty ByteBuffers, possibly the same one
		// multiple times, refilling it each time.
		val totalBytes = bytes.tupleSize()
		val bufferIterator: Iterator<ByteBuffer>
		if (bytes.isByteBufferTuple)
		{
			val buffer = bytes.byteBuffer().slice()
			bufferIterator = listOf(buffer).iterator()
		}
		else if (bytes.isByteArrayTuple)
		{
			val buffer = ByteBuffer.wrap(bytes.byteArray())
			bufferIterator = listOf(buffer).iterator()
		}
		else
		{
			bufferIterator = object : MutableIterator<ByteBuffer>
			{
				/** The buffer to reuse for writing.  */
				internal val buffer = ByteBuffer.allocateDirect(
					min(totalBytes, MAX_WRITE_BUFFER_SIZE))

				/**
				 * The position in the bytes tuple corresponding with the
				 * current buffer start.
				 */
				internal var nextSubscript = 1

				override fun hasNext(): Boolean
				{
					return nextSubscript <= totalBytes
				}

				override fun next(): ByteBuffer
				{
					if (!hasNext())
					{
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
						val zeroBasedSubscriptAfterBuffer = oneBasedPositionLong + nextSubscript - 2 + count
						val modulus = zeroBasedSubscriptAfterBuffer % alignment
						assert(modulus == modulus.toInt().toLong())
						if (modulus < count)
						{
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

				override fun remove()
				{
					throw UnsupportedOperationException()
				}
			}
		}
		val nextPosition = MutableLong(oneBasedPositionLong - 1)
		val currentBuffer = Mutable(bufferIterator.next())
		recurse { continueWriting ->
			if (!currentBuffer.value.hasRemaining())
			{
				if (bufferIterator.hasNext())
				{
					currentBuffer.value = bufferIterator.next()
					assert(currentBuffer.value.hasRemaining())
				}
			}
			if (currentBuffer.value.hasRemaining())
			{
				fileChannel.write<Void>(
					currentBuffer.value,
					nextPosition.value, null,
					SimpleCompletionHandler(
						{ bytesWritten ->
							nextPosition.value += bytesWritten
							continueWriting.value()
							Unit
						},
						{
							// Invalidate *all* pages for this file to
							// ensure subsequent I/O has a proper
							// opportunity to re-encounter problems like
							// read faults and whatnot.
							for (key in ArrayList(
								handle.bufferKeys.keys))
							{
								ioSystem.discardBuffer(key)
							}
							runOutermostFunction(
								runtime,
								newFiber,
								fail,
								listOf(E_IO_ERROR.numericCode()))
							Unit
						}))
			}
			else
			{
				// Just finished the entire write.  Transfer the data onto
				// any affected cached pages.
				assert(nextPosition.value == oneBasedPositionLong + totalBytes - 1)
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
						assert(tuple.tupleSize() == alignment)
						val parts = ArrayList<A_Tuple>(3)
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
						while (!parts.isEmpty())
						{
							tuple = tuple!!.concatenateWith(
								parts.removeAt(0), true)
						}
						assert(tuple!!.tupleSize() == alignment)
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

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tupleFromArray(
				naturalNumbers(),
				oneOrMoreOf(bytes()),
				ATOM.o(),
				functionType(
					emptyTuple(),
					TOP.o()),
				functionType(
					tuple(instanceType(E_IO_ERROR.numericCode())),
					TOP.o()),
				bytes()),
			fiberType(TOP.o()))
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_NOT_OPEN_FOR_WRITE,
				E_EXCEEDS_VM_LIMIT))
	}

}