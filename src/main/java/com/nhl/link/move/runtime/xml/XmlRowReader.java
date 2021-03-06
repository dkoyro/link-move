package com.nhl.link.move.runtime.xml;

import com.nhl.link.move.Row;
import com.nhl.link.move.RowAttribute;
import com.nhl.link.move.RowReader;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Iterator;

/**
 * @since 1.4
 */
public class XmlRowReader implements RowReader {
	private final RowAttribute[] attributes;
	private final NodeList nodes;

	public XmlRowReader(RowAttribute[] attributes, NodeList nodes) {
		this.attributes = attributes;
		this.nodes = nodes;
	}

	@Override
	public void close() {
		// no need to close anything
	}

	@Override
	public Iterator<Row> iterator() {
		return new Iterator<Row>() {
			private int i = 0;

			@Override
			public boolean hasNext() {
				return i < nodes.getLength();
			}

			@Override
			public Row next() {
				Node node = nodes.item(i++);
				return new XmlNodeRow(attributes, node);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
