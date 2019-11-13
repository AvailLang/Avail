/*
 * A_Set.java
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

package com.avail.descriptor;

import com.avail.descriptor.objects.A_BasicObject;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.Iterator;

/**
 * {@code A_Set} is an interface that specifies the set-specific operations
 * that an {@link AvailObject} must implement.  It's a sub-interface of {@link
 * A_BasicObject}, the interface that defines the behavior that all AvailObjects
 * are required to support.
 *
 * <p>
 * The purpose for A_BasicObject and its sub-interfaces is to allow sincere type
 * annotations about the basic kinds of objects that support or may be passed as
 * arguments to various operations.  The VM is free to always declare objects as
 * AvailObject, but in cases where it's clear that a particular object must
 * always be a set, a declaration of A_Set ensures that only the basic object
 * capabilities plus set-like capabilities are to be allowed.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Set
extends A_BasicObject, Iterable<AvailObject>
{
	/**
	 * Construct a {@linkplain TupleDescriptor tuple} from the receiver, a
	 * {@linkplain SetDescriptor set}. Element ordering in the tuple will be
	 * arbitrary and unstable.
	 *
	 * @return A tuple containing each element in the set.
	 */
	A_Tuple asTuple ();

	/**
	 * Answer whether this set contains the specified element.
	 *
	 * @param elementObject The element.
	 * @return {@code true} if the receiver contains the element, {@code false}
	 *         otherwise.
	 */
	boolean hasElement (A_BasicObject elementObject);

	/**
	 * Answer true if and only if every element of the receiver is also present
	 * in the provided set.
	 *
	 * @param another The potential superset of the receiver.
	 * @return Whether the receiver is a subset of another.
	 */
	boolean isSubsetOf (A_Set another);

	/**
	 * Answer an {@linkplain Iterator iterator} suitable for traversing the
	 * elements of the {@linkplain AvailObject receiver} with a Java
	 * <em>foreach</em> construct.
	 *
	 * @return An {@linkplain Iterator iterator}.
	 */
	@Override Iterator<AvailObject> iterator ();

	/**
	 * Check if all elements of the set are all instances of the specified kind
	 * (any type that isn't an instance type).
	 *
	 * @param kind
	 *            The type with which to test all elements.
	 * @return
	 *            Whether all elements conform with the specified non-instance
	 *            type.
	 */
	boolean setElementsAreAllInstancesOfKind (AvailObject kind);

	/**
	 * Answer a set containing all values that are present simultaneously in
	 * both the receiver and the otherSet.
	 *
	 * @param otherSet
	 *            A set.
	 * @param canDestroy
	 *            Whether the receiver or the otherSet can be modified if it is
	 *            mutable.
	 * @return The intersection of the receiver and otherSet.
	 */
	A_Set setIntersectionCanDestroy (
		A_Set otherSet,
		boolean canDestroy);

	/**
	 * Answer whether the receiver and otherSet have any elements in common.
	 *
	 * @param otherSet
	 *        A set to test for intersection with.
	 * @return Whether the intersection of the receiver and otherSet is
	 *         non-empty.
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	boolean setIntersects (
		A_Set otherSet);

	/**
	 * Answer a set containing all values that are present in the receiver but
	 * not in otherSet.
	 *
	 * @param otherSet
	 *            The set to subtract.
	 * @param canDestroy
	 *            Whether the receiver can be modified if it is mutable.
	 * @return The asymmetric difference between the receiver and otherSet.
	 */
	A_Set setMinusCanDestroy (
		A_Set otherSet,
		boolean canDestroy);

	/**
	 * Answer the number of values in the set.
	 *
	 * @return The set's size.
	 */
	@ReferencedInGeneratedCode
	int setSize ();

	/**
	 * Answer a set containing all the elements of this set and all the elements
	 * of the otherSet.
	 *
	 * @param otherSet
	 *            A set.
	 * @param canDestroy
	 *            Whether the receiver or the otherSet can be modified if it is
	 *            mutable.
	 * @return The union of the receiver and otherSet.
	 */
	A_Set setUnionCanDestroy (
		A_Set otherSet,
		boolean canDestroy);

	/**
	 * Answer a set like this one but with newElementObject present.  If it was
	 * already present in the original set then answer that.  The set might be
	 * modified in place (and then returned) if canDestroy is true and the set
	 * is mutable.
	 *
	 * @param newElementObject The object to add.
	 * @param canDestroy Whether the original set can be modified if mutable.
	 * @return The new set containing the specified object.
	 */
	@ReferencedInGeneratedCode
	A_Set setWithElementCanDestroy (
		A_BasicObject newElementObject,
		boolean canDestroy);

	/**
	 * Answer a set like this one but with elementObjectToExclude absent.  If it
	 * was already absent in the original set then answer that.  The set might
	 * be modified in place (and then returned) if canDestroy is true and the
	 * set is mutable.
	 *
	 * @param elementObjectToExclude The object to remove.
	 * @param canDestroy Whether the original set can be modified if mutable.
	 * @return The new set not containing the specified object.
	 */
	A_Set setWithoutElementCanDestroy (
		A_BasicObject elementObjectToExclude,
		boolean canDestroy);
}
