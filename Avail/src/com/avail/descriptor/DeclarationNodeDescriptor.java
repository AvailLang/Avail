/**
 * DeclarationNodeDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.AvailObject.*;
import static com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind.*;
import static com.avail.descriptor.DeclarationNodeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.DeclarationNodeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.utility.evaluation.*;

/**
 * My instances represent variable and constant statements.  There are several
 * {@linkplain DeclarationKind kinds of declarations}, some with initializing
 * expressions and some with type expressions.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class DeclarationNodeDescriptor
extends ParseNodeDescriptor
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
		 * The optional {@linkplain ParseNodeDescriptor initialization
		 * expression}, or {@link NilDescriptor#nil() nil} otherwise. Not
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
	 * My slots of type {@linkplain Integer int}.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * Flags encoded as an {@code int}.
		 */
		@EnumField(describedBy=DeclarationKind.class)
		DECLARATION_KIND
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
		ARGUMENT("argument", false, false, ParseNodeKind.ARGUMENT_NODE)
		{
			@Override
			public void emitVariableValueForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLocalOrOuter(declarationNode);
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final List<A_BasicObject> recursionList,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" : ");
				object.declaredType().printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
		},

		/**
		 * This is a label declaration at the start of a block.
		 */
		LABEL("label", false, false, ParseNodeKind.LABEL_NODE)
		{
			/**
			 * Let the code generator know that the label occurs at the
			 * current code position.
			 */
			@Override
			public void emitEffectForOn (
				final A_Phrase object,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitLabelDeclaration(object);
			}

			@Override
			public void emitVariableValueForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLocalOrOuter(declarationNode);
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final List<A_BasicObject> recursionList,
				final int indent)
			{
				builder.append('$');
				builder.append(object.token().string().asNativeString());
				builder.append(':');
				final A_Type functionType =
					object.declaredType().functionType();
				functionType.returnType().printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
		},

		/**
		 * This is a local variable, declared within a block.
		 */
		LOCAL_VARIABLE(
			"local variable", true, false, ParseNodeKind.LOCAL_VARIABLE_NODE)
		{
			@Override
			public void emitEffectForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				final A_Phrase expr =
					declarationNode.initializationExpression();
				if (!expr.equalsNil())
				{
					expr.emitValueOn(codeGenerator);
					codeGenerator.emitSetLocalOrOuter(declarationNode);
				}
			}

			@Override
			public void emitVariableAssignmentForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitSetLocalOrOuter(declarationNode);
			}

			@Override
			public void emitVariableReferenceForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLocalOrOuter(declarationNode);
			}

			@Override
			public void emitVariableValueForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitGetLocalOrOuter(declarationNode);
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final List<A_BasicObject> recursionList,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" : ");
				object.declaredType().printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
				if (!object.initializationExpression().equalsNil())
				{
					builder.append(" := ");
					object.initializationExpression().printOnAvoidingIndent(
						builder,
						recursionList,
						indent + 1);
				}
			}
		},

		/**
		 * This is a local constant, declared within a block.
		 */
		LOCAL_CONSTANT(
			"local constant", false, false, ParseNodeKind.LOCAL_CONSTANT_NODE)
		{
			@Override
			public void emitEffectForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				declarationNode.initializationExpression()
					.emitValueOn(codeGenerator);
				codeGenerator.emitSetLocalOrOuter(declarationNode);
			}

			@Override
			public void emitVariableValueForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitGetLocalOrOuter(declarationNode);
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final List<A_BasicObject> recursionList,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" ::= ");
				object.initializationExpression().printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
		},

		/**
		 * This is a variable declared at the outermost (module) scope.
		 */
		MODULE_VARIABLE(
			"module variable", true, true, ParseNodeKind.MODULE_VARIABLE_NODE)
		{
			@Override
			public void emitVariableAssignmentForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitSetLiteral(declarationNode.literalObject());
			}

			@Override
			public void emitVariableReferenceForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLiteral(declarationNode.literalObject());
			}

			@Override
			public void emitVariableValueForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitGetLiteral(declarationNode.literalObject());
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final List<A_BasicObject> recursionList,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" : ");
				object.declaredType().printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
				if (!object.initializationExpression().equalsNil())
				{
					builder.append(" := ");
					object.initializationExpression().printOnAvoidingIndent(
						builder,
						recursionList,
						indent + 1);
				}
			}
		},

		/**
		 * This is a constant declared at the outermost (module) scope.
		 */
		MODULE_CONSTANT(
			"module constant", false, true, ParseNodeKind.MODULE_CONSTANT_NODE)
		{
			@Override
			public void emitVariableValueForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitGetLiteral(declarationNode.literalObject());
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final List<A_BasicObject> recursionList,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" ::= ");
				object.initializationExpression().printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
		},

		/**
		 * This is a local constant, declared within a block.
		 */
		PRIMITIVE_FAILURE_REASON(
			"primitive failure reason",
			false,
			false,
			ParseNodeKind.PRIMITIVE_FAILURE_REASON_NODE)
		{
			@Override
			public void emitEffectForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				// Handled automatically by the primitive attempt.
			}

			@Override
			public void emitVariableValueForOn (
				final A_Phrase declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitGetLocalOrOuter(declarationNode);
			}

			@Override
			public void print (
				final A_Phrase object,
				final StringBuilder builder,
				final List<A_BasicObject> recursionList,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" : ");
				object.declaredType().printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
		};


		/** Whether this entity can be modified. */
		private final boolean isVariable;

		/** Whether this entity occurs at the module scope. */
		private final boolean isModuleScoped;

		/**
		 * The instance of the enumeration {@link ParseNodeKind} that
		 * is associated with this kind of declaration.
		 */
		private final ParseNodeKind kindEnumeration;

		/** A Java {@link String} describing this kind of declaration */
		private final String nativeKindName;

		/**
		 * An Avail {@link StringDescriptor string} describing this kind of
		 * declaration.
		 */
		private final A_String kindName;

		/**
		 * Construct a {@link DeclarationKind}.  Can only be invoked implicitly
		 * when constructing the enumeration values.
		 *
		 * @param nativeKindName
		 *        A Java {@link String} describing this kind of declaration.
		 * @param isVariable
		 *        Whether it's legal to assign to this entity.
		 * @param isModuleScoped
		 *        Whether declarations of this kind have module scope.
		 * @param kindEnumeration
		 *        The enumeration instance of {@link TypeDescriptor.Types} that
		 *        is associated with this kind of declaration.
		 */
		private DeclarationKind (
			final String nativeKindName,
			final boolean isVariable,
			final boolean isModuleScoped,
			final ParseNodeKind kindEnumeration)
		{
			this.nativeKindName = nativeKindName;
			this.isVariable = isVariable;
			this.isModuleScoped = isModuleScoped;
			this.kindEnumeration = kindEnumeration;
			this.kindName = StringDescriptor.from(nativeKindName).makeShared();
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
		 * Return the instance of the enumeration {@link ParseNodeKind} that is
		 * associated with this kind of declaration.
		 *
		 * @return The associated {@code ParseNodeKind} enumeration value.
		 */
		public final ParseNodeKind parseNodeKind ()
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
		 * @return The associated {@code ParseNodeKind} enumeration value.
		 */
		public final A_String kindName ()
		{
			return kindName;
		}

		/**
		 * Return the {@linkplain PrimitiveTypeDescriptor primitive type}
		 * associated with this kind of entity.
		 *
		 * @param expressionType
		 *            The type of object produced by some parse node's
		 *            expression.
		 * @return The Avail {@linkplain TypeDescriptor type} associated with
		 *         this kind of entity.
		 */
		public A_BasicObject parseNodeTypeFor (
			final AvailObject expressionType)
		{
			return kindEnumeration.create(expressionType);
		}


		/**
		 * Emit an assignment to this variable.
		 *
		 * @param declarationNode The declaration that has this declarationKind.
		 * @param codeGenerator Where to generate the assignment.
		 */
		public void emitVariableAssignmentForOn (
			final A_Phrase declarationNode,
			final AvailCodeGenerator codeGenerator)
		{
			error("Cannot assign to this " + name());
		}

		/**
		 * Emit a reference to this variable.
		 *
		 * @param declarationNode The declaration that has this declarationKind.
		 * @param codeGenerator Where to emit the reference to this variable.
		 */
		public void emitVariableReferenceForOn (
			final A_Phrase declarationNode,
			final AvailCodeGenerator codeGenerator)
		{
			error("Cannot take a reference to this " + name());
		}

		/**
		 * Emit a use of this variable.
		 *
		 * @param declarationNode The declaration that has this declarationKind.
		 * @param codeGenerator Where to emit the use of this variable.
		 */
		public void emitVariableValueForOn (
			final A_Phrase declarationNode,
			final AvailCodeGenerator codeGenerator)
		{
			error("Cannot extract the value of this " + name());
		}

		/**
		 * If this is an ordinary declaration then it was handled on a separate
		 * pass.  Do nothing by default.
		 *
		 * @param object The declaration node.
		 * @param codeGenerator Where to emit the declaration.
		 */
		public void emitEffectForOn (
			final A_Phrase object,
			final AvailCodeGenerator codeGenerator)
		{
			return;
		}

		/**
		 * Print a declaration of this kind.
		 *
		 * @param object The declaration.
		 * @param builder Where to print.
		 * @param recursionList A list of parent objects that are printing.
		 * @param indent The indentation depth.
		 */
		public abstract void print (
			final A_Phrase object,
			final StringBuilder builder,
			final List<A_BasicObject> recursionList,
			final int indent);
	}

	/**
	 * Getter for field name.
	 */
	@Override @AvailMethod
	A_Token o_Token (
		final AvailObject object)
	{
		return object.slot(TOKEN);
	}

	/**
	 * Getter for field declaredType.
	 */
	@Override @AvailMethod
	AvailObject o_DeclaredType (
		final AvailObject object)
	{
		return object.slot(DECLARED_TYPE);
	}

	/**
	 * Getter for field initializationExpression.
	 */
	@Override @AvailMethod
	AvailObject o_InitializationExpression (
		final AvailObject object)
	{
		return object.slot(INITIALIZATION_EXPRESSION);
	}

	/**
	 * Getter for field literalObject.
	 */
	@Override @AvailMethod
	AvailObject o_LiteralObject (
		final AvailObject object)
	{
		return object.slot(LITERAL_OBJECT);
	}

	/**
	 * Getter for field declarationKind.
	 */
	@Override @AvailMethod
	DeclarationKind o_DeclarationKind (
		final AvailObject object)
	{
		return DeclarationKind.values()[object.slot(DECLARATION_KIND)];
	}


	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		return TOP.o();
	}


	/**
	 * This is a declaration, so it was handled on a separate pass.  Do nothing.
	 *
	 * @param codeGenerator Where to emit the declaration.
	 */
	@Override @AvailMethod
	void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.declarationKind().emitEffectForOn(object, codeGenerator);
	}


	/**
	 * This is a declaration, so it shouldn't generally produce a value.
	 */
	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.emitEffectOn(codeGenerator);
		codeGenerator.emitPushLiteral(NilDescriptor.nil());
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			(((object.token().hash() * multiplier
				+ object.declaredType().hash()) * multiplier
				+ object.initializationExpression().hash()) * multiplier
				+ object.literalObject().hash()) * multiplier
				+ object.declarationKind().ordinal()
			^ 0x4C27EB37;
	}

	@Override @AvailMethod
	boolean o_EqualsParseNode (
		final AvailObject object,
		final A_Phrase aParseNode)
	{
		return object.sameAddressAs(aParseNode.traversed());
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> aBlock)
	{
		A_Phrase expression = object.initializationExpression();
		if (!expression.equalsNil())
		{
			expression = aBlock.value(expression);
			object.setSlot(INITIALIZATION_EXPRESSION, expression);
		}
	}


	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<A_Phrase> aBlock)
	{
		final AvailObject expression = object.initializationExpression();
		if (!expression.equalsNil())
		{
			aBlock.value(expression);
		}
	}


	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	@Override
	ParseNodeKind o_ParseNodeKind (
		final AvailObject object)
	{
		return object.declarationKind().parseNodeKind();
	}


	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		object.declarationKind().print(
			object,
			builder,
			recursionList,
			indent);
	}


	/**
	 * Construct a {@linkplain DeclarationNodeDescriptor declaration node} of
	 * some {@linkplain DeclarationKind kind}.
	 *
	 * @param declarationKind
	 *        The {@linkplain DeclarationKind kind} of {@linkplain
	 *        DeclarationNodeDescriptor declaration} to create.
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the entity being declared.
	 * @param declaredType
	 *        The {@linkplain TypeDescriptor type} of the entity being declared.
	 * @param initializationExpression
	 *        An {@linkplain ParseNodeDescriptor expression} used for
	 *        initializing the entity being declared, or {@linkplain
	 *        NilDescriptor#nil() nil} if none.
	 * @param literalObject
	 *        An {@link AvailObject} that is the actual variable or constant
	 *        being defined, or {@linkplain NilDescriptor#nil() the top
	 *        object} if none.
	 * @return The new {@linkplain DeclarationNodeDescriptor declaration}.
	 */
	private static A_Phrase newDeclaration (
		final DeclarationKind declarationKind,
		final A_Token token,
		final A_Type declaredType,
		final A_Phrase initializationExpression,
		final A_BasicObject literalObject)
	{
		assert token.isInstanceOf(Types.TOKEN.o());
		assert declaredType.isType();
		assert initializationExpression.equalsNil()
			|| initializationExpression.isInstanceOfKind(
				ParseNodeKind.EXPRESSION_NODE.create(Types.ANY.o()));

		final AvailObject declaration = mutable.create();
		declaration.setSlot(DECLARATION_KIND, declarationKind.ordinal());
		declaration.setSlot(TOKEN, token);
		declaration.setSlot(DECLARED_TYPE, declaredType);
		declaration.setSlot(
			INITIALIZATION_EXPRESSION,
			initializationExpression);
		declaration.setSlot(LITERAL_OBJECT, literalObject);
		declaration.makeShared();
		return declaration;
	}

	/**
	 * Construct a new {@linkplain DeclarationNodeDescriptor declaration} of a
	 * block or method {@linkplain DeclarationKind#ARGUMENT argument}.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the entity being declared.
	 * @param declaredType
	 *        The {@linkplain TypeDescriptor type} of the entity being declared.
	 * @return The argument declaration.
	 */
	public static A_Phrase newArgument (
		final A_Token token,
		final A_Type declaredType)
	{
		return newDeclaration(
			ARGUMENT,
			token,
			declaredType,
			NilDescriptor.nil(),
			NilDescriptor.nil());
	}

	/**
	 * Construct a new {@linkplain DeclarationNodeDescriptor declaration} of a
	 * {@linkplain DeclarationKind#LOCAL_VARIABLE local variable}.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the local variable being declared.
	 * @param declaredType
	 *        The {@linkplain TypeDescriptor type} of the local variable being
	 *        declared.
	 * @param initializationExpression
	 *        An {@linkplain ParseNodeDescriptor expression} used for
	 *        initializing the local variable, or {@linkplain
	 *        NilDescriptor#nil() nil} if none.
	 * @return The new local variable declaration.
	 */
	public static A_Phrase newVariable (
		final A_Token token,
		final A_Type declaredType,
		final A_Phrase initializationExpression)
	{
		return newDeclaration(
			LOCAL_VARIABLE,
			token,
			declaredType,
			initializationExpression,
			NilDescriptor.nil());
	}

	/**
	 * Construct a new {@linkplain DeclarationNodeDescriptor declaration} of a
	 * {@linkplain DeclarationKind#LOCAL_VARIABLE local variable} without an
	 * initialization expression.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the local variable being declared.
	 * @param declaredType
	 *        The {@linkplain TypeDescriptor type} of the local variable being
	 *        declared.
	 * @return The new local variable declaration.
	 */
	public static A_Phrase newVariable (
		final A_Token token,
		final A_Type declaredType)
	{
		return newDeclaration(
			LOCAL_VARIABLE,
			token,
			declaredType,
			NilDescriptor.nil(),
			NilDescriptor.nil());
	}

	/**
	 * Construct a new {@linkplain DeclarationNodeDescriptor declaration} of a
	 * {@linkplain DeclarationKind#LOCAL_CONSTANT local constant}.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the local constant being declared.
	 * @param initializationExpression
	 *        An {@linkplain ParseNodeDescriptor expression} used to
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
			initializationExpression,
			NilDescriptor.nil());
	}

	/**
	 * Construct a new {@linkplain DeclarationNodeDescriptor declaration} of a
	 * {@linkplain DeclarationKind#PRIMITIVE_FAILURE_REASON primitive failure
	 * variable}.  This is set up automatically when a primitive fails, and the
	 * statements of the block should not be allowed to write to it.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the local constant being declared.
	 * @param type
	 *        The type
	 *        An {@linkplain ParseNodeDescriptor expression} used to
	 *        provide the value of the local constant.
	 * @return The new local constant declaration.
	 */
	public static A_Phrase newPrimitiveFailureVariable (
		final A_Token token,
		final A_Type type)
	{
		return newDeclaration(
			PRIMITIVE_FAILURE_REASON,
			token,
			type,
			NilDescriptor.nil(),
			NilDescriptor.nil());
	}

	/**
	 * Construct a new {@linkplain DeclarationNodeDescriptor declaration} of a
	 * {@linkplain DeclarationKind#LABEL label}.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the label being declared.
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
		final A_Type declaredType)
	{
		return newDeclaration(
			LABEL,
			token,
			declaredType,
			NilDescriptor.nil(),
			NilDescriptor.nil());
	}

	/**
	 * Construct a new {@linkplain DeclarationNodeDescriptor declaration} of a
	 * {@linkplain DeclarationKind#MODULE_VARIABLE module variable} without an
	 * initialization expression.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that is the defining
	 *        occurrence of the name of the module variable being declared.
	 * @param literalVariable
	 *        The actual {@linkplain VariableDescriptor variable} to be used
	 *        as a module variable.
	 * @param initializationExpression
	 *        The expression (or null) used to initialize this module variable.
	 * @return The new module variable declaration.
	 */
	public static A_Phrase newModuleVariable(
		final A_Token token,
		final A_BasicObject literalVariable,
		final A_Phrase initializationExpression)
	{
		return newDeclaration(
			MODULE_VARIABLE,
			token,
			literalVariable.kind().readType(),
			initializationExpression,
			literalVariable);
	}

	/**
	 * Construct a new {@linkplain DeclarationNodeDescriptor declaration} of a
	 * {@linkplain DeclarationKind#MODULE_CONSTANT module constant}.
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
	public static A_Phrase newModuleConstant(
		final A_Token token,
		final A_BasicObject literalVariable,
		final A_Phrase initializationExpression)
	{
		return newDeclaration(
			MODULE_CONSTANT,
			token,
			literalVariable.kind().readType(),
			initializationExpression,
			literalVariable);
	}

	/**
	 * Construct a new {@link DeclarationNodeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public DeclarationNodeDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link DeclarationNodeDescriptor}. */
	private static final DeclarationNodeDescriptor mutable =
		new DeclarationNodeDescriptor(Mutability.MUTABLE);

	@Override
	DeclarationNodeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link DeclarationNodeDescriptor}. */
	private static final DeclarationNodeDescriptor shared =
		new DeclarationNodeDescriptor(Mutability.SHARED);

	@Override
	DeclarationNodeDescriptor shared ()
	{
		return shared;
	}
}
