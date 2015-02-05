/**
 * A_Definition.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
 * {@code A_Definition} is an interface that specifies the operations specific
 * to {@linkplain DefinitionDescriptor definitions} (of a {@linkplain
 * MethodDescriptor method}) in Avail.  It's a sub-interface of {@link
 * A_BasicObject}, the interface that defines the behavior that all AvailObjects
 * are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Definition
extends A_BasicObject
{
	/**
	 * Answer the {@linkplain ModuleDescriptor module} in which this
	 * definition occurred.
	 *
	 * @return The module containing this definition.
	 */
	A_Module module ();

	/**
	 * Answer the {@linkplain MethodDescriptor method} that this {@linkplain
	 * DefinitionDescriptor definition} is for.
	 *
	 * <p>Also defined in {@link A_SemanticRestriction}.</p>
	 *
	 * @return The definition's method.
	 */
	A_Method definitionMethod ();

	/**
	 * Answer the {@link ModuleDescriptor module} in which this {@linkplain
	 * DefinitionDescriptor definition} occurred.
	 *
	 * <p>Also defined in {@link A_SemanticRestriction}. and {@link
	 * A_GrammaticalRestriction}</p>
	 *
	 * @return The definition's originating module.
	 */
	A_Module definitionModule ();

	/**
	 * Answer a {@linkplain FunctionTypeDescriptor function type} that
	 * identifies where this definition occurs in the {@linkplain
	 * MethodDescriptor method}'s directed acyclic graph of definitions.
	 *
	 * @return The function type for this definition.
	 */
	A_Type bodySignature ();

	/**
	 * Answer whether this is an {@linkplain AbstractDefinitionDescriptor
	 * abstract definition}.
	 *
	 * @return Whether it's abstract.
	 */
	boolean isAbstractDefinition ();

	/**
	 * Is the {@linkplain AvailObject receiver} a {@linkplain
	 * ForwardDefinitionDescriptor forward declaration site}?
	 *
	 * @return {@code true} if the receiver is a forward declaration site.
	 */
	boolean isForwardDefinition ();

	/**
	 * Is the {@linkplain AvailObject receiver} a {@linkplain
	 * MethodDefinitionDescriptor method definition}?
	 *
	 * @return {@code true} if the receiver is a method definition.
	 */
	boolean isMethodDefinition ();

	/**
	 * Answer whether this definition is a {@linkplain MacroDefinitionDescriptor
	 * macro definition}.  Macro definitions may not be mixed with any other
	 * kinds of definitions.
	 *
	 * @return Whether it's a macro.
	 */
	boolean isMacroDefinition ();

	/**
	 * If this is a {@linkplain MethodDefinitionDescriptor method definition}
	 * then answer the actual {@linkplain FunctionDescriptor function}.  Fail if
	 * this is not a method definition.
	 *
	 * @return The method definition's function.
	 */
	A_Function bodyBlock ();
}