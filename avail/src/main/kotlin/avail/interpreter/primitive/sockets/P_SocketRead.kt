/*
 * P_SocketRead.kt
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

import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.SOCKET_KEY
import avail.descriptor.representation.A_Atom.Companion.getAtomProperty
import avail.descriptor.representation.A_Atom.Companion.isAtomSpecial
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ByteBufferTupleDescriptor
import avail.descriptor.tuples.ByteBufferTupleDescriptor.Companion.tupleForByteBuffer
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
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
 * **Primitive:** Read from the [socket][AsynchronousSocketChannel] referenced
 * by the specified [handle][AtomDescriptor], reading at most the specified
 * number of bytes.  The calling fiber is suspended until the read completes,
 * then resumed with a 2-tuple: the [bytes][ByteBufferTupleDescriptor] that
 * were read, and a [boolean][booleanType] that is [true][trueObject] if the
 * socket has reached end-of-stream.  If the read fails, the fiber fails with
 * [E_IO_ERROR].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_SocketRead : Primitive2(CanSuspend, HasSideEffect)
{
	override fun attempt2(
		interpreter: Interpreter,
		size: AvailObject,
		handle: AvailObject
	): A_BasicObject? = interpreter.run {
		val pojo = handle.getAtomProperty(SOCKET_KEY.atom)
		if (pojo.isNil)
		{
			return fail(
				if (handle.isAtomSpecial) E_SPECIAL_ATOM else E_INVALID_HANDLE)
		}
		val socket = pojo.javaObjectNotNull<AsynchronousSocketChannel>()
		val buffer = ByteBuffer.allocateDirect(size.extractInt)
		return suspendThen {
			// The socket completes the read asynchronously, and its completion
			// handler resumes the fiber.
			SimpleCompletionHandler<Int>(
				{
					buffer.flip()
					succeed(
						tuple(
							tupleForByteBuffer(buffer),
							objectFromBoolean(value == -1)))
				},
				{ fail(E_IO_ERROR) }
			).guardedDo { socket.read(buffer, Unit, handler) }
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(inclusive(0, Int.MAX_VALUE), ATOM()),
			tupleTypeForTypes(zeroOrMoreOf(u8), booleanType))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_IO_ERROR))
}
