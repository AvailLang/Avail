/**
 * compiler/AvailBlockNode.java Copyright (c) 2010, Mark van Gulik. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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

package com.avail.oldcompiler;

import java.util.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * This represents a syntactic parse of a block of code in Avail. It can be
 * converted to an actual {@link ClosureDescriptor closure} via
 * {@link #generateOn(AvailCodeGenerator)}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AvailBlockNode extends AvailParseNode
{
	/**
	 * The block's argument declarations.
	 */
	private List<AvailVariableDeclarationNode> _arguments;

	/**
	 * The {@link Primitive primitive} number to invoke for this block.
	 */
	private int _primitive;

	/**
	 * The list of statements contained in this block.
	 */
	private List<AvailParseNode> _statements;

	/**
	 * The type this block is expected to return an instance of.
	 */
	private AvailObject _resultType;

	/**
	 * Any variables needed by this block. This is set after the
	 * {@linkplain AvailBlockNode} has already been created.
	 */
	private List<AvailVariableDeclarationNode> _neededVariables;




	/**
	 * Set this block's list of argument declarations.
	 *
	 * @param anArray
	 *            The argument declarations.
	 */
	public void arguments (final List<AvailVariableDeclarationNode> anArray)
	{
		_arguments = anArray;
	}

	/**
	 * Answer the labels present in this block's list of statements. There is
	 * either zero or one label, and it must be the first statement.
	 *
	 * @return A list of between zero and one labels.
	 */
	private List<AvailLabelNode> labels ()
	{
		final List<AvailLabelNode> labels = new ArrayList<AvailLabelNode>(1);
		for (final AvailParseNode maybeLabel : _statements)
		{
			if (maybeLabel.isLabel())
			{
				labels.add((AvailLabelNode) maybeLabel);
			}
		}
		return labels;
	}

	/**
	 * Answer the declarations of this block's local variables.
	 *
	 * @return This block's local variable declarations.
	 */
	private List<AvailVariableDeclarationNode> locals ()
	{
		final List<AvailVariableDeclarationNode> locals = new ArrayList<AvailVariableDeclarationNode>(
			5);
		for (final AvailParseNode maybeLocal : _statements)
		{
			if (maybeLocal.isDeclaration() && !maybeLocal.isLabel())
			{
				locals.add((AvailVariableDeclarationNode) maybeLocal);
			}
		}
		return locals;
	}

	/**
	 * Answer the list of declarations of variables that need to be captured
	 * when this {@linkplain AvailBlockNode} is lexically closed. This is
	 * computed during validation.
	 *
	 * @return A list of {@link AvailVariableDeclarationNode declarations}.
	 */
	protected List<AvailVariableDeclarationNode> neededVariables ()
	{
		return _neededVariables;
	}

	/**
	 * Set the primitive number associated with this block.
	 *
	 * @param primitiveNumber
	 *            An integer.
	 */
	public void primitive (final int primitiveNumber)
	{
		_primitive = primitiveNumber;
	}

	/**
	 * Set the type of objectw returned by this block.
	 *
	 * @param aType
	 *            An Avail {@link TypeDescriptor type}.
	 */
	public void resultType (final AvailObject aType)
	{

		_resultType = aType;
	}

	/**
	 * Answer this block's list of statements.
	 *
	 * @return A list of statements.
	 */
	public List<AvailParseNode> statements ()
	{
		return _statements;
	}

	/**
	 * Set this block's list of statements.
	 *
	 * @param anArray A list of statements.
	 */
	public void statements (final List<AvailParseNode> anArray)
	{
		_statements = anArray;
	}

	@Override
	public AvailObject expressionType ()
	{
		List<AvailObject> argumentTypes;
		argumentTypes = new ArrayList<AvailObject>(_arguments.size());
		for (final AvailVariableDeclarationNode arg : _arguments)
		{
			argumentTypes.add(arg.declaredType());
		}
		return ClosureTypeDescriptor.closureTypeForArgumentTypesReturnType(
			TupleDescriptor.mutableObjectFromArray(argumentTypes),
			_resultType);
	}

	@Override
	public void emitEffectOn (final AvailCodeGenerator codeGenerator)
	{
		// The expression '[expr]' has no effect, only a value.
		return;
	}

	@Override
	public void emitValueOn (final AvailCodeGenerator codeGenerator)
	{
		final AvailCodeGenerator newGenerator = new AvailCodeGenerator();
		final AvailObject compiledBlock = generateOn(newGenerator);
		if (_neededVariables.isEmpty())
		{
			final AvailObject closure = ClosureDescriptor
					.create(
						compiledBlock,
						TupleDescriptor.empty());
			closure.makeImmutable();
			codeGenerator.emitPushLiteral(closure);
		}
		else
		{
			codeGenerator.emitCloseCode(
				compiledBlock,
				_neededVariables);
		}
	}

	/**
	 * Answer an Avail compiled block compiled from this block node, using the
	 * given {@link AvailCodeGenerator}.
	 *
	 * @param codeGenerator
	 *            A {@link AvailCodeGenerator code generator}
	 * @return An {@link AvailObject} of type {@link ClosureDescriptor closure}.
	 */
	public AvailObject generateOn (final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.startBlockWithArgumentsLocalsLabelsOuterVarsResultType(
			_arguments,
			locals(),
			labels(),
			_neededVariables,
			_resultType);
		codeGenerator.stackShouldBeEmpty();
		codeGenerator.primitive(_primitive);
		codeGenerator.stackShouldBeEmpty();
		if (_statements.isEmpty())
		{
			codeGenerator.emitPushLiteral(VoidDescriptor.voidObject());
		}
		else
		{
			for (int index = 0; index < _statements.size() - 1; index++)
			{
				_statements.get(index).emitEffectOn(codeGenerator);
				codeGenerator.stackShouldBeEmpty();
			}
			final AvailParseNode lastStatement = _statements
					.get(_statements.size() - 1);
			if (lastStatement.isLabel() || lastStatement.isAssignment())
			{
				lastStatement.emitEffectOn(codeGenerator);
				codeGenerator.emitPushLiteral(VoidDescriptor.voidObject());
			}
			else
			{
				lastStatement.emitValueOn(codeGenerator);
			}
		}
		return codeGenerator.endBlock();
	}

	/**
	 * Evaluate the {@link Continuation1 block} for each of my arguments,
	 * locals, and labels.
	 *
	 * @param aBlock
	 *            What to do with each variable I define.
	 */
	private void allLocallyDefinedVariablesDo (
		final Continuation1<AvailVariableDeclarationNode> aBlock)
	{
		for (final AvailVariableDeclarationNode arg : _arguments)
		{
			aBlock.value(arg);
		}
		for (final AvailVariableDeclarationNode local : locals())
		{
			aBlock.value(local);
		}
		for (final AvailVariableDeclarationNode label : labels())
		{
			aBlock.value(label);
		}
	}

	@Override
	public void childrenMap (
		final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{
		// Map my children through the (destructive) transformation
		// specified by aBlock. Answer the receiver.

		for (int i = 0; i < _arguments.size(); i++)
		{
			_arguments
					.set(i, (AvailVariableDeclarationNode) aBlock
							.value(_arguments.get(i)));
		}
		for (int i = 0; i < _statements.size(); i++)
		{
			_statements.set(i, aBlock.value(_statements.get(i)));
		}
	}

	@Override
	public AvailParseNode treeMapAlsoPassingParentAndEnclosingBlocksMyParentOuterBlockNodes (
		final Transformer3<AvailParseNode, AvailParseNode, List<AvailBlockNode>, AvailParseNode> aBlock,
		final AvailParseNode parent,
		final List<AvailBlockNode> outerNodes)
	{
		final List<AvailBlockNode> outerNodesWithSelf = new ArrayList<AvailBlockNode>(
			outerNodes.size() + 1);
		outerNodesWithSelf.addAll(outerNodes);
		outerNodesWithSelf.add(this);
		childrenMap(new Transformer1<AvailParseNode, AvailParseNode>()
		{
			@Override
			public AvailParseNode value (final AvailParseNode child)
			{
				return child
						.treeMapAlsoPassingParentAndEnclosingBlocksMyParentOuterBlockNodes(
							aBlock,
							AvailBlockNode.this,
							outerNodesWithSelf);
			}
		});
		return aBlock.value(this, parent, outerNodes);
	}


	@Override
	public void printOnIndent (final StringBuilder aStream, final int indent)
	{
		// Optimize for one-liners...
		if (_arguments.size() == 0 && _primitive == 0 && _statements
				.size() == 1)
		{
			aStream.append('[');
			_statements.get(0).printOnIndent(aStream, indent + 1);
			aStream.append(";]");
			return;
		}

		// Use multiple lines instead...
		aStream.append('[');
		if (_arguments.size() > 0)
		{
			_arguments.get(0).printOnIndent(aStream, indent + 1);
			for (int argIndex = 2, _end1 = _arguments.size(); argIndex <= _end1; argIndex++)
			{
				aStream.append(", ");
				_arguments.get(argIndex - 1).printOnIndent(aStream, indent + 1);
			}
			aStream.append(" |");
		}
		aStream.append('\n');
		for (int _count2 = 1; _count2 <= indent; _count2++)
		{
			aStream.append('\t');
		}
		if (_primitive != 0)
		{
			aStream.append('\t');
			aStream.append("Primitive ");
			aStream.append(_primitive);
			aStream.append(";");
			aStream.append('\n');
			for (int _count3 = 1; _count3 <= indent; _count3++)
			{
				aStream.append('\t');
			}
		}
		for (int statementIndex = 1, _end4 = _statements.size(); statementIndex <= _end4; statementIndex++)
		{
			final AvailParseNode statement = _statements
					.get(statementIndex - 1);
			aStream.append('\t');
			statement.printOnIndent(aStream, indent + 1);
			aStream.append(';');
			aStream.append('\n');
			for (int _count5 = 1; _count5 <= indent; _count5++)
			{
				aStream.append('\t');
			}
		}
		aStream.append(']');
		if (_resultType != null)
		{
			aStream.append(" : ");
			aStream.append(_resultType.toString());
		}
	}

	@Override
	public boolean isBlock ()
	{
		return true;
	}

	/**
	 * Figure out what outer variables will need to be captured when a closure
	 * for me is built.
	 */
	private void collectNeededVariablesOfOuterBlocks ()
	{

		final Set<AvailVariableDeclarationNode> needed = new HashSet<AvailVariableDeclarationNode>();
		final Set<AvailVariableDeclarationNode> providedByMe = new HashSet<AvailVariableDeclarationNode>();
		allLocallyDefinedVariablesDo(new Continuation1<AvailVariableDeclarationNode>()
		{
			@Override
			public void value (final AvailVariableDeclarationNode declarationNode)
			{
				providedByMe.add(declarationNode);
			}
		});
		childrenMap(new Transformer1<AvailParseNode, AvailParseNode>()
		{
			@Override
			public AvailParseNode value (final AvailParseNode node)
			{
				if (node.isBlock())
				{
					final AvailBlockNode blockNode = (AvailBlockNode) node;
					for (final AvailVariableDeclarationNode declaration
							: blockNode.neededVariables())
					{
						if (!providedByMe.contains(declaration))
						{
							needed.add(declaration);
						}
					}
					return node;
				}
				node.childrenMap(this);
				if (!node.isVariableUse())
				{
					return node;
				}
				final AvailVariableUseNode use = (AvailVariableUseNode) node;
				final AvailVariableDeclarationNode declaration =
					use.associatedDeclaration();
				if (!providedByMe.contains(declaration)
						&& !declaration.isSyntheticVariableDeclaration())
				{
					needed.add(declaration);
				}
				return node;
			}
		});
		_neededVariables = new ArrayList<AvailVariableDeclarationNode>(needed);
	}


	@Override
	public AvailParseNode validateLocally (
		final AvailParseNode parent,
		final List<AvailBlockNode> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		// Make sure our neededVariables list has up-to-date information about
		// the outer variables that are accessed in me, because they have to be
		// captured when a closure is made for me.

		collectNeededVariablesOfOuterBlocks();
		return this;
	}

}
