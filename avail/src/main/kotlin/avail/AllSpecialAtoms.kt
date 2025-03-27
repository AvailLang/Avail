/*
 * AllSpecialAtoms.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail

import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.Exceptions
import avail.descriptor.representation.AvailObject
import avail.descriptor.tokens.TokenDescriptor.StaticInit
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.types.PojoTypeDescriptor

/**
 * The special [A_Atom]s of the [runtime][AvailRuntime].
 *
 * **DO NOT** alter existing entries, as they are used to generate the
 * `SpecialObjectNames_en.properties` file.  If a new entry is appended, you
 * should run the `generateSpecialObjectNames` Gradle task, update the entries
 * in that file, then run the `generateBootstrap` task.
 */
enum class AllSpecialAtoms(givenAtom: A_Atom)
{
	ALL_TOKENS_KEY(SpecialAtom.ALL_TOKENS_KEY.atom),
	CLIENT_DATA_GLOBAL_KEY(SpecialAtom.CLIENT_DATA_GLOBAL_KEY.atom),
	COMPILER_SCOPE_MAP_KEY(SpecialAtom.COMPILER_SCOPE_MAP_KEY.atom),
	COMPILER_SCOPE_STACK_KEY(SpecialAtom.COMPILER_SCOPE_STACK_KEY.atom),
	EXPLICIT_SUBCLASSING_KEY(SpecialAtom.EXPLICIT_SUBCLASSING_KEY.atom),
	FALSE(SpecialAtom.FALSE.atom),
	FILE_KEY(SpecialAtom.FILE_KEY.atom),
	HERITABLE_KEY(SpecialAtom.HERITABLE_KEY.atom),
	MACRO_BUNDLE_KEY(SpecialAtom.MACRO_BUNDLE_KEY.atom),
	OBJECT_TYPE_NAME_PROPERTY_KEY(SpecialAtom.OBJECT_TYPE_NAME_PROPERTY_KEY.atom),
	SERVER_SOCKET_KEY(SpecialAtom.SERVER_SOCKET_KEY.atom),
	SOCKET_KEY(SpecialAtom.SOCKET_KEY.atom),
	STATIC_TOKENS_KEY(SpecialAtom.STATIC_TOKENS_KEY.atom),
	STATIC_TOKEN_INDICES_KEY(SpecialAtom.STATIC_TOKEN_INDICES_KEY.atom),
	TRUE(SpecialAtom.TRUE.atom),
	DONT_DEBUG_KEY(SpecialAtom.DONT_DEBUG_KEY.atom),
	ABSTRACT_DEFINER(SpecialMethodAtom.ABSTRACT_DEFINER.atom),
	ADD_TO_MAP_VARIABLE(SpecialMethodAtom.ADD_TO_MAP_VARIABLE.atom),
	ALIAS(SpecialMethodAtom.ALIAS.atom),
	APPLY(SpecialMethodAtom.APPLY.atom),
	ATOM_PROPERTY(SpecialMethodAtom.ATOM_PROPERTY.atom),
	CONTINUATION_CALLER(SpecialMethodAtom.CONTINUATION_CALLER.atom),
	CRASH(SpecialMethodAtom.CRASH.atom),
	CREATE_LITERAL_PHRASE(SpecialMethodAtom.CREATE_LITERAL_PHRASE.atom),
	CREATE_LITERAL_TOKEN(SpecialMethodAtom.CREATE_LITERAL_TOKEN.atom),
	FORWARD_DEFINER(SpecialMethodAtom.FORWARD_DEFINER.atom),
	GET_RETHROW_JAVA_EXCEPTION(SpecialMethodAtom.GET_RETHROW_JAVA_EXCEPTION.atom),
	GET_VARIABLE(SpecialMethodAtom.GET_VARIABLE.atom),
	GRAMMATICAL_RESTRICTION(SpecialMethodAtom.GRAMMATICAL_RESTRICTION.atom),
	MACRO_DEFINER(SpecialMethodAtom.MACRO_DEFINER.atom),
	METHOD_DEFINER(SpecialMethodAtom.METHOD_DEFINER.atom),
	ADD_POSTLOAD_FUNCTION(SpecialMethodAtom.ADD_POSTLOAD_FUNCTION.atom),
	ADD_UNLOAD_FUNCTION(SpecialMethodAtom.ADD_UNLOAD_FUNCTION.atom),
	PUBLISH_ATOMS(SpecialMethodAtom.PUBLISH_ATOMS.atom),
	PUBLISH_ALL_ATOMS_FROM_OTHER_MODULE(SpecialMethodAtom.PUBLISH_ALL_ATOMS_FROM_OTHER_MODULE.atom),
	RESUME_CONTINUATION(SpecialMethodAtom.RESUME_CONTINUATION.atom),
	RECORD_TYPE_NAME(SpecialMethodAtom.RECORD_TYPE_NAME.atom),
	CREATE_MODULE_VARIABLE(SpecialMethodAtom.CREATE_MODULE_VARIABLE.atom),
	SEAL(SpecialMethodAtom.SEAL.atom),
	SEMANTIC_RESTRICTION(SpecialMethodAtom.SEMANTIC_RESTRICTION.atom),
	LEXER_DEFINER(SpecialMethodAtom.LEXER_DEFINER.atom),
	PUBLISH_NEW_NAME(SpecialMethodAtom.PUBLISH_NEW_NAME.atom),
	CREATE_ATOM(SpecialMethodAtom.CREATE_ATOM.atom),
	CREATE_HERITABLE_ATOM(SpecialMethodAtom.CREATE_HERITABLE_ATOM.atom),
	CREATE_EXPLICIT_SUBCLASS_ATOM(SpecialMethodAtom.CREATE_EXPLICIT_SUBCLASS_ATOM.atom),
	SET_STYLER(SpecialMethodAtom.SET_STYLER.atom),
	TERMINATE_CURRENT_FIBER(SpecialMethodAtom.TERMINATE_CURRENT_FIBER.atom),
	EXCEPTION_ATOM(Exceptions.exceptionAtom),
	STACK_DUMP_ATOM(Exceptions.stackDumpAtom),
	POJO_SELF_TYPE_ATOM(PojoTypeDescriptor.pojoSelfTypeAtom()),
	END_OF_FILE_TOKEN_CLASSIFIER(TokenType.END_OF_FILE.atom),
	KEYWORD_TOKEN_CLASSIFIER(TokenType.KEYWORD.atom),
	LITERAL_TOKEN_CLASSIFIER(TokenType.LITERAL.atom),
	OPERATOR_TOKEN_CLASSIFIER(TokenType.OPERATOR.atom),
	COMMENT_TOKEN_CLASSIFIER(TokenType.COMMENT.atom),
	WHITESPACE_TOKEN_CLASSIFIER(TokenType.WHITESPACE.atom),
	TOKEN_TYPE_ORDINAL_KEY(StaticInit.tokenTypeOrdinalKey),
	SET_ONCE_PROPERTY_KEY(SpecialAtom.SET_ONCE_PROPERTY_KEY.atom)

	;

	val atom: AvailObject = givenAtom as AvailObject

	init
	{
		assert(atom.isAtom && atom.isAtomSpecial)
	}

	companion object
	{
		/**
		 * Answer the special object ([AvailObject]) with the specified ordinal.
		 *
		 * @param ordinal
		 *   The [special object][AvailObject] with the specified ordinal.
		 * @return
		 *   An [AvailObject].
		 */
		fun specialAtom(ordinal: Int): AvailObject = entries[ordinal].atom
	}
}
