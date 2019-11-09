/*
 * AbstractSlotsEnum.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

/**
 * The {@code AbstractSlotsEnum} is an interface that helps ensure that object
 * representations and access are consistent and correct.  In particular, some
 * operations in AvailObject (such as {@link AvailObject#slot(ObjectSlotsEnum)})
 * are expected to operate on enumerations defined as inner classes within the
 * {@link Descriptor} class for which the slot layout is specified.
 *
 * <p>
 * There are two sub-interfaces, {@link ObjectSlotsEnum} and {@link
 * IntegerSlotsEnum}, and the representation access methods defined in {@link
 * AbstractAvailObject} typically restrict the passed enumerations to be of the
 * appropriate kind.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface AbstractSlotsEnum
{
	/**
	 * Answer the name of this enumeration value.
	 *
	 * @return A string that names this enumeration value.
	 */
	String name ();

	/**
	 * Answer an integer that identifies this enumeration value uniquely within
	 * this enumeration subclass (i.e., any enumeration class implementing this
	 * interface).  These values are allocated sequentially to the enumeration
	 * values, starting at zero.
	 *
	 * @return The enumeration value's ordinal number.
	 */
	int ordinal ();
}
