/*
 * P_FileRead.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.descriptor.FiberDescriptor.newFiber
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.FILE_KEY
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.numbers.InfinityDescriptor.positiveInfinity
import com.avail.descriptor.numbers.IntegerDescriptor.one
import com.avail.descriptor.sets.SetDescriptor.set
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ByteArrayTupleDescriptor
import com.avail.descriptor.tuples.ByteBufferTupleDescriptor.tupleForByteBuffer
import com.avail.descriptor.tuples.ObjectTupleDescriptor.*
import com.avail.descriptor.tuples.StringDescriptor.formatString
import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FiberTypeDescriptor.fiberType
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.InstanceTypeDescriptor.instanceType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.*
import com.avail.descriptor.types.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.*
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Interpreter.runOutermostFunction
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.io.IOSystem.BufferKey
import com.avail.io.IOSystem.FileHandle
import com.avail.io.SimpleCompletionHandler
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import kotlin.math.min

/**
 * **Primitive:** Read the requested number of bytes from the [file
 * channel][AsynchronousFileChannel] associated with the specified
 * [handle][AtomDescriptor], starting at the requested one-based position.
 * Produce them as a [tuple][ByteArrayTupleDescriptor] of bytes. If fewer bytes
 * are available, then simply produce a shorter tuple; an empty tuple
 * unambiguously indicates that the end of the file has been reached. If the
 * request amount is infinite or very large, fewer bytes may be returned, at the
 * discretion of the Avail VM.
 *
 *
 *
 * Answer a new fiber which, if the read is eventually successful, will be
 * started to apply the [success function][FunctionDescriptor] to the resulting
 * tuple of bytes.  If the read is unsuccessful, the fiber will be started to
 * apply the `failure function` to the error code.  The fiber runs at the
 * specified priority.
 *
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_FileRead : Primitive(6, CanInline, HasSideEffect)
{
	/**
	 * The maximum transfer size when reading from a file.  Attempts to read
	 * more than this will simply be limited to this value.
	 */
	private const val MAX_READ_SIZE = 4194304

	/**
	 * The maximum transfer size for which a buffer is always allocated with the
	 * specified size, without first checking the file size.  Read requests with
	 * requested sizes greater than this will use the start position and the
	 * actual file size to determine how big a buffer to actually use to avoid
	 * over-allocating buffer space.
	 */
	private const val THRESHOLD_READ_SIZE = 32768

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(6)
		val positionObject = interpreter.argument(0)
		val sizeObject = interpreter.argument(1)
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
		if (!handle.canRead)
		{
			return interpreter.primitiveFailure(E_NOT_OPEN_FOR_READ)
		}
		val fileChannel = handle.channel
		if (!positionObject.isLong)
		{
			return interpreter.primitiveFailure(E_EXCEEDS_VM_LIMIT)
		}
		val runtime = interpreter.runtime()
		val ioSystem = runtime.ioSystem()
		val oneBasedPositionLong = positionObject.extractLong()
		// Guaranteed positive by argument constraint.
		assert(oneBasedPositionLong > 0L)
		var size = min(
			if (sizeObject.isInt) sizeObject.extractInt() else MAX_READ_SIZE,
			MAX_READ_SIZE)
		if (size > THRESHOLD_READ_SIZE)
		{
			// Limit the buffer size based on the file's actual size.
			var actualFileSize: Long
			try
			{
				actualFileSize = fileChannel.size()
			}
			catch (e: IOException)
			{
				// The file's inaccessible somehow.  Don't report the exception,
				// since the Avail code didn't ask about the file size.  Limit
				// the buffer size to the threshold size to avoid
				// over-allocating due to this blindness.
				actualFileSize = Long.MAX_VALUE
				size = THRESHOLD_READ_SIZE
			}

			size = if (oneBasedPositionLong > actualFileSize) {
				// Don't bother dealing with empty buffers.  Besides, the file
				// might get more data before we actually read it.
				1
			} else {
				val available = actualFileSize - oneBasedPositionLong + 1
				min(size, min(available, MAX_READ_SIZE.toLong()).toInt())
			}
		}

		assert(size in 1..MAX_READ_SIZE)
		val alignment = handle.alignment
		val augmentedStart =
			(oneBasedPositionLong - 1) / alignment * alignment + 1
		val augmentedEnd =
			(oneBasedPositionLong + size + alignment - 2) / alignment * alignment
		val bufferCount = (augmentedEnd + 1 - augmentedStart) / alignment
		assert(bufferCount == bufferCount.toInt().toLong())
		// Collect the initial run of either cache hits or cache misses.  Limit
		// the number of bytes actually returned to that first run, either
		// concatenating buffers for a run of hits or fetching into a big buffer
		// for a run of misses.
		var firstPresentBufferStart = Long.MIN_VALUE
		var firstMissingBufferStart = Long.MIN_VALUE
		val buffers = mutableListOf<A_Tuple?>()
		for (bufferStart in
			augmentedStart..augmentedEnd step alignment.toLong()
		) {
			val key = BufferKey(handle, bufferStart)
			val bufferHolder = ioSystem.getBuffer(key)
			val buffer = bufferHolder.value
			if (buffer === null)
			{
				if (firstMissingBufferStart == Long.MIN_VALUE)
				{
					// This is the first null buffer encountered.
					firstMissingBufferStart = bufferStart
					if (firstPresentBufferStart != Long.MIN_VALUE)
					{
						// We must have started with hits, and now we know
						// how many buffers in a row to return.
						break
					}
				}
			}
			else
			{
				if (firstPresentBufferStart == Long.MIN_VALUE)
				{
					// This is the first hit encountered.
					firstPresentBufferStart = bufferStart
					if (firstMissingBufferStart != Long.MIN_VALUE)
					{
						// We must have started with misses, and now we know
						// how many buffers in a row to fetch.
						break
					}
				}
			}
			buffers.add(buffer)
		}
		val current = interpreter.fiber()
		val newFiber = newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priority.extractInt())
		{
			formatString("Asynchronous file read, %s", handle.filename)
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
		if (firstPresentBufferStart == augmentedStart)
		{
			// We began with buffer hits, so don't fetch anything.
			// Concatenate the buffers we have.
			val buffersTuple = tupleFromList(buffers)
			val concatenated =
				buffersTuple.concatenateTuplesCanDestroy(false)
			runOutermostFunction(
				runtime,
				newFiber,
				succeed,
				listOf(concatenated))
			return interpreter.primitiveSuccess(newFiber)
		}
		// We began with buffer misses, and we can figure out how many...
		assert(firstMissingBufferStart == augmentedStart)
		assert(buffers.all { it === null })
		size = buffers.size * alignment
		// Now start the asynchronous read.
		val buffer = ByteBuffer.allocateDirect(size)
		SimpleCompletionHandler<Int, Any?>(
			// completion
			{ bytesRead ->
				buffer.flip()
				val bytesTuple: A_Tuple
				if (bytesRead == -1)
				{
					// We started reading after the last byte of the file. Avail
					// expects an empty buffer in this case.
					assert(buffer.remaining() == 0)
					bytesTuple = emptyTuple()
				}
				else
				{
					assert(buffer.remaining() == bytesRead)
					bytesTuple = tupleForByteBuffer(buffer).makeShared()
					assert(bytesTuple.tupleSize() == bytesRead)
					// Seed the file cache, except for the final partial buffer.
					val lastPosition =
						oneBasedPositionLong + bytesRead - 1
					val lastFullBufferStart =
						lastPosition / alignment * alignment - alignment + 1
					var offsetInBuffer = 1
					for (bufferStart in
						oneBasedPositionLong..lastFullBufferStart step
							alignment.toLong())
					{
						val subtuple =
							bytesTuple.copyTupleFromToCanDestroy(
								offsetInBuffer,
								offsetInBuffer + alignment - 1,
								false
							).makeShared()
						assert(subtuple.tupleSize() == alignment)
						val key = BufferKey(handle, bufferStart)
						val bufferHolder = ioSystem.getBuffer(key)
						// The getBuffer() used a lock, so all writes have now
						// happened-before.
						bufferHolder.value = subtuple
						// Do one more lookup of the key to ensure that
						// everything happens-after the above write.
						ioSystem.getBuffer(key)
						offsetInBuffer += alignment
					}
				}
				runOutermostFunction(
					runtime,
					newFiber,
					succeed,
					listOf(bytesTuple))
			},
			// failed
			{
				runOutermostFunction(
					runtime,
					newFiber,
					fail,
					listOf(E_IO_ERROR.numericCode()))
			}
		).guardedDo(fileChannel::read, buffer, oneBasedPositionLong - 1, null)
		return interpreter.primitiveSuccess(newFiber)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tupleFromArray(
				naturalNumbers(),
				inclusive(one(), positiveInfinity()),
				ATOM.o(),
				functionType(tuple(zeroOrMoreOf(bytes())), TOP.o()),
				functionType(
					tuple(instanceType(E_IO_ERROR.numericCode())), TOP.o()),
				bytes()),
			fiberType(TOP.o()))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_NOT_OPEN_FOR_READ,
				E_EXCEEDS_VM_LIMIT))
}
