package com.svea.webpayadmin.test;

import static org.junit.Assert.fail;

import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.junit.After;
import org.junit.Before;

import com.svea.webpay.common.auth.ListOfSveaCredentials;
import com.svea.webpay.common.auth.SveaCredential;
import com.svea.webpay.common.reconciliation.PaymentReportFactory;
import com.svea.webpayadmin.report.WebpayAdminReportFactory;

public class WebpayAdminClientTest {

	protected List<PaymentReportFactory> 	clients = new ArrayList<PaymentReportFactory>();
	protected ListOfSveaCredentials	credentials = new ListOfSveaCredentials();	
	
	protected java.sql.Date			startDate;
	
	private String TEST_CONFIG = "config-test.xml";
	
	@Before
	public void setUp() throws Exception {
		
		URL url = ClassLoader.getSystemResource(TEST_CONFIG);
		
		if (url==null) {
			fail("The file " + TEST_CONFIG + "  must exist in classpath for unit tests to work.\n" +
				 "Copy the file config-template.xml in src/test/resources to config-test.xml and fill in login details.");
		}

		credentials.setCredentials(SveaCredential.loadCredentialsFromXmlFile(TEST_CONFIG));
		PaymentReportFactory client = null;
		
		for (SveaCredential cre : credentials.getCredentials()) {
			
			if (cre.getAccountNo()!=null && cre.getAccountNo().trim().length()>0) {
			
				client = new WebpayAdminReportFactory().init(cre);
				
			}
			
			clients.add(client);
			
		}

		// Set startDate to last monday
		Calendar cal = Calendar.getInstance();
		while(cal.get(Calendar.DAY_OF_WEEK)!=Calendar.MONDAY) {
			cal.add(Calendar.DAY_OF_WEEK, -1);
		}
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		startDate = new java.sql.Date(cal.getTimeInMillis());
		
	}

	@After
	public void tearDown() throws Exception {
	}

}
