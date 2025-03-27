/*
 * A_Variable.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.variables

import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.methods.A_ChunkDependable
import avail.descriptor.module.A_Module
import avail.descriptor.numbers.A_Number
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.tuples.A_String
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.A_Type.Companion.writeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
import avail.descriptor.variables.A_Variable.Companion.compareAndSwapValuesNoCheck
import avail.descriptor.variables.A_Variable.Companion.getValue
import avail.descriptor.variables.A_Variable.Companion.staticCheckForSharedOrReactors
import avail.descriptor.variables.A_Variable.Companion.staticClearValue
import avail.descriptor.variables.A_Variable.Companion.staticGetValueClearing
import avail.descriptor.variables.A_Variable.Companion.staticGetValueMakingImmutable
import avail.descriptor.variables.A_Variable.Companion.staticSetValue
import avail.descriptor.variables.A_Variable.Companion.staticSetValueNoCheck
import avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailException
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
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
	companion object
	{
		/**
		 * Extract the current value of the [variable][VariableDescriptor].
		 * Answer [nil] if the variable has no value.
		 *
		 * @return
		 *   The variable's value or nil.
		 */
		fun A_Variable.value(): AvailObject = dispatch { o_Value(it) }

		/**
		 * Extract the current value of the [variable][VariableDescriptor].
		 * Fail if the variable has no value.
		 *
		 * @return
		 *   The variable's value.
		 * @throws VariableGetException
		 *   If the current value could not be read, e.g., because the variable
		 *   is unassigned.
		 */
		@Throws(VariableGetException::class)
		fun A_Variable.getValue(): AvailObject = dispatch { o_GetValue(it) }

		/**
		 * Extract the current value of the [variable][VariableDescriptor].
		 * Fail if the variable has no value.  Clear the variable afterward.
		 *
		 * @return
		 *   The variable's value prior to it being cleared.
		 * @throws VariableGetException
		 *   If the current value could not be read, e.g., because the variable
		 *   is unassigned.
		 */
		@Throws(VariableGetException::class, VariableSetException::class)
		fun A_Variable.getValueClearing(): AvailObject =
			dispatch { o_GetValueClearing(it) }


		/**
		 * Extract the current value of the [variable][VariableDescriptor].
		 * Fail if the variable has no value.  Clear the variable afterward if
		 * the variable is mutable, otherwise make the value immutable.
		 *
		 * @return
		 *   The variable's value prior to it being optionally cleared.
		 * @throws VariableGetException
		 *   If the current value could not be read, e.g., because the variable
		 *   is unassigned.
		 */
		@Throws(VariableGetException::class, VariableSetException::class)
		fun A_Variable.getValueClearingIfMutable(): AvailObject =
			dispatch { o_GetValueClearingIfMutable(it) }

		/**
		 * Answer `true` if the variable currently has a value, otherwise answer
		 * `false`.  No value is typically represented by the variable's value slot
		 * containing [nil].
		 *
		 * @return
		 *   Whether the variable has a value.
		 */
		fun A_Variable.hasValue(): Boolean = dispatch { o_HasValue(it) }

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
		fun A_Variable.setValue(newValue: A_BasicObject) =
			dispatch { o_SetValue(it, newValue) }

		/**
		 * Assign the given value to the [variable][VariableDescriptor]. The client
		 * should ensure that the value is acceptable for the variable.
		 *
		 * @param newValue
		 *   The variable's new value.
		 */
		@ReferencedInGeneratedCode
		fun A_Variable.setValueNoCheck(newValue: A_BasicObject) =
			dispatch { o_SetValueNoCheck(it, newValue) }

		/**
		 * Write to a local variable that should be guaranteed by the VM not to have
		 * been made shared or to have any [VariableAccessReactor] on it.
		 */
		@ReferencedInGeneratedCode
		fun A_Variable.setUnescapedLocalValueNoCheck(newValue: A_BasicObject) =
			dispatch { o_SetUnescapedLocalValueNoCheck(it, newValue) }

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
		fun A_Variable.getAndSetValue(newValue: A_BasicObject): AvailObject =
			dispatch { o_GetAndSetValue(it, newValue) }

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
		fun A_Variable.compareAndSwapValues(
			reference: A_BasicObject,
			newValue: A_BasicObject
		): Boolean =
			dispatch { o_CompareAndSwapValues(it, reference, newValue) }

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
		@JvmStatic
		fun A_Variable.compareAndSwapValuesNoCheck(
			reference: A_BasicObject,
			newValue: A_BasicObject
		): Boolean =
			dispatch { o_CompareAndSwapValuesNoCheck(it, reference, newValue) }

		/**
		 * Read the variable's value, add the addend to it, and store it back
		 * into the variable.  This sequence of operations is protected by a
		 * lock if the variable is potentially [shared][Mutability.SHARED] among
		 * multiple Avail [fibers][FiberDescriptor].  Fail if the variable had
		 * no value, if the variable's content type is not a subtype of the
		 * [extended&#32;integers][extendedIntegers], if the addend is not an
		 * extended integer, if the sum of the old value and the addend is
		 * undefined (e.g., ∞ plus -∞), or if the sum does not satisfy the
		 * variable's [write&#32;type][writeType].
		 * Return the previous value.
		 *
		 * It is the client's responsibility to ensure the
		 * [read&#32;type][readType] of the variable is a subtype of extended
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
		fun A_Variable.fetchAndAddValue(addend: A_Number): A_Number =
			dispatch { o_FetchAndAddValue(it, addend) }

		/**
		 * Clear the variable.  This causes the variable to have no value, and
		 * subsequent attempts to [get&#32;the&#32;value][A_Variable.getValue] of
		 * this variable will fail.
		 *
		 * The variable is not required to have a value prior to this operation.
		 */
		fun A_Variable.clearValue() = dispatch { o_ClearValue(it) }

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
		fun A_Variable.addWriteReactor(
			key: A_Atom,
			reactor: VariableAccessReactor
		) = dispatch { o_AddWriteReactor(it, key, reactor) }

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
		fun A_Variable.removeWriteReactor(key: A_Atom) =
			dispatch { o_RemoveWriteReactor(it, key) }

		/**
		 * Answer the [set][SetDescriptor] of
		 * [write&#32;reactor][VariableAccessReactor]
		 * [functions][FunctionDescriptor] that have not previously activated.
		 *
		 * @return
		 *   The requested functions.
		 */
		val A_Variable.validWriteReactorFunctions: A_Set
			get() = dispatch { o_ValidWriteReactorFunctions(it) }

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
		fun A_Variable.atomicAddToMap(
			key: A_BasicObject,
			value: A_BasicObject
		) = dispatch { o_AtomicAddToMap(it, key, value) }

		/**
		 * Extract the map from this variable, add the key → value binding to it,
		 * and write it back into the variable.  Don't check the map's type before
		 * writing it – assume it was already guaranteed statically.  Require the
		 * key and values be compatible with the variable's map type.  Require that
		 * the variable contains a map (or is unassigned), and that the map type's
		 * maximum size is ∞, to ensure it will accept the new map.
		 *
		 * This is an atomic operation, so the update is serialized with respect
		 * to other operations on this variable.
		 *
		 * @param key
		 *   The key to add to the map.
		 * @param value
		 *   The value to add to the map.
		 * @throws VariableGetException
		 *   If the variable does not contain a map (i.e., it's unassigned).
		 * @throws VariableSetException
		 *    If the updated map cannot be written back.
		 */
		@Throws(VariableGetException::class, VariableSetException::class)
		fun A_Variable.atomicAddToMapNoCheck(
			key: A_BasicObject,
			value: A_BasicObject
		) = dispatch { o_AtomicAddToMapNoCheck(it, key, value) }

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
		fun A_Variable.atomicRemoveFromMap(key: A_BasicObject) =
			dispatch { o_AtomicRemoveFromMap(it, key) }

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
		fun A_Variable.variableMapHasKey(key: A_BasicObject): Boolean =
			dispatch { o_VariableMapHasKey(it, key) }

		/**
		 * Answer whether this variable is a module-scoped global.
		 *
		 * @return `true` if it is a module-scoped global; `false` otherwise.
		 */
		val A_Variable.isGlobal: Boolean
			get() = dispatch { o_IsGlobal(it) }

		/**
		 * Only applicable to
		 * [global&#32;variables][VariableSharedGlobalDescriptor]. Answer the
		 * [module][A_Module] in which it's defined.
		 *
		 * @return
		 *   The module in which this global variable/constant is defined.
		 */
		val A_Variable.globalModule: A_Module
			get() = dispatch { o_GlobalModule(it) }

		/**
		 * Only applicable to
		 * [global&#32;variables][VariableSharedGlobalDescriptor]. Answer the name
		 * of this global variable or constant.
		 *
		 * @return
		 *   The name of this global variable/constant.
		 */
		val A_Variable.globalName: A_String
			get() = dispatch { o_GlobalName(it) }

		/**
		 * Read the current value of a variable without tripping any observerless
		 * mechanisms or checks.  If the variable is unassigned, answer [nil].
		 */
		fun A_Variable.getValueForDebugger(): AvailObject =
			dispatch { o_GetValueForDebugger(it) }

		/**
		 * Examine this variable.  If it's shared or might have a reactor, answer
		 * true.
		 */
		fun A_Variable.checkForSharedOrReactors(): Boolean
		{
			val traversed = traversed()
			val traversedDescriptor = traversed.descriptor as VariableDescriptor
			if (traversedDescriptor.isShared) return true
			return !traversedDescriptor.withWriteReactorsToModify(
				traversed,
				toModify = false,
				body = MutableMap<*, *>?::isNullOrEmpty)
		}

		/**
		 * Answer whether this variable is both a write-once variable and
		 * initialized from an expression which is stable – always produces the
		 * same value (modulo loading of modules) and has no side-effects.
		 */
		var A_Variable.valueWasStablyComputed: Boolean
			get() = dispatch { o_ValueWasStablyComputed(it) }
			set(value) = dispatch { o_SetValueWasStablyComputed(it, value) }

		fun A_Variable.isPlaceholderVariable(): Boolean =
			dispatch { o_IsPlaceholderVariable(it) }

		fun A_Variable.placeholderVariableLocalIndex(): Int =
			dispatch { o_PlaceholderVariableLocalIndex(it) }



		/* Static methods for the L2/JVM code to invoke. */

		/** The static method equivalent of getValue().makeImmutable() */
		@ReferencedInGeneratedCode
		@JvmStatic
		@Throws(VariableGetException::class)
		fun staticGetValueMakingImmutable(
			variable: A_Variable
		): AvailObject = variable.dispatch { o_GetValue(it) }.makeImmutable()

		/** The [CheckedMethod] for [staticGetValueMakingImmutable]. */
		val getValueMakingImmutableMethod = staticMethod(
			receiverClass = A_Variable::class.java,
			methodName = ::staticGetValueMakingImmutable.name,
			returnClass = AvailObject::class.java,
			A_Variable::class.java)


		/** The static method equivalent of getValueClearing() */
		@ReferencedInGeneratedCode
		@JvmStatic
		@Throws(VariableGetException::class)
		fun staticGetValueClearing(variable: A_Variable): AvailObject =
			variable.dispatch { o_GetValueClearing(it) }

		/** The [CheckedMethod] for [staticGetValueClearing]. */
		val getValueClearingMethod = staticMethod(
			receiverClass = A_Variable::class.java,
			methodName = ::staticGetValueClearing.name,
			returnClass = AvailObject::class.java,
			A_Variable::class.java)


		/**
		 * Read the variable, and if it was still mutable then clear it,
		 * otherwise make the value immutable.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		@Throws(VariableGetException::class)
		fun staticGetValueClearingIfMutable(variable: A_Variable): AvailObject =
			variable.dispatch { o_GetValueClearingIfMutable(it) }

		/** The [CheckedMethod] for [staticGetValueClearing]. */
		val getValueClearingMethodIfMutableMethod = staticMethod(
			receiverClass = A_Variable::class.java,
			methodName = ::staticGetValueClearingIfMutable.name,
			returnClass = AvailObject::class.java,
			A_Variable::class.java)


		@ReferencedInGeneratedCode
		@JvmStatic
		@Throws(VariableSetException::class)
		fun staticSetValue(variable: A_Variable, newValue: A_BasicObject) =
			variable.dispatch { o_SetValue(it, newValue) }

		/** The [CheckedMethod] for [staticSetValue]. */
		val setValueMethod = staticMethod(
			receiverClass = A_Variable::class.java,
			methodName = ::staticSetValue.name,
			returnClass = Void.TYPE,
			A_Variable::class.java,
			A_BasicObject::class.java)


		@ReferencedInGeneratedCode
		@JvmStatic
		fun staticClearValue(variable: A_Variable) =
			variable.dispatch { o_ClearValue(it) }

		/** The [CheckedMethod] for [staticClearValue]. */
		val clearVariableMethod = staticMethod(
			receiverClass = A_Variable::class.java,
			methodName = ::staticClearValue.name,
			returnClass = Void.TYPE,
			A_Variable::class.java)


		@ReferencedInGeneratedCode
		@JvmStatic
		fun staticSetValueNoCheck(
			variable: A_Variable,
			newValue: A_BasicObject
		) = variable.dispatch { o_SetValueNoCheck(it, newValue) }

		/** The [CheckedMethod] for [staticSetValueNoCheck]. */
		val setValueNoCheckMethod = staticMethod(
			receiverClass = A_Variable::class.java,
			methodName = ::staticSetValueNoCheck.name,
			returnClass = Void.TYPE,
			A_Variable::class.java,
			A_BasicObject::class.java)


		@ReferencedInGeneratedCode
		@JvmStatic
		fun staticCompareAndSwapValuesNoCheck(
			variable: A_Variable,
			reference: A_BasicObject,
			newValue: A_BasicObject
		): Boolean = variable.dispatch {
			o_CompareAndSwapValuesNoCheck(it, reference, newValue)
		}

		/** The [CheckedMethod] for [compareAndSwapValuesNoCheck]. */
		val compareAndSwapValuesNoCheckMethod = staticMethod(
			receiverClass = A_Variable::class.java,
			methodName = ::staticCompareAndSwapValuesNoCheck.name,
			returnClass = Boolean::class.javaPrimitiveType!!,
			A_Variable::class.java,
			A_BasicObject::class.java,
			A_BasicObject::class.java)


		@ReferencedInGeneratedCode
		@JvmStatic
		fun staticCheckForSharedOrReactors(variable: A_Variable): Boolean =
			variable.checkForSharedOrReactors()

		/** The [CheckedMethod] for [staticCheckForSharedOrReactors]. */
		val checkForSharedOrReactorsMethod = staticMethod(
			receiverClass = A_Variable::class.java,
			methodName = ::staticCheckForSharedOrReactors.name,
			returnClass = Boolean::class.javaPrimitiveType!!,
			A_Variable::class.java)
	}
}
