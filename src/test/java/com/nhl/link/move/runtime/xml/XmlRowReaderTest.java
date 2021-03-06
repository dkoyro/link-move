package com.nhl.link.move.runtime.xml;


import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.Iterator;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.nhl.link.move.Row;
import com.nhl.link.move.RowAttribute;
import com.nhl.link.move.runtime.xml.XmlRowReader;

public class XmlRowReaderTest {

	private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<tests>"
			+ "  <test><k1>v11</k1><k2>v12</k2></test>  <test><k1>v21</k1><k2>v22</k2></test>"
			+ "  <test><k1>v31</k1><k2>v32</k2></test></tests>";

	private XmlRowReader xmlRowReader;
	private RowAttribute[] attributes;
	private NodeList nodes;

	@Before
	public void setUpXmlRowReader() throws Exception {
		configAttributes();
		configNodes();
		xmlRowReader = new XmlRowReader(attributes, nodes);
	}

	private void configAttributes() {
		attributes = new RowAttribute[2];
		attributes[0] = new RowAttribute(String.class, "k1", "k1", 0);
		attributes[1] = new RowAttribute(String.class, "k2", "k1", 0);
	}

	private void configNodes() throws Exception {
		XPath xPath = XPathFactory.newInstance().newXPath();
		nodes = (NodeList) xPath
				.evaluate("/tests/test", new InputSource(new StringReader(XML)), XPathConstants.NODESET);
	}

	@Test
	public void testIterator() throws Exception {
		Iterator<Row> rowIterator = xmlRowReader.iterator();
		int i = 0;

		while (rowIterator.hasNext()) {
			i++;
			Row row = rowIterator.next();
			assertArrayEquals(attributes, row.attributes());
			assertEquals("v" + i + "1", (String) row.get(attributes[0]));
			assertEquals("v" + i + "2", (String) row.get(attributes[1]));
		}

		assertEquals(3, i);
	}
}