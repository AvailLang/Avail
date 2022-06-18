/*
 * AbstractSlotsEnum.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.representation

/**
 * The `AbstractSlotsEnum` is an interface that helps ensure that object
 * representations and access are consistent and correct.  In particular, some
 * operations in AvailObject (such as [AvailObject.slot]) are expected to
 * operate on enumerations defined as inner classes within the [Descriptor]
 * class for which the slot layout is specified.
 *
 * There are two sub-interfaces, [ObjectSlotsEnum] and [IntegerSlotsEnum]. The
 * representation access methods defined in [AvailObjectRepresentation]
 * typically restrict the passed enumerations to be of the appropriate kind.
 *
 * The Kotlin methods [Enum.name] and [Enum.ordinal] (and Java's corresponding
 * but incompatible methods) are specified here, and in subclasses for Java
 * compatibility, to ensure something that purports to be an Enum probably is
 * so, and to make these methods available to it, even though neither Java nor
 * Kotlin provides a mechanism to say an interface is only appropriate for
 * subtypes of [Enum] – because it's a class.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface AbstractSlotsEnum
{
	companion object
	{
		/**
		 * In Java it was possible to define this interface in such a way that
		 * the `name` method was abstract and implemented by each specific
		 * [Enum], but Kotlin breaks this mechanism.  BUT – we're able to cast
		 * `this` to [Enum] to get to that field.  I believe this code gets
		 * copied down into each specific Enum subclass, so the dynamic type
		 * check for the cast is trivially eliminated in each case.  And worst
		 * case, Hotspot will be able to inline calls to this from sites that
		 * are known to be Enums.
		 */
		val AbstractSlotsEnum.fieldName get() = (this as Enum<*>).name

		/**
		 * In Java it was possible to define this interface in such a way that
		 * the `ordinal()` method was abstract and implemented by each specific
		 * [Enum], but Kotlin breaks this mechanism.  BUT – we're able to cast
		 * `this` to [Enum] to get to that field.  I believe this code gets
		 * copied down into each specific Enum subclass, so the dynamic type
		 * check for the cast is trivially eliminated in each case.  And worst
		 * case, Hotspot will be able to inline calls to this from sites that
		 * are known to be Enums.
		 */
		val AbstractSlotsEnum.fieldOrdinal get() = (this as Enum<*>).ordinal
	}
}
