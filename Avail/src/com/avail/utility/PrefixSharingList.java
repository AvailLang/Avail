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

/**
 * This is an implementation of an immutable {@link List} for which {@link
 * #append(List, Object)}, the non-destructive append operation, takes constant
 * time.  Iterating over the entire list takes linear time, and does not use
 * recursion.
 *
 * @param <E> The type of elements in the list.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class PrefixSharingList<E> extends AbstractList<E>
{
	/** The size of this list */
	private final int size;

	/** A list containing the first size-1 elements.  Do not modify it. */
	private final List<E> allButLast;

	/** The last element of this list. */
	private final E lastElement;

	/**
	 * A lazily computed ArrayList containing (at least) all the elements of
	 * this {@link PrefixSharingList}.
	 */
	private ArrayList<E> cachedFlatListOrMore;

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
	 * #cachedFlatListOrMore} then computed it and cache it.  Also record this
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
	 */
	private void cacheFlatListOrMore ()
	{
		if (cachedFlatListOrMore != null)
		{
			return;
		}
		cachedFlatListOrMore = new ArrayList<E>(size);
		PrefixSharingList<E> pointer = this;
		while (true)
		{
			cachedFlatListOrMore.add(0, lastElement);
			if (pointer.allButLast instanceof PrefixSharingList<?>)
			{
				pointer = (PrefixSharingList<E>)pointer.allButLast;
				if (pointer.cachedFlatListOrMore != null)
				{
					cachedFlatListOrMore.addAll(
						0,
						pointer.cachedFlatListOrMore);
					return;
				}
				pointer.cachedFlatListOrMore = cachedFlatListOrMore;
			}
			else
			{
				cachedFlatListOrMore.addAll(
					0,
					pointer.allButLast);
				return;
			}
		}
	}

	@Override
	public Iterator<E> iterator ()
	{
		cacheFlatListOrMore();

		return new Iterator<E>()
		{
			int position = 0;

			@Override
			public boolean hasNext ()
			{
				return position < size;
			}

			@Override
			public E next ()
			{
				if (position >= size)
				{
					throw new NoSuchElementException();
				}
				return cachedFlatListOrMore.get(position++);
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
		this.allButLast = allButLast;
		this.lastElement = lastElement;
		this.size = allButLast.size() + 1;
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
}
