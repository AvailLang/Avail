/*
 * A_Definition.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.descriptor.methods

import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.parsing.A_DefinitionParsingPlan
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor
import com.avail.descriptor.types.ListPhraseTypeDescriptor
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * `A_Sendable` is the common superinterface that subsumes [A_Definition] and
 * [A_Macro].  It's the kind of thing for which an invocation site can be
 * parsed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Sendable : A_BasicObject {
	/**
	 * If this is a [method&#32;definition][MethodDefinitionDescriptor] then
	 * answer the actual [function][FunctionDescriptor].  If this is a
	 * [macro&#32;definition][MacroDescriptor], answer the macro body
	 * function.  Fail otherwise.
	 *
	 * @return
	 *   The function of the method/macro definition.
	 */
	fun bodyBlock(): A_Function

	/**
	 * Answer a [function&#32;type][FunctionTypeDescriptor] that identifies
	 * where this definition occurs in the [method][MethodDescriptor]'s directed
	 * acyclic graph of definitions.
	 *
	 * @return
	 *   The function type for this definition.
	 */
	fun bodySignature(): A_Type

	/**
	 * Answer the [module][ModuleDescriptor] in which this
	 * [definition][DefinitionDescriptor] occurred.
	 *
	 * Also defined in [A_SemanticRestriction] and [A_GrammaticalRestriction].
	 *
	 * @return
	 *   The definition's originating module.
	 */
	fun definitionModule(): A_Module

	/**
	 * Answer the [A_String] that names the [module][ModuleDescriptor] in which
	 * this [definition][DefinitionDescriptor] occurred.  If the definition is
	 * built-in (i.e., not created in any module), reply with a suitable string
	 * to indicate this.
	 *
	 * @return
	 *   The definition's originating module's name.
	 */
	fun definitionModuleName(): A_String

	/**
	 * Answer whether this is an
	 * [abstract&#32;definition][AbstractDefinitionDescriptor].
	 *
	 * @return
	 *   Whether it's abstract.
	 */
	@ReferencedInGeneratedCode
	fun isAbstractDefinition(): Boolean

	/**
	 * Is the [receiver][AvailObject] a
	 * [forward&#32;declaration&#32;site][ForwardDefinitionDescriptor]?
	 *
	 * @return
	 *   `true` if the receiver is a forward declaration site.
	 */
	@ReferencedInGeneratedCode
	fun isForwardDefinition(): Boolean

	/**
	 * Is the [receiver][AvailObject] a
	 * [method&#32;definition][MethodDefinitionDescriptor]?
	 *
	 * @return
	 *   `true` if the receiver is a method definition.
	 */
	fun isMethodDefinition(): Boolean

	/**
	 * Answer the [list&#32;phrase&#32;type][ListPhraseTypeDescriptor] for this
	 * definition.  The parser uses this type to produce a customized
	 * [parsing&#32;plan][A_DefinitionParsingPlan], specialized to a particular
	 * [A_Sendable].
	 *
	 * @return
	 *   A subtype of `list phrase type`.
	 */
	fun parsingSignature(): A_Type
}
