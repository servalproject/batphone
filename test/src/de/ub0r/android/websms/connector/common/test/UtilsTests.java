package de.ub0r.android.websms.connector.common.test;

import junit.framework.TestCase;
import de.ub0r.android.websms.connector.common.Utils;

public class UtilsTests extends TestCase {
	public void testCleanRecipient() {
		assertEquals("", Utils.cleanRecipient(null));
		assertEquals("", Utils.cleanRecipient(" \t\n-.()<>/\\"));
		assertEquals("123456789012", Utils
				.cleanRecipient("1 2\t3\n4-5.6(7)8<9>0/1\\2"));
		assertEquals("+4917312345", "+4917312345");
	}

	public void testGetRecipientsName() {
		assertEquals("f00", Utils.getRecipientsName("f00 <+4917312345>"));
	}

	public void testGetRecipientsNumber() {
		assertEquals("+4917312345", Utils
				.getRecipientsNumber("f00 <+4917312345>"));
		assertEquals("+4917312346", Utils.getRecipientsNumber("+4917312346"));
	}

	public void testParseRecipients() {
		final String in = "f00 <+4917312345>, bar, foo <+4917312346>, bar <+49 (173) 123-4 7>, +49 (173) 123-4 8";
		final String[] ret = Utils.parseRecipients(in);
		assertEquals("f00 <+4917312345>", ret[0]);
		assertEquals("bar, foo <+4917312346>", ret[1]);
		assertEquals("bar <+4917312347>", ret[2]);
		assertEquals("+49 (173) 123-4 8 <+4917312348>", ret[3]);
	}
}
