/*
 * A_Character.kt
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
package com.avail.descriptor.character

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.dispatch
import com.avail.descriptor.representation.AvailObject

/**
 * [A_Character] is an interface that specifies the
 * [character][CharacterDescriptor]-specific operations that an [AvailObject]
 * must implement.  It's a sub-interface of [A_BasicObject], the interface that
 * defines the behavior that all AvailObjects are required to support.
 *
 * Avail characters correspond exactly with Unicode code points.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Character : A_BasicObject {
	companion object {
		/**
		 * Answer this character's Unicode code point.
		 *
		 * @return
		 *   The character's numerical encoding in Unicode.
		 */
		val A_Character.codePoint: Int get() = dispatch { o_CodePoint(it) }

		/**
		 * Answer whether the receiver, an [object][AvailObject], is a
		 * character with a code point equal to the integer argument.
		 *
		 * @param aCodePoint The code point to be compared to the receiver.
		 * @return `true` if the receiver is a character with a code point
		 * equal to the argument, `false` otherwise.
		 */
		fun A_Character.equalsCharacterWithCodePoint(aCodePoint: Int): Boolean =
			dispatch { o_EqualsCharacterWithCodePoint(it, aCodePoint) }

		/**
		 * Is the [receiver][AvailObject] an Avail character?
		 *
		 * @return `true` if the receiver is a character, `false`
		 * otherwise.
		 */
		val A_Character.isCharacter: Boolean get() =
			dispatch { o_IsCharacter(it) }
	}
}
