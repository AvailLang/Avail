/*
 * ObjectFieldTypeException.kt
 * Copyright © 1993-2025, The Avail Foundation, LLC.
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

package avail.descriptor.objects

import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.OBJECT_FIELD_RESTRICTION_KEY
import avail.descriptor.representation.A_Atom
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY

/**
 * A [ObjectFieldTypeException] is thrown when attempting to fetch an
 * [ObjectLayoutVariant] for a set of fields, and at least one of the fields has
 * neither the [EXPLICIT_SUBCLASSING_KEY] property nor the
 * [OBJECT_FIELD_RESTRICTION_KEY], or it has the latter but it's not mapped to a
 * type (at or below [ANY]).
 *
 * It may also be thrown when constructing an object or object type and the
 * value or strengthened type, respectively, does not comply with the field
 * atom's [OBJECT_FIELD_RESTRICTION_KEY].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [ObjectFieldTypeException].
 *
 * @param problematicFields
 *   The [List] of [A_Atom]s that had neither an [EXPLICIT_SUBCLASSING_KEY] nor
 *   an [OBJECT_FIELD_RESTRICTION_KEY], or it had the latter but the value was
 *   not a type at or below [ANY].
 */
class ObjectFieldTypeException
constructor(
	val problematicFields: List<A_Atom>
) : Exception()
