/**
 * EnumField.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.annotations;

import java.lang.annotation.*;
import com.avail.descriptor.*;

/**
 * {@code EnumField} annotation is used to indicate which enumeration should be
 * used to describe an integer value embedded in an integer slot that has this
 * annotation.  This is used for pretty-printing {@linkplain AvailObject}s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface EnumField
{
	/**
	 * This annotation field indicates the {@link Enum} responsible for
	 * describing the integer slot to which the annotation is applied.  The
	 * value of the field (an {@code int}) should equal an {@linkplain
	 * Enum#ordinal() ordinal} of a member of the specified {@code enum}.  If a
	 * {@link #lookupMethodName()} is also specified then the int value may be
	 * something other than the Enum's ordinal.
	 */
	public Class<? extends IntegerEnumSlotDescriptionEnum> describedBy ();

	/**
	 * This optional annotation field indicates the name of a static method
	 * defined within the {@linkplain #describedBy() describing enumeration}.
	 * The method should take an {@code int} argument and return an instance of
	 * the {@code #describedBy()} enumeration or null.  If null, only the
	 * numeric value is displayed, otherwise the enumeration value's name is
	 * displayed.  If this annotation field is omitted, the value of the field
	 * is treated as the {@linkplain Enum#ordinal() ordinal} to look up.
	 * Similarly, in this case an ordinal that is out of range will only display
	 * its numeric value.
	 */
	public String lookupMethodName () default "";
}
