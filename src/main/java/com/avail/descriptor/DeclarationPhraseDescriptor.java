/*
 * DeclarationNodeDescriptor.java
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

import com.avail.annotations.AvailMethod;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.descriptor.parsing.A_Phrase;
import com.avail.descriptor.parsing.PhraseDescriptor;
import com.avail.descriptor.tuples.A_String;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind.*;
import static com.avail.descriptor.DeclarationPhraseDescriptor.ObjectSlots.*;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;

/**
 * My instances represent variable and constant statements.  There are several
 * {@linkplain DeclarationKind kinds of declarations}, some with initializing
 * expressions and some with type expressions.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class DeclarationPhraseDescriptor
extends PhraseDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain TokenDescriptor token} containing the name of the
		 * entity being declared.
		 */
		TOKEN,

		/**
		 * The {@linkplain TypeDescriptor type} of the variable being declared.
		 */
		DECLARED_TYPE,

		/**
		 * The {@link PhraseDescriptor expression} that produced the type for
		 * the entity being declared, or {@link NilDescriptor#nil nil} if
		 * there was no such expression.
		 */
		TYPE_EXPRESSION,

		/**
		 * The optional {@linkplain PhraseDescriptor initialization
		 * expression}, or {@link NilDescriptor#nil nil} otherwise. Not
		 * applicable to all kinds of declarations.
		 */
		INITIALIZATION_EXPRESSION,

		/**
		 * The {@link AvailObject} held directly by this declaration. It can be
		 * either a module constant value or a module variable.
		 */
		LITERAL_OBJECT
	}

	/**
	 * These are the kinds of arguments, variables, constants, and labels that
	 * can be declared.  There are also optional initializing expressions, fixed
	 * values (for module constants), and fixed variable objects (for module
	 * variables).
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public enum DeclarationKind implements IntegerEnumSlotDescriptionEnum
	{
		/**
		 * This is an argument to a block.
		 */
		ARGUMENT("argument", false, false, PhraseKind.ARGUMENT_PHRASE)
		{
			@Override
			public void emitVariableValueForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLocalOrOuter(tokens, declarationNode);
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final IdentityHashMap<A_BasicObject, Void> recursionMap,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" : ");
				printTypePartOf(object, builder, recursionMap, indent + 1);
			}
		},

		/**
		 * This is a label declaration at the start of a block.
		 */
		LABEL("label", false, false, PhraseKind.LABEL_PHRASE)
		{
			/**
			 * Let the code generator know that the label occurs at the
			 * current code position.
			 */
			@Override
			public void emitEffectForOn (
				final A_Tuple tokens,
				final A_Phrase object,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitLabelDeclaration(object);
			}

			@Override
			public void emitVariableValueForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLocalOrOuter(tokens, declarationNode);
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final IdentityHashMap<A_BasicObject, Void> recursionMap,
				final int indent)
			{
				builder.append('$');
				builder.append(object.token().string().asNativeString());
				builder.append(" : ");
				final A_BasicObject typeExpression = object.typeExpression();
				if (!typeExpression.equalsNil())
				{
					typeExpression.printOnAvoidingIndent(
						builder, recursionMap, indent + 1);
				}
				else
				{
					// Output the continuation type's return type, since that's
					// what get specified syntactically.
					final A_Type functionType =
						object.declaredType().functionType();
					functionType.returnType().printOnAvoidingIndent(
						builder, recursionMap, indent + 1);
				}
			}
		},

		/**
		 * This is a local variable, declared within a block.
		 */
		LOCAL_VARIABLE(
			"local variable", true, false, PhraseKind.LOCAL_VARIABLE_PHRASE)
		{
			@Override
			public void emitEffectForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				final A_Phrase expr =
					declarationNode.initializationExpression();
				if (!expr.equalsNil())
				{
					expr.emitValueOn(codeGenerator);
					codeGenerator.emitSetLocalOrOuter(tokens, declarationNode);
				}
			}

			@Override
			public void emitVariableAssignmentForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitSetLocalOrOuter(tokens, declarationNode);
			}

			@Override
			public void emitVariableReferenceForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLocalOrOuter(tokens, declarationNode);
			}

			@Override
			public void emitVariableValueForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitGetLocalOrOuter(tokens, declarationNode);
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final IdentityHashMap<A_BasicObject, Void> recursionMap,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" : ");
				printTypePartOf(object, builder, recursionMap, indent + 1);
				if (!object.initializationExpression().equalsNil())
				{
					builder.append(" := ");
					object.initializationExpression().printOnAvoidingIndent(
						builder, recursionMap, indent + 1);
				}
			}
		},

		/**
		 * This is a local constant, declared within a block.
		 */
		LOCAL_CONSTANT(
			"local constant", false, false, PhraseKind.LOCAL_CONSTANT_PHRASE)
		{
			@Override
			public void emitEffectForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				declarationNode.initializationExpression()
					.emitValueOn(codeGenerator);
				codeGenerator.emitSetLocalFrameSlot(tokens, declarationNode);
			}

			@Override
			public void emitVariableValueForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLocalOrOuter(tokens, declarationNode);
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final IdentityHashMap<A_BasicObject, Void> recursionMap,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" ::= ");
				object.initializationExpression().printOnAvoidingIndent(
					builder, recursionMap, indent + 1);
			}
		},

		/**
		 * This is a variable declared at the outermost (module) scope.
		 */
		MODULE_VARIABLE(
			"module variable", true, true, PhraseKind.MODULE_VARIABLE_PHRASE)
		{
			@Override
			public void emitVariableAssignmentForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitSetLiteral(
					tokens, declarationNode.literalObject());
			}

			@Override
			public void emitVariableReferenceForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLiteral(
					tokens, declarationNode.literalObject());
			}

			@Override
			public void emitVariableValueForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitGetLiteral(
					// Technically, that's the declaration, not the use, but it
					// should do for now.
					declarationNode.tokens(), declarationNode.literalObject());
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final IdentityHashMap<A_BasicObject, Void> recursionMap,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" : ");
				printTypePartOf(object, builder, recursionMap, indent + 1);
				if (!object.initializationExpression().equalsNil())
				{
					builder.append(" := ");
					object.initializationExpression().printOnAvoidingIndent(
						builder, recursionMap, indent + 1);
				}
			}
		},

		/**
		 * This is a constant declared at the outermost (module) scope.
		 */
		MODULE_CONSTANT(
			"module constant", false, true, PhraseKind.MODULE_CONSTANT_PHRASE)
		{
			@Override
			public void emitVariableValueForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitGetLiteral(
					tokens, declarationNode.literalObject());
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final IdentityHashMap<A_BasicObject, Void> recursionMap,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" ::= ");
				object.initializationExpression().printOnAvoidingIndent(
					builder, recursionMap, indent + 1);
			}
		},

		/**
		 * This is a local constant, declared within a block.
		 */
		PRIMITIVE_FAILURE_REASON(
			"primitive failure reason",
			true,
			false,
			PhraseKind.PRIMITIVE_FAILURE_REASON_PHRASE)
		{
			@Override
			public void emitVariableValueForOn (
				final A_Tuple tokens,
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitGetLocalOrOuter(tokens, declarationNode);
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final IdentityHashMap<A_BasicObject, Void> recursionMap,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" : ");
				printTypePartOf(object, builder, recursionMap, indent + 1);
			}
		};


		/** Whether this entity can be modified. */
		private final boolean isVariable;

		/** Whether this entity occurs at the module scope. */
		private final boolean isModuleScoped;

		/**
		 * The instance of the enumeration {@link PhraseKind} that
		 * is associated with this kind of declaration.
		 */
		private final PhraseKind kindEnumeration;

		/** A Java {@link String} describing this kind of declaration */
		private final String nativeKindName;

		/**
		 * An Avail {@link StringDescriptor string} describing this kind of
		 * declaration.
		 */
		private final A_String kindName;

		/**
		 * Construct one of the enumeration values.
		 *
		 * @param nativeKindName
		 *        A Java {@link String} describing this kind of declaration.
		 * @param isVariable
		 *        Whether it's legal to assign to this entity.
		 * @param isModuleScoped
		 *        Whether declarations of this kind have module scope.
		 * @param kindEnumeration
		 *        The enumeration instance of {@link Types} that is associated
		 *        with this kind of declaration.
		 */
		DeclarationKind (
			final String nativeKindName,
			final boolean isVariable,
			final boolean isModuleScoped,
			final PhraseKind kindEnumeration)
		{
			this.nativeKindName = nativeKindName;
			this.isVariable = isVariable;
			this.isModuleScoped = isModuleScoped;
			this.kindEnumeration = kindEnumeration;
			this.kindName = stringFrom(nativeKindName).makeShared();
		}

		/**
		 * Stash a copy of the array of all {@link DeclarationKind} enum values.
		 */
		private static final DeclarationKind[] all = values();

		/**
		 * Answer the previously stashed copy of the array of all {@link
		 * DeclarationKind} enum values.
		 *
		 * @param ordinal
		 *        The ordinal of the declaration kind to look up.
		 * @return The requested {@code DeclarationKind}.
		 */
		public static DeclarationKind lookup (final int ordinal)
		{
			return all[ordinal];
		}

		/**
		 * Return whether this entity can be written.
		 *
		 * @return Whether this entity is assignable.
		 */
		public final boolean isVariable ()
		{
			return isVariable;
		}

		/**
		 * Return whether this entity is defined at the module scope.
		 *
		 * @return Whether this entity is scoped at the module level.
		 */
		public final boolean isModuleScoped ()
		{
			return isModuleScoped;
		}

		/**
		 * Return the instance of the enumeration {@link PhraseKind} that is
		 * associated with this kind of declaration.
		 *
		 * @return The associated {@code PhraseKind} enumeration value.
		 */
		public final PhraseKind phraseKind ()
		{
			return kindEnumeration;
		}

		/**
		 * Answer a Java {@link String} describing this kind of declaration.
		 *
		 * @return A Java String.
		 */
		public final String nativeKindName ()
		{
			return nativeKindName;
		}

		/**
		 * Return an Avail {@link StringDescriptor string} describing this kind
		 * of declaration.
		 *
		 * @return The associated {@code PhraseKind} enumeration value.
		 */
		public final A_String kindName ()
		{
			return kindName;
		}

		/**
		 * Emit an assignment to this variable.
		 *
		 * @param tokens
		 *        The {@link A_Tuple} of {@link A_Token}s associated with this
		 *        call.
		 * @param declarationNode
		 *        The declaration that has this declarationKind.
		 * @param codeGenerator
		 *        Where to generate the assignment.
		 */
		public void emitVariableAssignmentForOn (
			final A_Tuple tokens,
			final A_Phrase declarationNode,
			final AvailCodeGenerator codeGenerator)
		{
			error("Cannot assign to this " + name());
		}

		/**
		 * Emit a reference to this variable.
		 *
		 * @param tokens
		 *        The {@link A_Tuple} of {@link A_Token}s associated with this
		 *        call.
		 * @param declarationNode
		 *        The declaration that has this declarationKind.
		 * @param codeGenerator
		 *        Where to emit the reference to this variable.
		 */
		public void emitVariableReferenceForOn (
			final A_Tuple tokens,
			final A_Phrase declarationNode,
			final AvailCodeGenerator codeGenerator)
		{
			error("Cannot take a reference to this " + name());
		}

		/**
		 * Emit a use of this variable.
		 *
		 * @param tokens
		 *        The {@link A_Tuple} of {@link A_Token}s associated with this
		 *        call.
		 * @param declarationNode
		 *        The declaration that has this declarationKind.
		 * @param codeGenerator
		 *        Where to emit the use of this variable.
		 */
		public void emitVariableValueForOn (
			final A_Tuple tokens,
			final A_Phrase declarationNode,
			final AvailCodeGenerator codeGenerator)
		{
			error("Cannot extract the value of this " + name());
		}

		/**
		 * If this is an ordinary declaration then it was handled on a separate
		 * pass.  Do nothing by default.
		 *
		 * @param tokens
		 *        The {@link A_Tuple} of {@link A_Token}s associated with this
		 *        call.
		 * @param object
		 *        The declaration phrase.
		 * @param codeGenerator
		 *        Where to emit the declaration.
		 */
		public void emitEffectForOn (
			final A_Tuple tokens,
			final A_Phrase object,
			final AvailCodeGenerator codeGenerator)
		{
			// Declarations emit no instructions.
		}

		/**
		 * Print a declaration of this kind.
		 *
		 * @param object
		 *        The declaration.
		 * @param builder
		 *        Where to print.
		 * @param recursionMap
		 *        An {@link IdentityHashMap} of parent objects that are
		 *        printing.
		 * @param indent
		 *        The indentation depth.
		 */
		public abstract void print (
			final A_Phrase object,
			final StringBuilder builder,
			final IdentityHashMap<A_BasicObject, Void> recursionMap,
			final int indent);

		/**
		 * Print the part of the given declaration of this kind which indicates
		 * the content type of the entity that is being declared.
		 *
		 * @param object
		 *        The declaration.
		 * @param builder
		 *        Where to print.
		 * @param recursionMap
		 *        An {@link IdentityHashMap} of parent objects that are
		 *        printing.
		 * @param indent
		 *        The indentation depth.
		 */
		static void printTypePartOf (
			final A_Phrase object,
			final StringBuilder builder,
			final IdentityHashMap<A_BasicObject, Void> recursionMap,
			final int indent)
		{
			final A_BasicObject typeExpression = object.typeExpression();
			if (!typeExpression.equalsNil())
			{
				typeExpression.printOnAvoidingIndent(
					builder, recursionMap, indent);
			}
			else
			{
				object.declaredType().printOnAvoidingIndent(
					builder, recursionMap, indent);
			}
		}
	}

	/**
	 * Getter for field name.
	 */
	@Override @AvailMethod
	protected A_Token o_Token (
		final AvailObject object)
	{
		return object.slot(TOKEN);
	}

	/**
	 * Getter for field declaredType.
	 */
	@Override @AvailMethod
	protected A_Type o_DeclaredType (
		final AvailObject object)
	{
		return object.slot(DECLARED_TYPE);
	}

	/**
	 * Getter for field typeExpression.
	 */
	@Override @AvailMethod
	protected A_Phrase o_TypeExpression (
		final AvailObject object)
	{
		return object.slot(TYPE_EXPRESSION);
	}

	/**
	 * Getter for field initializationExpression.
	 */
	@Override @AvailMethod
	protected AvailObject o_InitializationExpression (
		final AvailObject object)
	{
		return object.slot(INITIALIZATION_EXPRESSION);
	}

	/**
	 * Getter for field literalObject.
	 */
	@Override @AvailMethod
	protected AvailObject o_LiteralObject (
		final AvailObject object)
	{
		return object.slot(LITERAL_OBJECT);
	}

	/**
	 * Getter for field declarationKind.
	 */
	@Override @AvailMethod
	protected DeclarationKind o_DeclarationKind (
		final AvailObject object)
	{
		return declarationKind;
	}

	@Override @AvailMethod
	protected A_Type o_ExpressionType (final AvailObject object)
	{
		return TOP.o();
	}

	/**
	 * This is a declaration, so it was handled on a separate pass.  Do nothing.
	 *
	 * @param codeGenerator Where to emit the declaration.
	 */
	@Override @AvailMethod
	protected void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.declarationKind().emitEffectForOn(
			object.tokens(), object, codeGenerator);
	}

	/**
	 * This is a declaration, so it shouldn't generally produce a value.
	 */
	@Override @AvailMethod
	protected void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.emitEffectOn(codeGenerator);
		codeGenerator.emitPushLiteral(emptyTuple(), nil);
	}

	@Override @AvailMethod
	protected int o_Hash (final AvailObject object)
	{
		return
			((((object.token().hash() * multiplier
				+ object.typeExpression().hash()) * multiplier
				+ object.declaredType().hash()) * multiplier
				+ object.initializationExpression().hash()) * multiplier
				+ object.literalObject().hash()) * multiplier
				+ object.declarationKind().ordinal()
			^ 0x4C27EB37;
	}

	@Override @AvailMethod
	protected boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return object.sameAddressAs(aPhrase.traversed());
	}

	@Override @AvailMethod
	protected void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		final A_Phrase typeExpression = object.typeExpression();
		if (!typeExpression.equalsNil())
		{
			object.setSlot(
				TYPE_EXPRESSION, transformer.valueNotNull(typeExpression));
		}
		final A_Phrase expression = object.initializationExpression();
		if (!expression.equalsNil())
		{
			object.setSlot(
				INITIALIZATION_EXPRESSION,
				transformer.valueNotNull(expression));
		}
	}

	@Override @AvailMethod
	protected void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		final AvailObject expression = object.initializationExpression();
		if (!expression.equalsNil())
		{
			action.value(expression);
		}
	}

	@Override
	protected void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		continuation.value(object);
	}

	@SuppressWarnings("EmptyMethod")
	@Override @AvailMethod
	protected void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	@Override
	protected PhraseKind o_PhraseKind (
		final AvailObject object)
	{
		return object.declarationKind().phraseKind();
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.DECLARATION_PHRASE;
	}

	@Override
	protected A_Tuple o_Tokens (final AvailObject object)
	{
		return tuple(object.slot(TOKEN));
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write(object.declarationKind().kindName() + " phrase");
		writer.write("token");
		object.slot(TOKEN).writeTo(writer);
		writer.write("declared type");
		object.slot(DECLARED_TYPE).writeTo(writer);
		final AvailObject typeExpression = object.slot(TYPE_EXPRESSION);
		if (!typeExpression.equalsNil())
		{
			writer.write("type expression");
			typeExpression.writeTo(writer);
		}
		final AvailObject initializationExpression =
			object.slot(INITIALIZATION_EXPRESSION);
		if (!initializationExpression.equalsNil())
		{
			writer.write("initialization expression");
			initializationExpression.writeTo(writer);
		}
		final AvailObject literal = object.slot(LITERAL_OBJECT);
		if (!literal.equalsNil())
		{
			writer.write("literal");
			literal.writeTo(writer);
		}
		writer.endObject();
	}

	@Override
	protected String o_NameForDebugger (final AvailObject object)
	{
		return super.o_NameForDebugger(object) + ": " + object.phraseKind();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		object.declarationKind().print(object, builder, recursionMap, indent);
	}

	/**
	 * Construct a declaration phrase of some {@linkplain DeclarationKind kind}.
	 *
	 * @param declarationKind
	 *        The {@linkplain DeclarationKind kind} of {@linkplain
	 *        DeclarationPhraseDescriptor declaration} to create.
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the entity being declared.
	 * @param declaredType
	 *        The {@linkplain TypeDescriptor type} of the entity being declared.
	 * @param typeExpression
	 *        The {@link PhraseDescriptor expression} that produced the type
	 *        for the entity being declared, or {@link NilDescriptor#nil nil}
	 *        if there was no such expression.
	 * @param initializationExpression
	 *        An {@linkplain PhraseDescriptor expression} used for
	 *        initializing the entity being declared, or {@linkplain
	 *        NilDescriptor#nil nil} if none.
	 * @param literalObject
	 *        An {@link AvailObject} that is the actual variable or constant
	 *        being defined, or {@linkplain NilDescriptor#nil nil} if none.
	 * @return The new declaration phrase.
	 */
	public static A_Phrase newDeclaration (
		final DeclarationKind declarationKind,
		final A_Token token,
		final A_Type declaredType,
		final A_Phrase typeExpression,
		final A_Phrase initializationExpression,
		final A_BasicObject literalObject)
	{
		assert declaredType.isType();
		assert token.isInstanceOf(Types.TOKEN.o());
		assert initializationExpression.equalsNil()
			|| initializationExpression.isInstanceOfKind(
				PhraseKind.EXPRESSION_PHRASE.create(Types.ANY.o()));
		assert literalObject.equalsNil()
			|| declarationKind == MODULE_VARIABLE
			|| declarationKind == MODULE_CONSTANT;

		final AvailObject declaration =
			mutables[declarationKind.ordinal()].create();
		declaration.setSlot(TOKEN, token);
		declaration.setSlot(DECLARED_TYPE, declaredType);
		declaration.setSlot(TYPE_EXPRESSION, typeExpression);
		declaration.setSlot(
			INITIALIZATION_EXPRESSION, initializationExpression);
		declaration.setSlot(LITERAL_OBJECT, literalObject);
		declaration.makeShared();
		return declaration;
	}

	/**
	 * Construct a new declaration of a block or method {@linkplain
	 * DeclarationKind#ARGUMENT argument}.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the entity being declared.
	 * @param declaredType
	 *        The {@linkplain TypeDescriptor type} of the entity being declared.
	 * @param typeExpression
	 *        The {@link PhraseDescriptor expression} that produced the type
	 *        for the entity being declared, or {@link NilDescriptor#nil nil}
	 *        if there was no such expression.
	 * @return The argument declaration.
	 */
	public static A_Phrase newArgument (
		final A_Token token,
		final A_Type declaredType,
		final A_Phrase typeExpression)
	{
		return newDeclaration(
			ARGUMENT,
			token,
			declaredType,
			typeExpression,
			nil,
			nil);
	}

	/**
	 * Construct a new declaration of a {@linkplain
	 * DeclarationKind#LOCAL_VARIABLE local variable}.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the local variable being declared.
	 * @param declaredType
	 *        The inner {@linkplain TypeDescriptor type} of the local variable
	 *        being declared.
	 * @param typeExpression
	 *        The {@link PhraseDescriptor expression} that produced the type
	 *        for the entity being declared, or {@link NilDescriptor#nil nil}
	 *        if there was no such expression.
	 * @param initializationExpression
	 *        An {@linkplain PhraseDescriptor expression} used for
	 *        initializing the local variable, or {@linkplain
	 *        NilDescriptor#nil nil} if none.
	 * @return The new local variable declaration.
	 */
	public static A_Phrase newVariable (
		final A_Token token,
		final A_Type declaredType,
		final A_Phrase typeExpression,
		final A_Phrase initializationExpression)
	{
		return newDeclaration(
			LOCAL_VARIABLE,
			token,
			declaredType,
			typeExpression,
			initializationExpression,
			nil);
	}

	/**
	 * Construct a new declaration of a {@linkplain
	 * DeclarationKind#LOCAL_CONSTANT local constant}.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the local constant being declared.
	 * @param initializationExpression
	 *        An {@linkplain PhraseDescriptor expression} used to
	 *        provide the value of the local constant.
	 * @return The new local constant declaration.
	 */
	public static A_Phrase newConstant (
		final A_Token token,
		final A_Phrase initializationExpression)
	{
		return newDeclaration(
			LOCAL_CONSTANT,
			token,
			initializationExpression.expressionType(),
			nil,
			initializationExpression,
			nil);
	}

	/**
	 * Construct a new declaration of a {@linkplain
	 * DeclarationKind#PRIMITIVE_FAILURE_REASON primitive failure variable}.
	 * This is set up automatically when a primitive fails, and the statements
	 * of the block should not be allowed to write to it.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the local constant being declared.
	 * @param typeExpression
	 *        The {@link PhraseDescriptor expression} that produced the type
	 *        for the entity being declared, or {@link NilDescriptor#nil nil}
	 *        if there was no such expression.
	 * @param type
	 *        The {@linkplain TypeDescriptor type} of the primitive failure
	 *        variable being declared.
	 * @return The new local constant declaration.
	 */
	public static A_Phrase newPrimitiveFailureVariable (
		final A_Token token,
		final A_Phrase typeExpression,
		final A_Type type)
	{
		return newDeclaration(
			PRIMITIVE_FAILURE_REASON,
			token,
			type,
			typeExpression,
			nil,
			nil);
	}

	/**
	 * Construct a new declaration of a {@linkplain DeclarationKind#LABEL
	 * label}.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the label being declared.
	 * @param returnTypeExpression
	 *        The {@link PhraseDescriptor expression} that produced the type
	 *        for the entity being declared, or {@link NilDescriptor#nil nil}
	 *        if there was no such expression.  Note that this expression
	 *        produced the return type of the continuation type, not the
	 *        continuation type itself.
	 * @param declaredType
	 *        The {@linkplain TypeDescriptor type} of the label being declared,
	 *        which must be a {@linkplain ContinuationTypeDescriptor
	 *        continuation type} whose contained {@linkplain
	 *        FunctionTypeDescriptor function type} agrees with the block in which
	 *        the label occurs.
	 * @return The new label declaration.
	 */
	public static A_Phrase newLabel (
		final A_Token token,
		final A_Phrase returnTypeExpression,
		final A_Type declaredType)
	{
		return newDeclaration(
			LABEL,
			token,
			declaredType,
			returnTypeExpression,
			nil,
			nil);
	}

	/**
	 * Construct a new declaration of a {@linkplain
	 * DeclarationKind#MODULE_VARIABLE module variable} with or without an
	 * initialization expression.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the module variable being declared.
	 * @param literalVariable
	 *        The actual {@linkplain VariableDescriptor variable} to be used
	 *        as a module variable.
	 * @param typeExpression
	 *        The {@link PhraseDescriptor expression} that produced the type
	 *        for the entity being declared, or {@link NilDescriptor#nil nil}
	 *        if there was no such expression.
	 * @param initializationExpression
	 *        The expression (or {@linkplain NilDescriptor#nil nil}) used to
	 *        initialize this module variable.
	 * @return The new module variable declaration.
	 */
	public static A_Phrase newModuleVariable (
		final A_Token token,
		final A_BasicObject literalVariable,
		final A_Phrase typeExpression,
		final A_Phrase initializationExpression)
	{
		return newDeclaration(
			MODULE_VARIABLE,
			token,
			literalVariable.kind().readType(),
			typeExpression,
			initializationExpression,
			literalVariable);
	}

	/**
	 * Construct a new declaration of a {@linkplain
	 * DeclarationKind#MODULE_CONSTANT module constant}.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the module constant being declared.
	 * @param literalVariable
	 *        An actual {@link VariableDescriptor variable} that the new module
	 *        constant uses to hold its value.
	 * @param initializationExpression
	 *        The expression used to initialize this module constant.
	 * @return The new module constant declaration.
	 */
	public static A_Phrase newModuleConstant (
		final A_Token token,
		final A_BasicObject literalVariable,
		final A_Phrase initializationExpression)
	{
		return newDeclaration(
			MODULE_CONSTANT,
			token,
			literalVariable.kind().readType(),
			nil,
			initializationExpression,
			literalVariable);
	}

	/**
	 * Construct a new {@code DeclarationPhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param declarationKind
	 *        The declaration's {@link DeclarationKind}.
	 */
	public DeclarationPhraseDescriptor (
		final Mutability mutability,
		final DeclarationKind declarationKind)
	{
		super(
			mutability,
			declarationKind.phraseKind().typeTag,
			ObjectSlots.class,
			null);
		this.declarationKind = declarationKind;
	}

	/** The kind of declaration using this descriptor. */
	private final DeclarationKind declarationKind;

	/** The mutable {@link DeclarationPhraseDescriptor}s. */
	private static final DeclarationPhraseDescriptor[] mutables =
		new DeclarationPhraseDescriptor[DeclarationKind.values().length];

	/** The shared {@link DeclarationPhraseDescriptor}s. */
	private static final DeclarationPhraseDescriptor[] shareds =
		new DeclarationPhraseDescriptor[DeclarationKind.values().length];

	static
	{
		for (final DeclarationKind kind : DeclarationKind.values())
		{
			mutables[kind.ordinal()] = new DeclarationPhraseDescriptor(
				Mutability.MUTABLE, kind);
			shareds[kind.ordinal()] = new DeclarationPhraseDescriptor(
				Mutability.SHARED, kind);
		}
	}

	@Override
	protected DeclarationPhraseDescriptor mutable ()
	{
		return mutables[declarationKind.ordinal()];
	}

	@Override
	protected DeclarationPhraseDescriptor shared ()
	{
		return shareds[declarationKind.ordinal()];
	}
}
