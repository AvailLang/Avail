/**
 * AbstractBuilderFrameTreeNode.java
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

package com.avail.environment;

import javax.swing.tree.DefaultMutableTreeNode;
import com.avail.builder.AvailBuilder;

/**
 * An {@code AbstractBuilderFrameTreeNode} is a tree node used within some
 * {@link AvailBuilderFrame}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@SuppressWarnings("serial")
abstract class AbstractBuilderFrameTreeNode
extends DefaultMutableTreeNode
{
	/** The {@link AvailBuilder} for which this node presents information. */
	final AvailBuilder builder;

	/**
	 * Construct a new {@link AbstractBuilderFrameTreeNode} on behalf of the
	 * given {@link AvailBuilder}.
	 *
	 * @param builder The builder for which this node is being built.
	 */
	public AbstractBuilderFrameTreeNode (final AvailBuilder builder)
	{
		this.builder = builder;
	}

	/**
	 * Extract text to display for this node.  Presentation styling will be
	 * applied separately.
	 *
	 * @param selected
	 *        Whether the node is selected.
	 * @return A {@link String}.
	 *
	 */
	abstract String text (boolean selected);

	/**
	 * Produce a string for use in a <span style=…> tag for this node.
	 *
	 * @param selected
	 *        Whether the node is selected.
	 * @return The span style attribute text.
	 */
	String htmlStyle (final boolean selected)
	{
		// no-op, but can't be blank.
		return "font-weight:normal";
	}

	/**
	 * Construct HTML text to present for this node.
	 *
	 * @param selected
	 *        Whether the node is selected.
	 * @return
	 */
	final String htmlText (final boolean selected)
	{
		return "<div style='" + htmlStyle(selected) + "'>"
			+ text(selected)
			+ "</div>";
	}

	@Override
	final public String toString ()
	{
		return getClass().getSimpleName() + ": " + text(false);
	}

	/**
	 * Answer whether string is an appropriate semantic label for this node.
	 *
	 * @param string The string.
	 * @return Whether this is the indicated node.
	 */
	final public boolean isSpecifiedByString (final String string)
	{
		return text(false).equals(string);
	}
}