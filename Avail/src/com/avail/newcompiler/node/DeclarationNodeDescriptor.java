/**
 * com.avail.newcompiler/DeclarationNodeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.newcompiler.node;

import static com.avail.descriptor.AvailObject.*;
import static com.avail.newcompiler.node.DeclarationNodeDescriptor.DeclarationKind.*;
import java.util.List;
import com.avail.annotations.EnumField;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.newcompiler.scanner.TokenDescriptor;
import com.avail.utility.Transformer1;

/**
 * My instances represent assignment statements.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class DeclarationNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@link TokenDescriptor token} containing the name of the entity
		 * being declared.
		 */
		TOKEN,

		/**
		 * The {@link TypeDescriptor type} of the variable being declared.
		 */
		DECLARED_TYPE,

		/**
		 * The optional {@link ParseNodeDescriptor initialization expression},
		 * or the {@link VoidDescriptor#voidObject() voidObject} otherwise.  Not
		 * applicable to all kinds of declarations.
		 */
		INITIALIZATION_EXPRESSION,

		/**
		 * The {@link AvailObject} held directly by this declaration.  It can be
		 * either a module constant value or a module variable.
		 */
		LITERAL_OBJECT
	}

	/**
	 * My slots of type {@link Integer int}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum IntegerSlots
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
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum DeclarationKind
	{
		/**
		 * This is an argument to a block.
		 */
		ARGUMENT(false, Types.argumentNode)
		{
			@Override
			public void emitVariableValueForOn (
				final AvailObject declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLocalOrOuter(declarationNode);
			}

			@Override
			public void print (
				final AvailObject object,
				final StringBuilder builder,
				final List<AvailObject> recursionList,
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
		LABEL(false, Types.labelNode)
		{
			/**
			 * Let the code generator know that the label occurs at the
			 * current code position.
			 */
			@Override
			public void emitEffectForOn (
				final AvailObject object,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitLabelDeclaration(object);
			}

			@Override
			public void emitVariableValueForOn (
				final AvailObject declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLocalOrOuter(declarationNode);
			}

			@Override
			public void print (
				final AvailObject object,
				final StringBuilder builder,
				final List<AvailObject> recursionList,
				final int indent)
			{
				builder.append('$');
				builder.append(object.token().string().asNativeString());
				builder.append(':');
				object.declaredType().printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
		},

		/**
		 * This is a local variable, declared within a block.
		 */
		LOCAL_VARIABLE(true, Types.localVariableNode)
		{
			@Override
			public void emitEffectForOn (
				final AvailObject declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				final AvailObject expr = declarationNode.initializationExpression();
				if (!expr.equalsVoid())
				{
					expr.emitValueOn(codeGenerator);
					codeGenerator.emitSetLocalOrOuter(declarationNode);
				}
			}

			@Override
			public void emitVariableAssignmentForOn (
				final AvailObject declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitSetLocalOrOuter(declarationNode);
			}

			@Override
			public void emitVariableReferenceForOn (
				final AvailObject declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLocalOrOuter(declarationNode);
			}

			@Override
			public void emitVariableValueForOn (
				final AvailObject declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitGetLocalOrOuter(declarationNode);
			}

			@Override
			public void print (
				final AvailObject object,
				final StringBuilder builder,
				final List<AvailObject> recursionList,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" : ");
				object.declaredType().printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
				if (!object.initializationExpression().equalsVoid())
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
		LOCAL_CONSTANT(false, Types.localConstantNode)
		{
			@Override
			public void emitEffectForOn (
				final AvailObject declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				declarationNode.initializationExpression()
					.emitValueOn(codeGenerator);
				codeGenerator.emitSetLocalOrOuter(declarationNode);
			}

			@Override
			public void emitVariableValueForOn (
				final AvailObject declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitGetLocalOrOuter(declarationNode);
			}

			@Override
			public void print (
				final AvailObject object,
				final StringBuilder builder,
				final List<AvailObject> recursionList,
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
		MODULE_VARIABLE(true, Types.moduleVariableNode)
		{
			@Override
			public void emitVariableAssignmentForOn (
				final AvailObject declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitSetLiteral(declarationNode.literalObject());
			}

			@Override
			public void emitVariableReferenceForOn (
				final AvailObject declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLiteral(declarationNode.literalObject());
			}

			@Override
			public void emitVariableValueForOn (
				final AvailObject declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitGetLiteral(declarationNode.literalObject());
			}

			@Override
			public void print (
				final AvailObject object,
				final StringBuilder builder,
				final List<AvailObject> recursionList,
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
		 * This is a constant declared at the outermost (module) scope.
		 */
		MODULE_CONSTANT(false, Types.moduleConstantNode)
		{
			@Override
			public void emitVariableValueForOn (
				final AvailObject declarationNode,
				final AvailCodeGenerator codeGenerator)
			{
				codeGenerator.emitPushLiteral(declarationNode.literalObject());
			}

			@Override
			public void print (
				final AvailObject object,
				final StringBuilder builder,
				final List<AvailObject> recursionList,
				final int indent)
			{
				builder.append(object.token().string().asNativeString());
				builder.append(" ::= ");
				object.literalObject().printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
		};


		/**
		 * Whether this entity can be modified.
		 */
		private final boolean isVariable;

		/**
		 * The instance of the enumeration {@link TypeDescriptor.Types} that
		 * is associated with this kind of declaration.
		 */
		private final Types typeEnumeration;

		/**
		 * Construct a {@link DeclarationKind}.  Can only be invoked implicitly
		 * when constructing the enumeration values.
		 *
		 * @param isVariable
		 *        Whether it's legal to assign to this entity.
		 * @param typeEnumeration
		 *        The enumeration instance of {@link TypeDescriptor.Types} that
		 *        is associated with this kind of declaration.
		 */
		DeclarationKind (
			final boolean isVariable,
			final Types typeEnumeration)
		{
			this.isVariable = isVariable;
			this.typeEnumeration = typeEnumeration;
		}

		/**
		 * Return whether this entity can be written.
		 *
		 * @return Whether this entity is assignable.
		 */
		public boolean isVariable ()
		{
			return isVariable;
		}

		/**
		 * Return the {@link PrimitiveTypeDescriptor primitive type} associated
		 * with this kind of entity.
		 *
		 * @return The Avail {@link TypeDescriptor type} associated with this
		 *         kind of entity.
		 */
		public AvailObject primitiveType ()
		{
			return typeEnumeration.object();
		}


		/**
		 * Emit an assignment to this variable.
		 *
		 * @param declarationNode The declaration that has this declarationKind.
		 * @param codeGenerator Where to generate the assignment.
		 */
		public void emitVariableAssignmentForOn (
			final AvailObject declarationNode,
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
			final AvailObject declarationNode,
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
			final AvailObject declarationNode,
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
			final AvailObject object,
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
			final AvailObject object,
			final StringBuilder builder,
			final List<AvailObject> recursionList,
			final int indent);
	}

	/**
	 * Setter for field name.
	 */
	@Override
	public void o_Token (
		final AvailObject object,
		final AvailObject token)
	{
		object.objectSlotPut(ObjectSlots.TOKEN, token);
	}

	/**
	 * Getter for field name.
	 */
	@Override
	public AvailObject o_Token (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.TOKEN);
	}

	/**
	 * Setter for field declaredType.
	 */
	@Override
	public void o_DeclaredType (
		final AvailObject object,
		final AvailObject declaredType)
	{
		object.objectSlotPut(ObjectSlots.DECLARED_TYPE, declaredType);
	}

	/**
	 * Getter for field declaredType.
	 */
	@Override
	public AvailObject o_DeclaredType (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.DECLARED_TYPE);
	}

	/**
	 * Setter for field initializationExpression.
	 */
	@Override
	public void o_InitializationExpression (
		final AvailObject object,
		final AvailObject initializationExpression)
	{
		object.objectSlotPut(
			ObjectSlots.INITIALIZATION_EXPRESSION,
			initializationExpression);
	}

	/**
	 * Getter for field initializationExpression.
	 */
	@Override
	public AvailObject o_InitializationExpression (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INITIALIZATION_EXPRESSION);
	}

	/**
	 * Setter for field literalObject.
	 */
	@Override
	public void o_LiteralObject (
		final AvailObject object,
		final AvailObject literalObject)
	{
		object.objectSlotPut(ObjectSlots.LITERAL_OBJECT, literalObject);
	}

	/**
	 * Getter for field literalObject.
	 */
	@Override
	public AvailObject o_LiteralObject (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.LITERAL_OBJECT);
	}

	/**
	 * Setter for field declarationKind.
	 */
	@Override
	public void o_DeclarationKind (
		final AvailObject object,
		final DeclarationKind declarationKind)
	{
		object.integerSlotPut(
			IntegerSlots.DECLARATION_KIND,
			declarationKind.ordinal());
	}

	/**
	 * Getter for field declarationKind.
	 */
	@Override
	public DeclarationKind o_DeclarationKind (
		final AvailObject object)
	{
		return DeclarationKind.values()[object.integerSlot(
			IntegerSlots.DECLARATION_KIND)];
	}


	@Override
	public AvailObject o_ExpressionType (final AvailObject object)
	{
		error("Don't ask for the type of a variable declaration node");
		return VoidDescriptor.voidObject();
	}


	/**
	 * This is a declaration, so it was handled on a separate pass.  Do nothing.
	 *
	 * @param codeGenerator Where to emit the declaration.
	 */
	@Override
	public void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.declarationKind().emitEffectForOn(object, codeGenerator);
	}


	/**
	 * This is a declaration, so it shouldn't generally produce a value.
	 */
	@Override
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.emitEffectOn(codeGenerator);
		codeGenerator.emitPushLiteral(VoidDescriptor.voidObject());
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		return object.declarationKind().primitiveType();
	}

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		return object.declarationKind().primitiveType();
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return
			(((object.token().hash() * Multiplier
				+ object.declaredType().hash()) * Multiplier
				+ object.initializationExpression().hash()) * Multiplier
				+ object.literalObject().hash()) * Multiplier
				+ object.declarationKind().ordinal()
			^ 0x4C27EB37;
	}

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.type().equals(another.type())
			&& object.token().equals(another.token())
			&& object.declaredType().equals(another.declaredType())
			&& object.initializationExpression().equals(
				another.initializationExpression())
			&& object.literalObject().equals(another.literalObject())
			&& object.declarationKind() == another.declarationKind();
	}

	/**
	 * Emit a reference to this variable.
	 *
	 * @param declarationNode The declaration that has this declarationKind.
	 * @param codeGenerator Where to emit the reference to this variable.
	 */
	public static void emitVariableReferenceOn (
		final AvailObject declarationNode,
		final AvailCodeGenerator codeGenerator)
	{
		declarationNode.declarationKind().emitVariableReferenceForOn(
			declarationNode,
			codeGenerator);
	}


	@Override
	public void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		AvailObject expression = object.initializationExpression();
		if (!expression.equalsVoid())
		{
			expression = aBlock.value(expression);
			object.initializationExpression(expression);
		}
	}



	@Override
	public void o_ValidateLocally (
		final AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		// Do nothing.
	}


	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		object.declarationKind().print(
			object,
			builder,
			recursionList,
			indent);
	}


	private static AvailObject newDeclaration (
		final DeclarationKind declarationKind,
		final AvailObject token,
		final AvailObject declaredType,
		final AvailObject initializationExpression,
		final AvailObject literalObject)
	{
		final AvailObject declaration = mutable().create();
		declaration.declarationKind(declarationKind);
		declaration.token(token);
		declaration.declaredType(declaredType);
		declaration.initializationExpression(initializationExpression);
		declaration.literalObject(literalObject);
		// System.out.println(declaration);
		return declaration;
	}

	public static AvailObject newArgument (
		final AvailObject token,
		final AvailObject declaredType)
	{
		return newDeclaration(
			ARGUMENT,
			token,
			declaredType,
			VoidDescriptor.voidObject(),
			VoidDescriptor.voidObject());
	}

	public static AvailObject newVariable (
		final AvailObject token,
		final AvailObject declaredType,
		final AvailObject initializationExpression)
	{
		return newDeclaration(
			LOCAL_VARIABLE,
			token,
			declaredType,
			initializationExpression,
			VoidDescriptor.voidObject());
	}

	public static AvailObject newVariable (
		final AvailObject token,
		final AvailObject declaredType)
	{
		return newDeclaration(
			LOCAL_VARIABLE,
			token,
			declaredType,
			VoidDescriptor.voidObject(),
			VoidDescriptor.voidObject());
	}

	public static AvailObject newConstant (
		final AvailObject token,
		final AvailObject initializationExpression)
	{
		return newDeclaration(
			LOCAL_CONSTANT,
			token,
			initializationExpression.expressionType(),
			initializationExpression,
			VoidDescriptor.voidObject());
	}

	public static AvailObject newLabel (
		final AvailObject token,
		final AvailObject declaredType)
	{
		return newDeclaration(
			LABEL,
			token,
			declaredType,
			VoidDescriptor.voidObject(),
			VoidDescriptor.voidObject());
	}

	public static AvailObject newModuleVariable(
		final AvailObject token,
		final AvailObject literalObject)
	{
		return newDeclaration(
			MODULE_VARIABLE,
			token,
			literalObject.type().innerType(),
			VoidDescriptor.voidObject(),
			literalObject);
	}

	public static AvailObject newModuleConstant(
		final AvailObject token,
		final AvailObject literalObject)
	{
		return newDeclaration(
			MODULE_CONSTANT,
			token,
			literalObject.type(),
			VoidDescriptor.voidObject(),
			literalObject);
	}


	/**
	 * Construct a new {@link DeclarationNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public DeclarationNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link DeclarationNodeDescriptor}.
	 */
	private final static DeclarationNodeDescriptor mutable =
		new DeclarationNodeDescriptor(true);

	/**
	 * Answer the mutable {@link DeclarationNodeDescriptor}.
	 *
	 * @return The mutable {@link DeclarationNodeDescriptor}.
	 */
	public static DeclarationNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link DeclarationNodeDescriptor}.
	 */
	private final static DeclarationNodeDescriptor immutable =
		new DeclarationNodeDescriptor(false);

	/**
	 * Answer the immutable {@link DeclarationNodeDescriptor}.
	 *
	 * @return The immutable {@link DeclarationNodeDescriptor}.
	 */
	public static DeclarationNodeDescriptor immutable ()
	{
		return immutable;
	}

}
