/**
 * AbstractBuilderFrameTreeNode.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.environment.nodes;

import com.avail.builder.AvailBuilder;
import com.avail.environment.AvailWorkbench;
import com.avail.utility.LRUCache;
import com.avail.utility.Pair;
import com.avail.utility.evaluation.Transformer1;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.net.URL;
import java.util.Comparator;

/**
 * An {@code AbstractBuilderFrameTreeNode} is a tree node used within some
 * {@link AvailWorkbench}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@SuppressWarnings("serial")
public abstract class AbstractBuilderFrameTreeNode
extends DefaultMutableTreeNode
{
	/** The {@link AvailBuilder} for which this node presents information. */
	final AvailBuilder builder;

	/**
	 * Answer the {@link AvailBuilder} for which this node presents information.
	 *
	 * @return The Avail builder.
	 */
	public AvailBuilder builder ()
	{
		return builder;
	}

	/**
	 * Construct a new {@code AbstractBuilderFrameTreeNode} on behalf of the
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
	 * The local file name {@code String} of an image file, relative to the
	 * directory "/resources/workbench/".
	 *
	 * @return The local file name, or {@code null} to indicate not to display
	 *         an icon.
	 */
	abstract @Nullable String iconResourceName ();

	/**
	 * A static cache of scaled icons, organized by node class and line height.
	 */
	private static final LRUCache<Pair<String, Integer>, ImageIcon>
		cachedScaledIcons = new LRUCache<>(
			100,
			20,
			new Transformer1<Pair<String, Integer>, ImageIcon>()
			{
				@Override
				public ImageIcon value(
					final @Nullable Pair<String, Integer> key)
				{
					assert key != null;
					final String iconResourceName = key.first();
					@SuppressWarnings("StringConcatenationMissingWhitespace")
					final String path = AvailWorkbench.resourcePrefix
						+ iconResourceName + ".png";
					final Class<?> thisClass = this.getClass();
					final URL resource = thisClass.getResource(path);
					final ImageIcon originalIcon = new ImageIcon(resource);
					final Image scaled =
						originalIcon.getImage().getScaledInstance(
							-1, key.second(), Image.SCALE_SMOOTH);
					return new ImageIcon(scaled, iconResourceName);
				}
			});

	/**
	 * Return a suitable icon to display for this instance with the given line
	 * height.
	 *
	 * @param lineHeight The desired icon height in pixels.
	 * @return The icon.
	 */
	public final @Nullable ImageIcon icon (final int lineHeight)
	{
		final @Nullable String iconResourceName = iconResourceName();
		if (iconResourceName == null)
		{
			return null;
		}
		final Pair<String, Integer> pair = new Pair<>(
			iconResourceName,
			lineHeight != 0 ? lineHeight : 19);
		return cachedScaledIcons.get(pair);
	}

	/**
	 * Construct HTML text to present for this node.
	 *
	 * @param selected
	 *        Whether the node is selected.
	 * @return The HTML text as a {@link String}.
	 */
	public final String htmlText (final boolean selected)
	{
		return "<div style=\"" + htmlStyle(selected) + "\">"
			+ text(selected)
			+ "</div>";
	}

	@Override
	public final String toString ()
	{
		return getClass().getSimpleName() + ": " + text(false);
	}

	/**
	 * Answer whether string is an appropriate semantic label for this node.
	 *
	 * @param string The string.
	 * @return Whether this is the indicated node.
	 */
	public final boolean isSpecifiedByString (final String string)
	{
		return text(false).equals(string);
	}

	private static final Comparator<AbstractBuilderFrameTreeNode> sorter =
		Comparator.comparing(AbstractBuilderFrameTreeNode::sortMajor)
			.thenComparing(node -> node.text(false));

	/**
	 * Sort the direct children of this node.  The default sort order is
	 * alphabetic by the nodes' {@link #text(boolean)} (passing false).
	 */
	@SuppressWarnings("unchecked")
	public void sortChildren ()
	{
		if (children != null)
		{
			children.sort(sorter);
		}
	}

	/**
	 * The primary order by which to sort this node relative to siblings.
	 *
	 * @return An {@code int}.  Lower values sort before higher ones.
	 */
	public int sortMajor ()
	{
		return 0;
	}
}
