/*
 * P_FileSync.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

import com.avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import com.avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.FILE_KEY
import com.avail.descriptor.fiber.FiberDescriptor.Companion.newFiber
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FiberTypeDescriptor.Companion.fiberType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.bytes
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_INVALID_HANDLE
import com.avail.exceptions.AvailErrorCode.E_IO_ERROR
import com.avail.exceptions.AvailErrorCode.E_NOT_OPEN_FOR_WRITE
import com.avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.execution.Interpreter.Companion.runOutermostFunction
import com.avail.io.IOSystem.FileHandle
import java.io.IOException
import java.nio.channels.AsynchronousFileChannel

/**
 * **Primitive:** Force all system buffers associated with the
 * [writable][FileHandle.canWrite] [file&#32;channel][AsynchronousFileChannel]
 * associated with the [handle][AtomDescriptor] to synchronize with the
 * underlying device.
 *
 * Answer a new fiber which, if the sync is eventually successful, will be
 * started to run the success [function][FunctionDescriptor].  If the sync fails
 * with an [IOException], the fiber will be started to apply the failure
 * function to the error code.  The fiber runs at the specified priority.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_FileSync : Primitive(4, CanInline, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(4)
		val atom = interpreter.argument(0)
		val succeed = interpreter.argument(1)
		val fail = interpreter.argument(2)
		val priority = interpreter.argument(3)

		val pojo = atom.getAtomProperty(FILE_KEY.atom)
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				if (atom.isAtomSpecial()) E_SPECIAL_ATOM else E_INVALID_HANDLE)
		}
		val handle = pojo.javaObjectNotNull<FileHandle>()
		if (!handle.canWrite)
		{
			return interpreter.primitiveFailure(E_NOT_OPEN_FOR_WRITE)
		}

		// Don't block an execution thread - use the runtime's file executor
		// pool instead.  That keeps all the execution threads humming, even if
		// there are several pending blocking I/O operations (like sync).  Note
		// that the current (2014.06.11) implementation of the file executor
		// specifies an unbounded queue, so the fiber execution threads will
		// never be blocked waiting for I/O.
		val priorityInt = priority.extractInt()
		val current = interpreter.fiber()
		val newFiber =
			newFiber(
				succeed.kind().returnType().typeUnion(fail.kind().returnType()),
				priorityInt)
			{
				StringDescriptor.stringFrom(
					"Asynchronous file sync, ${handle.filename}")
			}
		newFiber.setAvailLoader(current.availLoader())
		newFiber.setHeritableFiberGlobals(
			current.heritableFiberGlobals().makeShared())
		newFiber.setTextInterface(current.textInterface())
		newFiber.makeShared()
		succeed.makeShared()
		fail.makeShared()

		val runtime = interpreter.runtime()
		runtime.ioSystem().executeFileTask(
			Runnable {
               try
               {
                   handle.channel.force(true)
               }
               catch (e: IOException)
               {
                   runOutermostFunction(
                       runtime,
                       newFiber,
                       fail,
                       listOf(E_IO_ERROR.numericCode()))
                   return@Runnable
               }

               runOutermostFunction(
                   runtime,
                   newFiber,
                   succeed,
                   emptyList())
           })
		return interpreter.primitiveSuccess(newFiber)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				ATOM.o,
				functionType(emptyTuple, TOP.o),
				functionType(
					tuple(instanceType(E_IO_ERROR.numericCode())),
					TOP.o
				),
				bytes
			),
			fiberType(TOP.o))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_INVALID_HANDLE, E_SPECIAL_ATOM, E_NOT_OPEN_FOR_WRITE))
}
