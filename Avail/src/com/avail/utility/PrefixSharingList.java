/**
 * PrefixSharingList.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.utility;

import java.util.*;
import com.avail.annotations.*;

/**
 * This is an implementation of an immutable {@link List} for which {@link
 * #append(List, Object)}, the non-destructive append operation, takes constant
 * time.  There is also a {@link #withoutLast(List)} operation for producing a
 * list with its rightmost element removed.  Iterating over the entire list
 * takes linear time, and does not use recursion.
 *
 * <p>
 * The implementation should be thread-safe if the lists that are supplied as
 * prefixes are themselves thread-safe.  Do not change any of those lists after
 * constructing {@code PrefixSharingList}s from them.
 * </p>
 *
 * @param <E> The type of elements in the list.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class PrefixSharingList<E> extends AbstractList<E>
{
	/** The size of this list */
	private final int size;

	/** A list containing the first size-1 elements.  Do not modify it. */
	private final @Nullable List<E> allButLast;

	/** The last element of this list. */
	private final E lastElement;

	/**
	 * A lazily computed ArrayList containing (at least) all the elements of
	 * this {@link PrefixSharingList}.
	 */
	private volatile @Nullable List<E> cachedFlatListOrMore;

	@Override
	public int size ()
	{
		return size;
	}

	@Override
	public E get (final int index)
	{
		if (index < 0 || index >= size)
		{
			throw new IndexOutOfBoundsException();
		}
		if (index == size - 1)
		{
			return lastElement;
		}
		cacheFlatListOrMore();
		return cachedFlatListOrMore.get(index);
	}

	/**
	 * If the flattened form of this list isn't already cached in {@link
	 * #cachedFlatListOrMore} then compute it and cache it.  Also record this
	 * flattened list in any prefixes that are themselves {@link
	 * PrefixSharingList}s.  That implies the prefixes may end up with more
	 * elements in their flattened representations than they really represent,
	 * so operations like {@link #iterator()} must compensate for this by
	 * ignoring the extra elements.
	 *
	 * <p>
	 * TODO: This could be considered a reference leak, but it shouldn't be
	 * significant for Avail's purposes.  It can be fixed by storing weak
	 * references within the flat list (the strong references from {@link
	 * #lastElement} will prevent useful elements from disappearing).
	 * </p>
	 *
	 * <p>
	 * An invariant of the class is that either the {@link #allButLast} must be
	 * non-null or the {@link #cachedFlatListOrMore} must be non-null (or both).
	 * </p>
	 */
	private void cacheFlatListOrMore ()
	{
		if (cachedFlatListOrMore != null)
		{
			return;
		}
		final ArrayList<E> flatList = new ArrayList<E>(size);
		PrefixSharingList<E> pointer = this;
		while (true)
		{
			flatList.add(0, pointer.lastElement);
			if (pointer.allButLast instanceof PrefixSharingList<?>)
			{
				pointer = (PrefixSharingList<E>)pointer.allButLast;
				final @Nullable List<E> pointerFlatList =
					pointer.cachedFlatListOrMore;
				if (pointerFlatList != null)
				{
					flatList.addAll(
						0,
						pointerFlatList.subList(0, pointer.size));
					break;
				}
			}
			else
			{
				flatList.addAll(0, pointer.allButLast);
				break;
			}
		}
		// Replace the cached flat lists until we hit a non-PrefixSharingList
		// or a PrefixSharingList with its flat list already set.
		pointer = this;
		while (true)
		{
			if (pointer.cachedFlatListOrMore != null)
			{
				break;
			}
			pointer.cachedFlatListOrMore = flatList;
			if (pointer.allButLast instanceof PrefixSharingList<?>)
			{
				pointer = (PrefixSharingList<E>)pointer.allButLast;
			}
			else
			{
				break;
			}
		}
	}

	@Override
	public Iterator<E> iterator ()
	{
		cacheFlatListOrMore();
		final int mySize = size;
		final @Nullable List<E> flatList = cachedFlatListOrMore;
		assert flatList != null;

		return new Iterator<E>()
		{
			int position = 0;

			@Override
			public boolean hasNext ()
			{
				return position < mySize;
			}

			@Override
			public E next ()
			{
				if (position >= mySize)
				{
					throw new NoSuchElementException();
				}
				return flatList.get(position++);
			}

			@Override
			public void remove ()
			{
				throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * Construct a new {@link PrefixSharingList}.
	 *
	 * @param allButLast All but the last element of the new list.
	 * @param lastElement The last element of the new list.
	 */
	private PrefixSharingList (
		final List<E> allButLast,
		final E lastElement)
	{
		this.size = allButLast.size() + 1;
		this.allButLast = allButLast;
		this.lastElement = lastElement;
	}

	/**
	 * Construct a new {@link PrefixSharingList} truncated to the specified
	 * size.
	 *
	 * @param originalList An immutable list.
	 * @param size The size of the resulting list.
	 */
	private PrefixSharingList (
		final List<E> originalList,
		final int size)
	{
		assert !(originalList instanceof PrefixSharingList<?>);
		assert size <= originalList.size();
		this.size = size;
		this.allButLast = null;
		this.lastElement = allButLast.get(size - 1);
		this.cachedFlatListOrMore = allButLast;
	}

	/**
	 * Produce a new immutable list based on the prefix {@link List} (which must
	 * not be modified after this operation) and the new element to follow them.
	 *
	 * @param allButLast The leading elements of the list.
	 * @param lastElement The value by which to extend the list.
	 * @return A new immutable list with all those elements.
	 */
	public static <E2> List<E2> append (
		final List<E2> allButLast,
		final E2 lastElement)
	{
		if (allButLast.isEmpty())
		{
			return Collections.singletonList(lastElement);
		}
		return new PrefixSharingList<E2>(allButLast, lastElement);
	}

	/**
	 * Produce a new immutable list based on the given list, but with the
	 * last element excluded.  Try to avoid creating new objects if possible.
	 *
	 * @param originalList The original list.
	 * @return An immutable list containing all but the last element of the
	 *         original.
	 */
	public static <E2> List<E2> withoutLast (
		final List<E2> originalList)
	{
		if (originalList instanceof PrefixSharingList<?>)
		{
			final PrefixSharingList<E2>strongOriginal =
				(PrefixSharingList<E2>)originalList;
			if (strongOriginal.allButLast != null)
			{
				return strongOriginal.allButLast;
			}
			return new PrefixSharingList<E2>(
				strongOriginal.cachedFlatListOrMore,
				originalList.size() - 1);
		}
		if (originalList.size() <= 1)
		{
			return Collections.<E2>emptyList();
		}
		return new PrefixSharingList<E2>(
			originalList,
			originalList.size() - 1);
	}

	/**
	 * Answer the last element of the given non-empty list.
	 *
	 * @param list The list.
	 * @return The last element of that list.
	 */
	public static <E2> E2 last (
		final List<E2> list)
	{
		return list.get(list.size() - 1);
	}
}
