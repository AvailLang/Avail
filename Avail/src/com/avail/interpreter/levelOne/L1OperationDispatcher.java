/**
 * interpreter/levelOne/L1OperationDispatcher.java
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

package com.avail.interpreter.levelOne;


public interface L1OperationDispatcher
{
	void L1_doCall();
	void L1_doVerifyType();
	void L1_doPushLiteral();
	void L1_doPushLastLocal();
	void L1_doPushLocal();
	void L1_doPushLastOuter();
	void L1_doClose();
	void L1_doSetLocal();
	void L1_doGetLocalClearing();
	void L1_doPushOuter();
	void L1_doPop();
	void L1_doGetOuterClearing();
	void L1_doSetOuter();
	void L1_doGetLocal();
	void L1_doMakeList();
	void L1_doExtension();
	void L1Ext_doGetOuter();
	void L1Ext_doPushLabel();
	void L1Ext_doGetLiteral();
	void L1Ext_doSetLiteral();
	void L1Ext_doSuperCall();
	void L1Ext_doGetType();
	void L1Ext_doReserved();
	void L1Implied_doReturn();
}
