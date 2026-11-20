/*
 * LabelDescriptor.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package avail.descriptor.functions

import avail.descriptor.functions.LabelDescriptor.ObjectSlots.CALLER
import avail.descriptor.functions.LabelDescriptor.ObjectSlots.FUNCTION
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Continuation
import avail.descriptor.representation.A_Function
import avail.descriptor.representation.A_Label
import avail.descriptor.representation.A_Label.Companion.caller
import avail.descriptor.representation.A_Label.Companion.function
import avail.descriptor.representation.A_RawFunction.Companion.methodName
import avail.descriptor.representation.A_String.Companion.asNativeString
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine3
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.types.LabelTypeDescriptor.Companion.labelTypeForFunctionType
import avail.descriptor.types.TypeTag
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.primitive.controlflow.P_ExitContinuationWithResultIf
import avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.serialization.SerializerOperation
import java.util.IdentityHashMap

/**
 * A label names an invocation of a [function][FunctionDescriptor], so that
 * the invocation can be [restarted][P_RestartContinuationWithArguments] with
 * new arguments or [exited][P_ExitContinuationWithResultIf] with a value.
 * It is created by [L1Operation.L1Ext_doPushLabel] at the start of a block
 * that declares one.
 *
 * A label holds only the invocation's [caller][CALLER] and its
 * [function][FUNCTION].  Restarting it makes the caller the current
 * continuation again and invokes the function afresh with the supplied
 * arguments; exiting it returns the value to the caller.  Neither needs the
 * invocation's arguments, locals, or stack, so a label captures none of them,
 * and two labels with equal callers and functions are equal.
 *
 * @constructor
 * Construct a new `LabelDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class LabelDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability, TypeTag.LABEL_TAG, ObjectSlots::class.java, null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [continuation][ContinuationDescriptor] that invoked the labeled
		 * function, or [nil] if the function was the outermost frame of its
		 * fiber.  It is immutable, since restarting the label resumes it again
		 * each time.
		 */
		CALLER,

		/** The [function][FunctionDescriptor] that the label invokes. */
		FUNCTION
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Unit>,
		indent: Int)
	{
		builder.append("label for ")
		builder.append(self[FUNCTION].code().methodName.asNativeString())
	}

	override fun o_Caller(self: AvailObject): A_Continuation = self[CALLER]

	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		another.equalsLabel(self)

	override fun o_EqualsLabel(self: AvailObject, aLabel: A_Label) =
		self.sameAddressAs(aLabel)
			|| (self[FUNCTION].equals(aLabel.function)
				&& self[CALLER].equals(aLabel.caller))

	override fun o_Function(self: AvailObject): A_Function = self[FUNCTION]

	override fun o_Hash(self: AvailObject): Int =
		combine3(self[CALLER].hash(), self[FUNCTION].hash(), 0x16E04C57)

	/**
	 * Answer the label's type, which is the label type for its function's
	 * type.
	 */
	override fun o_Kind(self: AvailObject): A_Type =
		labelTypeForFunctionType(self[FUNCTION].kind())

	override fun o_NameForDebugger(self: AvailObject) =
		super.o_NameForDebugger(self) +
			" /* ${self[FUNCTION].code().methodName.asNativeString()} */"

	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.LABEL

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object
	{
		/**
		 * Create an immutable label for an invocation of the given function by
		 * the given caller.
		 *
		 * @param function
		 *   The function whose invocation the label names.
		 * @param caller
		 *   The continuation that invoked the function, or [nil] if the
		 *   function is the outermost frame of its fiber.
		 * @return
		 *   The new label.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun createLabel(
			function: A_Function,
			caller: A_Continuation
		): AvailObject
		{
			assert(function.code().codePrimitive() == null)
			return mutable.createImmutable {
				setSlot(CALLER, caller)
				setSlot(FUNCTION, function)
			}
		}

		/** The [CheckedMethod] for [createLabel]. */
		val createLabelMethod: CheckedMethod = staticMethod(
			::createLabel,
			A_Function::class.java,
			A_Continuation::class.java)

		/** The mutable [LabelDescriptor]. */
		private val mutable = LabelDescriptor(Mutability.MUTABLE)

		/** The immutable [LabelDescriptor]. */
		private val immutable = LabelDescriptor(Mutability.IMMUTABLE)

		/** The shared [LabelDescriptor]. */
		private val shared = LabelDescriptor(Mutability.SHARED)
	}
}
