/*
 * A_Set.kt
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
package avail.descriptor.sets

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.TupleDescriptor
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * [A_Set] is an interface that specifies the set-specific operations that an
 * [AvailObject] must implement.  It's a sub-interface of [A_BasicObject], the
 * interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * The purpose for A_BasicObject and its sub-interfaces is to allow sincere type
 * annotations about the basic kinds of objects that support or may be passed as
 * arguments to various operations.  The VM is free to always declare objects as
 * AvailObject, but in cases where it's clear that a particular object must
 * always be a set, a declaration of A_Set ensures that only the basic object
 * capabilities plus set-like capabilities are to be allowed.
 *
 * An Avail [set][A_Set] is an immutable, finite collection of values without
 * duplicates. Two sets are equal if they contain the same members, regardless
 * of the order that they were specified, or which series of operations produced
 * them.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Set : A_BasicObject, Iterable<AvailObject> {
	/**
	 * Answer an [iterator][Iterator] suitable for traversing the elements
	 * of the [receiver][AvailObject] with a Java or Kotlin *foreach*
	 * construct.
	 *
	 * @return
	 *   An [iterator][Iterator].
	 */
	override fun iterator(): Iterator<AvailObject> = dispatch { o_Iterator(it) }

	companion object
	{
		/**
		 * Construct a [tuple][TupleDescriptor] from the receiver, a
		 * [set][SetDescriptor]. Element ordering in the tuple will be arbitrary
		 * and unstable – two successive invocations on the _same set_ may
		 * produce two differently ordered tuples.
		 *
		 * @return
		 *   A tuple containing each element of the set.
		 */
		val A_Set.asTuple: A_Tuple get() = dispatch { o_AsTuple(it) }

		/**
		 * Dispatch to the descriptor.
		 */
		fun A_Set.equalsSet(aSet: A_Set): Boolean =
			dispatch { o_EqualsSet(it, aSet) }

		/**
		 * Answer whether this set contains the specified element.
		 *
		 * @param elementObject
		 *   The element, any [AvailObject].
		 * @return
		 *   `true` if the receiver contains the element, `false` otherwise.
		 */
		fun A_Set.hasElement(elementObject: A_BasicObject): Boolean =
			dispatch { o_HasElement(it, elementObject) }

		/**
		 * Is the [receiver][AvailObject] an Avail set?
		 *
		 * @return `true` if the receiver is a set, `false` otherwise.
		 */
		val A_Set.isSet: Boolean get() = dispatch { o_IsSet(it) }

		/**
		 * Answer true if and only if every element of the receiver is also
		 * present in the provided set.
		 *
		 * @param another
		 *   The potential superset of the receiver.
		 * @return
		 *   Whether the receiver is a subset of another.
		 */
		fun A_Set.isSubsetOf(another: A_Set): Boolean =
			dispatch { o_IsSubsetOf(it, another) }

		/**
		 * Check if all elements of the set are instances of the specified kind
		 * (any type that isn't an instance type).
		 *
		 * @param kind
		 *   The type with which to test all elements.
		 * @return
		 *   Whether all elements conform with the specified non-instance type.
		 */
		fun A_Set.setElementsAreAllInstancesOfKind(kind: AvailObject): Boolean =
			dispatch { o_SetElementsAreAllInstancesOfKind(it, kind) }

		/**
		 * Answer a set containing all values that are present simultaneously in
		 * both the receiver and the otherSet.
		 *
		 * @param otherSet
		 *   A set.
		 * @param canDestroy
		 *   Whether the receiver or the otherSet can be modified if it is
		 *   mutable.
		 * @return
		 *   The intersection of the receiver and otherSet.
		 */
		fun A_Set.setIntersectionCanDestroy(
			otherSet: A_Set,
			canDestroy: Boolean
		): A_Set = dispatch {
			o_SetIntersectionCanDestroy(it, otherSet, canDestroy)
		}

		/**
		 * Answer whether the receiver and otherSet have any elements in common.
		 *
		 * @param otherSet
		 *   A set to test for intersection with.
		 * @return
		 *   Whether the intersection of the receiver and otherSet is non-empty.
		 */
		fun A_Set.setIntersects(otherSet: A_Set): Boolean =
			dispatch { o_SetIntersects(it, otherSet) }

		/**
		 * Answer a set containing all values that are present in the receiver
		 * but not in [otherSet].
		 *
		 * @param otherSet
		 *   The set to subtract.
		 * @param canDestroy
		 *   Whether the receiver can be modified if it is mutable.
		 * @return
		 *   The asymmetric difference between the receiver and otherSet.
		 */
		fun A_Set.setMinusCanDestroy(
			otherSet: A_Set,
			canDestroy: Boolean
		): A_Set = dispatch { o_SetMinusCanDestroy(it, otherSet, canDestroy) }

		/**
		 * Answer the number of values in the set.
		 *
		 * @return
		 *   The set's size.
		 */
		val A_Set.setSize: Int get() = dispatch { o_SetSize(it) }

		/**
		 * Answer a set containing all the elements of this set and all the
		 * elements of the otherSet.
		 *
		 * @param otherSet
		 *   A set.
		 * @param canDestroy
		 *   Whether the receiver or the otherSet can be modified if it is
		 *   mutable.
		 * @return
		 *   The union of the receiver and otherSet.
		 */
		fun A_Set.setUnionCanDestroy(
			otherSet: A_Set,
			canDestroy: Boolean
		): A_Set = dispatch { o_SetUnionCanDestroy(it, otherSet, canDestroy) }

		/**
		 * Answer a set like this one but with newElementObject present.  If it
		 * was already present in the original set then answer that.  The set
		 * might be modified in place (and then returned) if canDestroy is true
		 * and the set is mutable.
		 *
		 * @param newElementObject
		 *   The object to add.
		 * @param canDestroy
		 *   Whether the original set can be modified if mutable.
		 * @return
		 *   The new set containing the specified object.
		 */
		fun A_Set.setWithElementCanDestroy(
			newElementObject: A_BasicObject,
			canDestroy: Boolean
		): A_Set = dispatch {
			o_SetWithElementCanDestroy(it, newElementObject, canDestroy)
		}

		/**
		 * Answer a set like this one but with elementObjectToExclude absent.
		 * If it was already absent in the original set then answer that.  The
		 * set might be modified in place (and then returned) if canDestroy is
		 * true and the set is mutable.
		 *
		 * @param elementObjectToExclude
		 *   The object to remove.
		 * @param canDestroy
		 *   Whether the original set can be modified if mutable.
		 * @return
		 *   The new set not containing the specified object.
		 */
		fun A_Set.setWithoutElementCanDestroy(
			elementObjectToExclude: A_BasicObject,
			canDestroy: Boolean
		): A_Set = dispatch {
			o_SetWithoutElementCanDestroy(
				it, elementObjectToExclude, canDestroy)
		}

		/**
		 * Answer the given [A_Set]'s size.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun setSizeStatic(self: AvailObject): Int =
			self.descriptor().o_SetSize(self)

		/** The [CheckedMethod] for [setSizeStatic]. */
		val setSizeMethod = staticMethod(
			A_Set::class.java,
			::setSizeStatic.name,
			Int::class.javaPrimitiveType!!,
			AvailObject::class.java)

		/**
		 * Answer an [A_Set] with an additional element.  The original can be
		 * destroyed if it's mutable.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun setWithElementStatic(
			self: AvailObject,
			newElementObject: AvailObject
		): AvailObject = self.descriptor().o_SetWithElementCanDestroy(
			self, newElementObject, true) as AvailObject

		/** The [CheckedMethod] for [setWithElementStatic]. */
		val setWithElementMethod = staticMethod(
			A_Set::class.java,
			::setWithElementStatic.name,
			AvailObject::class.java,
			AvailObject::class.java,
			AvailObject::class.java)
	}
}
