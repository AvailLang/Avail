/*
 * AbstractAvailObject.kt
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
package com.avail.descriptor.representation

import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * `AbstractAvailObject` specifies the essential layout and storage requirements
 * of an Avail object, but does not specify a particular representation. As
 * such, it defines requirements for object and integer storage capability,
 * identity comparison by object address, indirection capability, and descriptor
 * access.
 *
 * @constructor
 * @param initialDescriptor
 *   The object's [descriptor][AbstractDescriptor] at creation time. Most
 *   messages are redirected through the descriptor to allow the behavior and
 *   representation to change, often without changing the observable semantics.
 *   The descriptor essentially says how this object should behave, including
 *   how its fields are laid out.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;todd@availlang.org&gt;
 */
abstract class AbstractAvailObject protected constructor(
	initialDescriptor: AbstractDescriptor)
{
	/**
	 * The object's [descriptor][AbstractDescriptor]. Most messages are
	 * redirected through the descriptor to allow the behavior and
	 * representation to change, often without changing the observable
	 * semantics. The descriptor essentially says how this object should behave,
	 * including how its fields are laid out.
	 */
	@field:Volatile
	protected var currentDescriptor = initialDescriptor

	/** Retrieve this object's current [descriptor][AbstractDescriptor]. */
	@ReferencedInGeneratedCode
	fun descriptor() = currentDescriptor

	/** Replace this object's current [currentDescriptor]AbstractDescriptor]. */
	fun setDescriptor(newDescriptor: AbstractDescriptor) {
		currentDescriptor = newDescriptor
	}

	/**
	 * Check if the object's address is valid. Throw an [Error] if it lies
	 * outside of all the currently allocated memory regions.
	 *
	 * Note: This is not meaningful in the Java/Kotlin implementation.
	 *
	 * @throws Error
	 *   If the address is invalid.
	 */
	@Throws(Error::class)
	protected fun checkValidAddress() { }

	/**
	 * Answer whether the [objects][AvailObject] occupy the same memory
	 * addresses.
	 *
	 * @param anotherObject
	 *   Another object.
	 * @return
	 *   Whether the receiver and the other object occupy the same storage.
	 */
	fun sameAddressAs(anotherObject: A_BasicObject) = this === anotherObject

	/**
	 * Replace the [descriptor][AbstractDescriptor] with a
	 * [filler][FillerDescriptor]. This blows up for most messages, catching
	 * further uses of this object. Note that all further uses are incorrect by
	 * definition.
	 */
	fun destroy() {
		currentDescriptor = FillerDescriptor.shared
	}

	/**
	 * Has this [object][AvailObject] been [destroyed][destroy]?
	 *
	 * @return
	 *   `true` if the object has been destroyed, `false` otherwise.
	 */
	protected val isDestroyed: Boolean
		get() {
			checkValidAddress()
			return currentDescriptor === FillerDescriptor.shared
		}

	/**
	 * Answer the number of integer slots. All variable integer slots occur
	 * following the last fixed integer slot.
	 *
	 * @return
	 *   The number of integer slots.
	 */
	abstract fun integerSlotsCount(): Int

	/**
	 * Answer the number of variable integer slots in this object. This does not
	 * include the fixed integer slots.
	 *
	 * @return
	 *   The number of variable integer slots.
	 */
	fun variableIntegerSlotsCount() =
		integerSlotsCount() - currentDescriptor.numberOfFixedIntegerSlots()

	/**
	 * Answer the number of object slots in this [AvailObject]. All variable
	 * object slots occur following the last fixed object slot.
	 *
	 * @return
	 *   The number of object slots.
	 */
	abstract fun objectSlotsCount(): Int

	/**
	 * Answer the number of variable object slots in this [AvailObject]. This
	 * does not include the fixed object slots.
	 *
	 * @return
	 *   The number of variable object slots.
	 */
	fun variableObjectSlotsCount() =
		objectSlotsCount() - currentDescriptor.numberOfFixedObjectSlots()

	/**
	 * Sanity check: ensure that the specified field is writable.
	 *
	 * @param e
	 *   An `enum` value whose ordinal is the field position.
	 */
	fun checkWriteForField(e: AbstractSlotsEnum) =
		currentDescriptor.checkWriteForField(e)

	/**
	 * Slice the current [object][AvailObject] into two objects, the left one
	 * (at the same starting address as the input), and the right one (a
	 * [filler][FillerDescriptor] object that nobody should ever create a
	 * pointer to). The new filler can have zero post-header slots (i.e., just
	 * the header), but the left object must not, since it may turn into an
	 * [indirection][IndirectionDescriptor] some day and will require at least
	 * one slot for the target pointer.
	 *
	 * @param newIntegerSlotsCount
	 *   The number of integer slots in the left object.
	 */
	abstract fun truncateWithFillerForNewIntegerSlotsCount(
		newIntegerSlotsCount: Int)

	/**
	 * Slice the current [object][AvailObject] into two objects, the left one
	 * (at the same starting address as the input), and the right one (a
	 * [filler][FillerDescriptor] object that nobody should ever create a
	 * pointer to). The new filler can have zero post-header slots (i.e., just
	 * the header), but the left object must not, since it may turn into an
	 * [indirection][IndirectionDescriptor] some day and will require at least
	 * one slot for the target pointer.
	 *
	 * @param newObjectSlotsCount
	 *   The number of object slots in the left object.
	 */
	abstract fun truncateWithFillerForNewObjectSlotsCount(
		newObjectSlotsCount: Int)

	companion object {
		/** The [CheckedMethod] for [descriptor]. */
		val descriptorMethod: CheckedMethod = instanceMethod(
			AbstractAvailObject::class.java,
			AbstractAvailObject::descriptor.name,
			AbstractDescriptor::class.java)
	}
}
