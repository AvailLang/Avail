/*
 * A_Map.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

package com.avail.descriptor.maps;

import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.maps.MapDescriptor.Entry;
import com.avail.descriptor.maps.MapDescriptor.MapIterable;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.sets.A_Set;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.optimizer.jvm.CheckedMethod;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;

/**
 * {@code A_Map} is an interface that specifies the map-specific operations
 * that an {@link AvailObject} must implement.  It's a sub-interface of {@link
 * A_BasicObject}, the interface that defines the behavior that all AvailObjects
 * are required to support.
 *
 * <p>
 * The purpose for A_BasicObject and its sub-interfaces is to allow sincere type
 * annotations about the basic kinds of objects that support or may be passed as
 * arguments to various operations.  The VM is free to always declare objects as
 * AvailObject, but in cases where it's clear that a particular object must
 * always be a map, a declaration of A_Map ensures that only the basic object
 * capabilities plus map-like capabilities are to be allowed.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Map
extends A_BasicObject
{
	/**
	 * Find the key/value pair in this map which has the specified key and
	 * answer the value.  Fail if the specified key is not present in the map.
	 * The result is <em>not</em> forced to be immutable, as it's up to the
	 * caller whether the new reference would leak beyond a usage that conserves
	 * its reference count.
	 *
	 * @param keyObject The key to look up.
	 * @return The value associated with that key in the map.
	 */
	AvailObject mapAt (A_BasicObject keyObject);

	/**
	 * Create a new map like this map, but with a new key/value pair as
	 * specified.  If there was an existing key/oldValue pair, then it is
	 * replaced by the new key/value pair.  The original map can be modified in
	 * place (and then returned) if canDestroy is true and the map is mutable.
	 *
	 * @param keyObject The key to add or replace.
	 * @param newValueObject The value to associate with the key in the new map.
	 * @param canDestroy Whether the map can be modified in place if mutable.
	 * @return The new map containing the specified key/value pair.
	 */
	@ReferencedInGeneratedCode
	A_Map mapAtPuttingCanDestroy (
		A_BasicObject keyObject,
		A_BasicObject newValueObject,
		boolean canDestroy);

	/**
	 * The {@link CheckedMethod} for {@link
	 * #mapAtPuttingCanDestroy(A_BasicObject, A_BasicObject, boolean)}.
	 */
	CheckedMethod mapAtPuttingCanDestroyMethod = CheckedMethod.instanceMethod(
		A_Map.class,
		"mapAtPuttingCanDestroy",
		A_Map.class,
		A_BasicObject.class,
		A_BasicObject.class,
		boolean.class);

	/**
	 * Look up the {@code key} in the map.  If present, use the key and the
	 * looked up value as arguments to a call to the {@code transformer}.
	 * Otherwise, use the key and the {@code notFoundValue} ({@link
	 * NilDescriptor#nil} is convenient for this) as arguments to the
	 * transformer.  Store the transformer's result in the map under the key,
	 * destroying the original if {@code canDestroy} is true.  Answer the
	 * resulting map.
	 *
	 * <p>The map must not change during evaluation of the transformer.</p>
	 *
	 * @param key
	 *        The key to look up.
	 * @param notFoundValue
	 *        The value to use as the second argument to the transformer if the
	 *        key was not found.
	 * @param transformer
	 *        The {@link BinaryOperator} that produces a replacement value to
	 *        store into the map.
	 * @param canDestroy
	 *        Whether the map can be modified by this call.
	 * @return The new map, possibly the mutated original map itself, if
	 *         canDestroy is true.
	 */
	A_Map mapAtReplacingCanDestroy (
		final A_BasicObject key,
		final A_BasicObject notFoundValue,
		final BinaryOperator<A_BasicObject> transformer,
		final boolean canDestroy);

	/**
	 * Answer the number of key/value pairs in the map.
	 *
	 * @return The size of the map.
	 */
	int mapSize ();

	/**
	 * Create a new map like this map, but without the key/value pair having the
	 * specified key.  If the key was not present, then answer the original map.
	 * The original map can be modified in place (and then returned) if
	 * canDestroy is true and the map is mutable.
	 *
	 * @param keyObject The key to remove.
	 * @param canDestroy Whether a mutable map can be modified in place.
	 * @return The new map not having the specified key.
	 */
	A_Map mapWithoutKeyCanDestroy (
		A_BasicObject keyObject,
		boolean canDestroy);

	/**
	 * Answer whether the argument is one of the keys of this map.
	 *
	 * @param keyObject The potential key to look for in this map's keys.
	 * @return Whether the potential key was found in this map.
	 */
	boolean hasKey (A_BasicObject keyObject);

	/**
	 * Answer a tuple of values from this map in arbitrary order.  A tuple is
	 * used instead of a set, since the values are not necessarily unique.
	 *
	 * @return The map's values in an arbitrarily ordered tuple.
	 */
	A_Tuple valuesAsTuple ();

	/**
	 * Answer the set of keys in this map.  Since keys of maps and set elements
	 * cannot have duplicates, it follows that the size of the resulting set is
	 * the same as the size of this map.
	 *
	 * @return The set of keys.
	 */
	A_Set keysAsSet ();

	/**
	 * Answer a suitable Iterable<> for iterating over this map's key/value
	 * pairs (made available in a {@link Entry}).  This allows the
	 * Java for-each syntax hack to be used.
	 *
	 * @return An {@linkplain MapIterable iterable for maps}.
	 */
	MapIterable mapIterable ();

	/**
	 * Execute the given action with each key and value.
	 *
	 * @param action The action to perform for each key and value pair.
	 */
	void forEach(BiConsumer<? super AvailObject, ? super AvailObject> action);
}
