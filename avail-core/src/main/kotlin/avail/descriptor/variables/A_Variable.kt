/*
 * A_Variable.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package avail.descriptor.variables

import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.methods.A_ChunkDependable
import avail.descriptor.module.A_Module
import avail.descriptor.numbers.A_Number
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.tuples.A_String
import avail.descriptor.types.A_Type
import avail.descriptor.types.IntegerRangeTypeDescriptor
import avail.descriptor.types.VariableTypeDescriptor
import avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailException
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * `A_Variable` is an interface that specifies the behavior specific to Avail
 * [variables][VariableDescriptor] that an [AvailObject] must implement.  It's a
 * sub-interface of [A_BasicObject], the interface that defines the behavior
 * that all AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Variable : A_ChunkDependable
{
	/**
	 * Extract the variable's kind.  This is always a
	 * [variable&#32;type][VariableTypeDescriptor].
	 *
	 * @return
	 *   The variable's kind.
	 */
	override fun kind(): A_Type

	/**
	 * Extract the current value of the [variable][VariableDescriptor].  Answer
	 * [nil][NilDescriptor.nil] if the variable has no value.
	 *
	 * @return
	 *   The variable's value or nil.
	 */
	fun value(): AvailObject

	/**
	 * Extract the current value of the [variable][VariableDescriptor].  Fail if
	 * the variable has no value.
	 *
	 * @return
	 *   The variable's value.
	 * @throws VariableGetException
	 *   If the current value could not be read, e.g., because the variable is
	 *   unassigned.
	 */
	@ReferencedInGeneratedCode
	@Throws(VariableGetException::class)
	fun getValue(): AvailObject

	/**
	 * Extract the current value of the [variable][VariableDescriptor].  Fail if
	 * the variable has no value.  Clear the variable afterward.
	 *
	 * @return
	 *   The variable's value prior to it being cleared.
	 * @throws VariableGetException
	 *   If the current value could not be read, e.g., because the variable is
	 *   unassigned.
	 */
	@ReferencedInGeneratedCode
	@Throws(VariableGetException::class)
	fun getValueClearing(): AvailObject

	/**
	 * Answer `true` if the variable currently has a value, otherwise answer
	 * `false`.  No value is typically represented by the variable's value slot
	 * containing [NilDescriptor.nil].
	 *
	 * @return
	 *   Whether the variable has a value.
	 */
	fun hasValue(): Boolean

	/**
	 * Assign the given value to the [variable][VariableDescriptor]. Fail if the
	 * value does not have a type suitable for the variable.
	 *
	 * @param newValue
	 *   The variable's proposed new value.
	 * @throws VariableSetException
	 *   If the new value is incorrectly typed.
	 */
	@ReferencedInGeneratedCode
	@Throws(VariableSetException::class)
	fun setValue(newValue: A_BasicObject)

	/**
	 * Assign the given value to the [variable][VariableDescriptor]. The client
	 * should ensure that the value is acceptable for the variable.
	 *
	 * @param newValue
	 *   The variable's new value.
	 */
	@ReferencedInGeneratedCode
	fun setValueNoCheck(newValue: A_BasicObject)

	/**
	 * Read the variable's value and set it to the new value.  Answer the old
	 * value.  Fail if the new value is not suitable for the variable, or if the
	 * variable had no value.  Ensure that the entire operation runs atomically
	 * with respect to other reads and writes of the variable.  Use information
	 * about whether the variable is potentially [ shared][Mutability.SHARED]
	 * between Avail [fibers][FiberDescriptor] to determine whether locking
	 * operations are needed.
	 *
	 * @param newValue
	 *   The value to assign.
	 * @return
	 *   The previous value of the variable.
	 * @throws VariableGetException
	 *   If the current value could not be read, e.g., because the variable is
	 *   unassigned.
	 * @throws VariableSetException
	 *   If the new value is incorrectly typed.
	 */
	@Throws(VariableGetException::class, VariableSetException::class)
	fun getAndSetValue(newValue: A_BasicObject): AvailObject

	/**
	 * Read the variable's value, compare it to a reference value via semantic
	 * [equality][A_BasicObject.equals], and if they're equal, store a provided
	 * new value into the variable and answer true. Otherwise answer false.  If
	 * the variable is potentially [shared][Mutability.SHARED], then ensure
	 * suitable locks bracket this entire sequence of operations.
	 *
	 * @param reference
	 *   The value to compare against the variable's current value.
	 * @param newValue
	 *   The replacement value to store if the reference value is equal to the
	 *   variable's old value.
	 * @return
	 *   Whether the replacement took place.
	 * @throws VariableGetException
	 *   If the current value could not be read, e.g., because the variable is
	 *   unassigned.
	 * @throws VariableSetException
	 *   If the new value is incorrectly typed.
	 */
	@Throws(VariableGetException::class, VariableSetException::class)
	fun compareAndSwapValues(
		reference: A_BasicObject,
		newValue: A_BasicObject): Boolean

	/**
	 * Read the variable's value, compare it to a reference value via semantic
	 * [equality][A_BasicObject.equals], and if they're equal, store a provided
	 * new value into the variable and answer true. Otherwise answer false.  If
	 * the variable is potentially [shared][Mutability.SHARED], then ensure
	 * suitable locks bracket this entire sequence of operations.
	 *
	 * Don't check the [newValue]'s type.  It's the client's responsibility to
	 * ensure it has a suitable type to be stored in this variable.
	 *
	 * If the variable was unassigned, treat it the same as a failed comparison
	 * with the [reference].
	 *
	 * @param reference
	 *   The value to compare against the variable's current value.
	 * @param newValue
	 *   The replacement value to store if the reference value is equal to the
	 *   variable's old value.
	 * @return
	 *   Whether the replacement took place.
	 */
	@ReferencedInGeneratedCode
	fun compareAndSwapValuesNoCheck(
		reference: A_BasicObject,
		newValue: A_BasicObject): Boolean

	/**
	 * Read the variable's value, add the addend to it, and store it back into
	 * the variable.  This sequence of operations is protected by a lock if the
	 * variable is potentially [shared][Mutability.SHARED] among multiple Avail
	 * [fibers][FiberDescriptor].  Fail if the variable had no value, if the
	 * variable's content type is not a subtype of the
	 * [extended&#32;integers][IntegerRangeTypeDescriptor.extendedIntegers], if
	 * the addend is not an extended integer, if the sum of the old value and
	 * the addend is undefined (e.g., ∞ plus -∞), or if the sum does not satisfy
	 * the variable's [write&#32;type][VariableTypeDescriptor.o_WriteType].
	 * Return the previous value.
	 *
	 * It is the client's responsibility to ensure the
	 * [read&#32;type][A_Type.readType] of the variable is a subtype of extended
	 * integer.
	 *
	 * @param addend
	 *   The value by which to adjust the variable.
	 * @return
	 *   The previous value of the variable.
	 * @throws VariableGetException
	 *   If the current value could not be read, e.g., because the variable is
	 *   unassigned.
	 * @throws VariableSetException
	 *   If the new value is incorrectly typed.
	 */
	@Throws(VariableGetException::class, VariableSetException::class)
	fun fetchAndAddValue(addend: A_Number): A_Number

	/**
	 * Clear the variable.  This causes the variable to have no value, and
	 * subsequent attempts to [get&#32;the&#32;value][A_Variable.getValue] of
	 * this variable will fail.
	 *
	 * The variable is not required to have a value prior to this operation.
	 */
	@ReferencedInGeneratedCode
	fun clearValue()

	/**
	 * Add a [write&#32;reactor][VariableAccessReactor] to the
	 * [variable][VariableDescriptor] and associate it with the specified key
	 * (for subsequent removal).
	 *
	 * @param key
	 *   An [atom][AtomDescriptor].
	 * @param reactor
	 *   A write reactor.
	 */
	fun addWriteReactor(key: A_Atom, reactor: VariableAccessReactor)

	/**
	 * Remove the [write&#32;reactor][VariableAccessReactor] associated with the
	 * specified [key][AtomDescriptor] from the [variable][VariableDescriptor].
	 *
	 * @param key
	 *   An atom.
	 * @throws AvailException
	 *   If the [key&#32;is&#32;not&#32;found][AvailErrorCode.E_KEY_NOT_FOUND].
	 */
	@Throws(AvailException::class)
	fun removeWriteReactor(key: A_Atom)

	/**
	 * Answer the [set][SetDescriptor] of
	 * [write&#32;reactor][VariableAccessReactor]
	 * [functions][FunctionDescriptor] that have not previously activated.
	 *
	 * @return
	 *   The requested functions.
	 */
	fun validWriteReactorFunctions(): A_Set?

	/**
	 * Answer whether this variable is both a write-once variable and
	 * initialized from an expression which is stable – always produces the same
	 * value (modulo loading of modules) and has no side-effects.
	 *
	 * @return
	 *   Whether the variable was initialized from a stable computation.
	 */
	fun valueWasStablyComputed(): Boolean

	/**
	 * Set whether this write-once variable was initialized from an expression
	 * which is stable – always produces the same value (modulo loading of
	 * modules) and has no side-effects.
	 *
	 * @param wasStablyComputed
	 *   Whether the variable was initialized from a stable computation.
	 */
	fun setValueWasStablyComputed(wasStablyComputed: Boolean)

	/**
	 * Extract the map from this variable, add the key → value binding to it,
	 * and write it back into the variable.
	 *
	 * This is an atomic operation, so the update is serialized with respect
	 * to other operations on this variable.
	 *
	 * @param key
	 *   The key to add to the map.
	 * @param value
	 *   The value to add to the map.
	 * @throws VariableGetException
	 *   If the variable does not contain a map.
	 * @throws VariableSetException
	 *    If the updated map cannot be written back.
	 */
	@Throws(VariableGetException::class, VariableSetException::class)
	fun atomicAddToMap(key: A_BasicObject, value: A_BasicObject)

	/**
	 * Extract the map from this variable, remove the key if present, and write
	 * it back into the variable.
	 *
	 * This is an atomic operation, so the update is serialized with respect
	 * to other operations on this variable.
	 *
	 * @param key
	 *   The key to remove from the map.
	 * @throws VariableGetException
	 *   If the variable does not contain a map.
	 * @throws VariableSetException
	 *    If the updated map cannot be written back.
	 */
	@Throws(VariableGetException::class, VariableSetException::class)
	fun atomicRemoveFromMap(key: A_BasicObject)

	/**
	 * Test whether the map in this variable has the specified key.
	 *
	 * This is an atomic operation, so the read is serialized with respect
	 * to other operations on this variable.
	 *
	 * @param key
	 *   The key to look for in the map.
	 * @throws VariableGetException
	 *   If the variable is uninitialized.
	 * @return
	 *   `true` iff the map in this variable has the specified key.
	 */
	@Throws(VariableGetException::class)
	fun variableMapHasKey(key: A_BasicObject): Boolean

	/**
	 * Answer whether this variable is a module-scoped global.
	 *
	 * @return `true` if it is a module-scoped global; `false` otherwise.
	 */
	fun isGlobal(): Boolean

	/**
	 * Only applicable to
	 * [global&#32;variables][VariableSharedGlobalDescriptor]. Answer the
	 * [module][A_Module] in which it's defined.
	 *
	 * @return
	 *   The module in which this global variable/constant is defined.
	 */
	fun globalModule(): A_Module?

	/**
	 * Only applicable to
	 * [global&#32;variables][VariableSharedGlobalDescriptor]. Answer the name
	 * of this global variable or constant.
	 *
	 * @return
	 *   The name of this global variable/constant.
	 */
	fun globalName(): A_String?

	/**
	 * Read the current value of a variable without tripping any observerless
	 * mechanisms or checks.  If the variable is unassigned, answer [nil].
	 */
	fun getValueForDebugger(): AvailObject

	companion object
	{
		/** The [CheckedMethod] for [getValue]. */
		val getValueMethod = instanceMethod(
			A_Variable::class.java,
			A_Variable::getValue.name,
			AvailObject::class.java)

		/** The [CheckedMethod] for [setValue]. */
		val setValueMethod = instanceMethod(
			A_Variable::class.java,
			A_Variable::setValue.name,
			Void.TYPE,
			A_BasicObject::class.java)

		/** The [CheckedMethod] for [setValueNoCheck]. */
		val setValueNoCheckMethod = instanceMethod(
			A_Variable::class.java,
			A_Variable::setValueNoCheck.name,
			Void.TYPE,
			A_BasicObject::class.java)

		/** The [CheckedMethod] for [compareAndSwapValuesNoCheck]. */
		val compareAndSwapValuesNoCheckMethod = instanceMethod(
			A_Variable::class.java,
			A_Variable::compareAndSwapValuesNoCheck.name,
			Boolean::class.javaPrimitiveType!!,
			A_BasicObject::class.java,
			A_BasicObject::class.java)
	}
}
