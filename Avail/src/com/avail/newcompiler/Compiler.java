/**
 * compiler/Compiler.java
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

package com.avail.newcompiler;


public class Compiler
{
//	List<AvailObject> tokens;
//	int position;
//	AvailObject currentToken;
//	List<ParserState> alternativeStates;
//	int greatestGuess;
//	List<Generator<String>> greatExpectations;
//	L2Interpreter interpreter;
//	AvailObject module;
//	AvailCompilerFragmentCache fragmentCache;
//	List<AvailObject> extendedModules;
//	List<AvailObject> usedModules;
//	List<AvailObject> exportedNames;
//	Continuation2<Integer, Integer> progressBlock;
//
//
//	public class AvailObjectStack
//	{
//		protected AvailObject node;
//		protected AvailObjectStack next;
//		public AvailObjectStack push (AvailObject newNode)
//		{
//			AvailObjectStack newStack = new AvailObjectStack();
//			newStack.node = newNode;
//			newStack.next = this;
//			return newStack;
//		}
//	}
//
//	public class ParserState
//	{
//		AvailObjectStack nodeStack;
//		List<AvailObject> expressionStack;
//		AvailObject firstArgument;
//		AvailCompilerScopeStack scopeStack;
//		AvailObject popNode ()
//		{
//			AvailObject node = nodeStack.node;
//			nodeStack = nodeStack.next;
//			return node;
//		}
//		void pushNode (AvailObject node)
//		{
//			AvailObjectStack newStack = new AvailObjectStack();
//			newStack.node = node;
//			newStack.next = nodeStack;
//			nodeStack = newStack;
//		}
//	};

//	public enum ParserOperation
//	{
//		pushEmpty(0)
//		{
//			@Override
//			void step (ParserState state, Compiler compiler)
//			{
//				state.pushNode(TupleDescriptor.empty());
//			}
//		},
//		append(0)
//		{
//			@Override
//			void step (ParserState state, Compiler compiler)
//			{
//				AvailObject element = state.popNode();
//				AvailObject oldList = state.popNode();
//				newList = oldList.copyWith(element);
//				state.pushNode(newList);
//			}
//		},
//		parseName(0)
//		{
//			if (currentToken.isKeyword())
//			{
//				error("finish this");
//			}
//		},
//		parseArgument(0)
//		{
//			if (currentToken.is)
//		},
//		parseKeyword(1)
//		{
//
//		},
//		forkTo(1)
//		{
//
//		},
//		jump(1)
//		{
//
//		},
//		endParse(1)
//		{
//
//		};
//
//		public final int operandCount;
//
//		ParserOperation (int operandCount)
//		{
//			this.operandCount = operandCount;
//		}
//
//		abstract void step (ParserState state, Compiler compiler);
//	}
}