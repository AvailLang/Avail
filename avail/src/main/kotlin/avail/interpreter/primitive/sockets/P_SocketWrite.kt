/*
 * P_SocketWrite.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.interpreter.primitive.sockets

import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.SOCKET_KEY
import avail.descriptor.representation.A_Atom
import avail.descriptor.representation.A_Atom.Companion.getAtomProperty
import avail.descriptor.representation.A_Atom.Companion.isAtomSpecial
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Tuple.Companion.byteArray
import avail.descriptor.representation.A_Tuple.Companion.byteBuffer
import avail.descriptor.representation.A_Tuple.Companion.transferIntoByteBuffer
import avail.descriptor.representation.A_Tuple.Companion.tupleSize
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.exceptions.AvailErrorCode.E_INVALID_HANDLE
import avail.exceptions.AvailErrorCode.E_IO_ERROR
import avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanSuspend
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive2
import avail.io.SimpleCompletionHandler
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

/**
 * **Primitive:** Write the specified bytes to the
 * [socket][AsynchronousSocketChannel] referenced by the specified
 * [handle][A_Atom].  The calling fiber is suspended until every byte has been
 * written, then resumed.  If the write fails, the fiber fails with
 * [E_IO_ERROR].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_SocketWrite : Primitive2(CanSuspend, HasSideEffect)
{
	override fun attempt2(
		interpreter: Interpreter,
		tuple: AvailObject,
		handle: AvailObject
	): A_BasicObject? = interpreter.run {
		val pojo = handle.getAtomProperty(SOCKET_KEY.atom)
		if (pojo.isNil)
		{
			return fail(
				if (handle.isAtomSpecial) E_SPECIAL_ATOM else E_INVALID_HANDLE)
		}
		val socket = pojo.javaObjectNotNull<AsynchronousSocketChannel>()
		// Obtain a buffer for writing.
		val buffer = when {
			tuple.isByteBufferTuple -> tuple.byteBuffer.slice()
			tuple.isByteArrayTuple -> ByteBuffer.wrap(tuple.byteArray)
			else -> ByteBuffer.allocateDirect(tuple.tupleSize).also {
				tuple.transferIntoByteBuffer(1, tuple.tupleSize, it)
				it.flip()
			}
		}
		return suspendThen {
			// The socket completes each write asynchronously, and its
			// completion handler either continues writing or resumes the
			// fiber.
			SimpleCompletionHandler<Int>(
				{
					if (buffer.hasRemaining())
					{
						// Not done all writing.  Continue.
						socket.write(buffer, Unit, handler)
					}
					else
					{
						// Done all writing.  Report success.
						succeed(nil)
					}
				},
				{ fail(E_IO_ERROR) }
			).guardedDo { socket.write(buffer, Unit, handler) }
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(zeroOrMoreOf(u8), ATOM()), TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_IO_ERROR))
}
