/*
 * NumericTupleDescriptor.java
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

package com.avail.descriptor.tuples;

import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.representation.IntegerSlotsEnum;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.representation.ObjectSlotsEnum;

import javax.annotation.Nullable;

/**
 * {@code NumericTupleDescriptor} has Avail tuples of integers as its instances. The actual representation of these tuples is determined by subclasses.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class NumericTupleDescriptor
extends TupleDescriptor
{
	/**
	 * Construct a new {@link TupleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param objectSlotsEnumClass
	 *        The Java {@link Class} which is a subclass of {@link ObjectSlotsEnum} and defines this object's object slots layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *        The Java {@link Class} which is a subclass of {@link IntegerSlotsEnum} and defines this object's object slots
	 */
	protected NumericTupleDescriptor (
		final Mutability mutability,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	@Override
	public abstract int o_TupleIntAt (final AvailObject object, final int index);
}
