/**
 * compiler/AvailBlockNode.java
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

package com.avail.compiler;

import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.AvailParseNode;
import com.avail.compiler.AvailVariableDeclarationNode;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ClosureDescriptor;
import com.avail.descriptor.ClosureTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.levelTwo.L2Interpreter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AvailBlockNode extends AvailParseNode
{
	List<AvailVariableDeclarationNode> _arguments;
	int _primitive;
	List<AvailParseNode> _statements;
	AvailObject _resultType;
	List<AvailVariableDeclarationNode> _neededVariables;


	// accessing

	public List<AvailVariableDeclarationNode> arguments ()
	{
		return _arguments;
	}

	public void arguments (
			final List<AvailVariableDeclarationNode> anArray)
	{
		_arguments = anArray;
	}

	public List<AvailLabelNode> labels ()
	{
		List<AvailLabelNode> labels = new ArrayList<AvailLabelNode>(1);
		for (AvailParseNode maybeLabel : _statements)
		{
			if (maybeLabel.isLabel())
			{
				labels.add((AvailLabelNode)maybeLabel);
			}
		}
		return labels;
	}

	public List<AvailVariableDeclarationNode> locals ()
	{
		List<AvailVariableDeclarationNode> locals = new ArrayList<AvailVariableDeclarationNode>(5);
		for (AvailParseNode maybeLocal : _statements)
		{
			if (maybeLocal.isDeclaration() && !maybeLocal.isLabel())
			{
				locals.add((AvailVariableDeclarationNode)maybeLocal);
			}
		}
		return locals;
	}

	public List<AvailVariableDeclarationNode> neededVariables ()
	{
		//  Computed during validation, used during code generation to determine what has
		//  to be pushed to make a closure of me.

		return _neededVariables;
	}

	public void primitive (
			final int primitiveNumber)
	{

		_primitive = primitiveNumber;
	}

	public AvailObject resultType ()
	{
		return _resultType;
	}

	public void resultType (
			final AvailObject aType)
	{

		_resultType = aType;
	}

	public List<AvailParseNode> statements ()
	{
		return _statements;
	}

	public void statements (
			final List<AvailParseNode> anArray)
	{
		_statements = anArray;
	}

	public AvailObject type ()
	{
		ArrayList<AvailObject> argumentTypes;
		argumentTypes = new ArrayList<AvailObject>(_arguments.size());
		for (AvailVariableDeclarationNode arg : _arguments)
		{
			argumentTypes.add(arg.declaredType());
		}
		return ClosureTypeDescriptor.closureTypeForArgumentTypesReturnType(TupleDescriptor.mutableObjectFromArray(argumentTypes), _resultType);
	}



	// code generation

	public void emitEffectOn (
			final AvailCodeGenerator codeGenerator)
	{
		//  The expression '[expr]' has no effect, only a value.
		return;
	}

	public void emitValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		final AvailCodeGenerator newGenerator = new AvailCodeGenerator();
		final AvailObject compiledBlock = generateOn(newGenerator);
		if (_neededVariables.isEmpty())
		{
			final AvailObject closure = ClosureDescriptor.newMutableObjectWithCodeAndCopiedTuple(compiledBlock, TupleDescriptor.empty());
			closure.makeImmutable();
			codeGenerator.emitPushLiteral(closure);
		}
		else
		{
			codeGenerator.emitClosedBlockWithCopiedVars(compiledBlock, _neededVariables);
		}
	}

	public AvailObject generateOn (
			final AvailCodeGenerator codeGenerator)
	{
		//  Answer an Avail compiled block compiled from this block node.

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
			AvailParseNode lastStatement = _statements.get(_statements.size() - 1);
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



	// enumerating

	public void allLocallyDefinedVariablesDo (
			final Continuation1<AvailVariableDeclarationNode> aBlock)
	{
		for (AvailVariableDeclarationNode arg : _arguments)
		{
			aBlock.value(arg);
		}
		for (AvailVariableDeclarationNode local : locals())
		{
			aBlock.value(local);
		}
		for (AvailVariableDeclarationNode label : labels())
		{
			aBlock.value(label);
		}
	}

	public void childrenMap (
			final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{
		//  Map my children through the (destructive) transformation
		//  specified by aBlock.  Answer the receiver.

		for (int i = 0; i < _arguments.size(); i++)
		{
			_arguments.set(i, (AvailVariableDeclarationNode)(aBlock.value(_arguments.get(i))));
		}
		for (int i = 0; i < _statements.size(); i++)
		{
			_statements.set(i, aBlock.value(_statements.get(i)));
		}
	}

	public AvailParseNode treeMapAlsoPassingParentAndEnclosingBlocksMyParentOuterBlockNodes (
			final Transformer3<AvailParseNode, AvailParseNode, List<AvailBlockNode>, AvailParseNode> aBlock, 
			final AvailParseNode parent, 
			final List<AvailBlockNode> outerNodes)
	{
		//  Map the tree through the (destructive) transformation specified by aBlock, children
		//  before parents.  The block takes three arguments; the node, its parent, and the list of
		//  enclosing block nodes.  Answer the resulting tree.

		final List<AvailBlockNode> outerNodesWithSelf = new ArrayList<AvailBlockNode>(outerNodes.size() + 1);
		outerNodesWithSelf.addAll(outerNodes);
		outerNodesWithSelf.add(this);
		childrenMap(new Transformer1<AvailParseNode, AvailParseNode> ()
		{
			public AvailParseNode value(AvailParseNode child)
			{
				return child.treeMapAlsoPassingParentAndEnclosingBlocksMyParentOuterBlockNodes(
					aBlock,
					AvailBlockNode.this,
					outerNodesWithSelf);
			}
		});
		return aBlock.value(
			this,
			parent,
			outerNodes);
	}



	// java printing

	public void printOnIndent (
			final StringBuilder aStream, 
			final int indent)
	{
		//  Optimize for one-liners...

		if (((_arguments.size() == 0) && ((_primitive == 0) && (_statements.size() == 1))))
		{
			aStream.append('[');
			_statements.get(0).printOnIndent(aStream, indent + 1);
			aStream.append(";]");
			return;
		}
		//  Ok, use multiple lines instead...
		aStream.append('[');
		if ((_arguments.size() > 0))
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
			final AvailParseNode statement = _statements.get(statementIndex - 1);
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



	// testing

	public boolean isBlock ()
	{
		return true;
	}



	// validation

	public void collectNeededVariablesOfOuterBlocks (
			final List<AvailBlockNode> outerBlocks)
	{
		//  Figure out what outer variables will need to be copied in when a closure for me is built.

		final Set<AvailVariableDeclarationNode> needed = new HashSet<AvailVariableDeclarationNode>();
		final Set<AvailVariableDeclarationNode> providedByMe = new HashSet<AvailVariableDeclarationNode>();
		allLocallyDefinedVariablesDo(new Continuation1<AvailVariableDeclarationNode> ()
		{
			public void value(AvailVariableDeclarationNode declarationNode)
			{
				providedByMe.add(declarationNode);
			}
		});
		final Transformer1<AvailParseNode, AvailParseNode> forEachBlock = new Transformer1<AvailParseNode, AvailParseNode> ()
		{
			public AvailParseNode value(AvailParseNode node)
			{
				if (node.isBlock())
				{
					for (AvailVariableDeclarationNode declaration : ((AvailBlockNode)node).neededVariables())
					{
						if (!providedByMe.contains(declaration))
						{
							needed.add(declaration);
						}
					}
				}
				else
				{
					node.childrenMap(this);
					if (node.isVariableUse())
					{
						AvailVariableDeclarationNode declaration = ((AvailVariableUseNode)node).associatedDeclaration();
						if ((!providedByMe.contains(declaration)) && (!declaration.isSyntheticVariableDeclaration()))
						{
							needed.add(declaration);
						}
					}
				}
				return node;
			}
		};
		childrenMap(forEachBlock);
		_neededVariables = new ArrayList<AvailVariableDeclarationNode>(needed);
	}

	public AvailParseNode validateLocallyWithParentOuterBlocksInterpreter (
			final AvailParseNode parent, 
			final List<AvailBlockNode> outerBlocks, 
			final L2Interpreter anAvailInterpreter)
	{
		//  Ensure the node represented by the receiver is valid.  Raise an appropriate
		//  exception if it is not.  outerBlocks is a list of enclosing BlockNodes.
		//  Answer the receiver.
		//
		//  Make sure our neededVariables list has up-to-date information about the outer
		//  variables that are accessed in me, because they have to be copied in when a
		//  closure is made for me.

		collectNeededVariablesOfOuterBlocks(outerBlocks);
		return this;
	}





}
