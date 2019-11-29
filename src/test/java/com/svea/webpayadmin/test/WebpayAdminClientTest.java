package com.svea.webpayadmin.test;

import static org.junit.Assert.fail;

import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.svea.webpay.common.auth.ListOfSveaCredentials;
import com.svea.webpay.common.auth.SveaCredential;
import com.svea.webpay.common.conv.JsonUtil;
import com.svea.webpay.common.reconciliation.PaymentReport;
import com.svea.webpay.common.reconciliation.PaymentReportFactory;
import com.svea.webpayadmin.report.WebpayAdminReportFactory;

public class WebpayAdminClientTest {

	private List<PaymentReportFactory> 	clients = new ArrayList<PaymentReportFactory>();
	private ListOfSveaCredentials	credentials = new ListOfSveaCredentials();	
	
	private java.sql.Date			startDate;
	
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

	@Test
	public void testAdminClient() {

		// Get invoices for clients
		if (clients.size()==0) 
			fail("No clients in configuration");
		
		String result = "";

		PaymentReport report = new PaymentReport();
		
		for (PaymentReportFactory c : clients) {

			try {
			
				System.out.println("Reading for client/account: " + c.getSveaCredential().toString());
				c.createBankStatementLines(report, startDate, startDate);
			
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}

		System.out.println(
				JsonUtil.PaymentReportToJson(report)
				);
		
		
	}

	@Test
	public void testJsonCredentials() {
		
		// Get invoices for clients
		if (credentials.getCredentials().size()==0) 
			fail("No clients in configuration");

		System.out.println(
				JsonUtil.gson.toJson(credentials)
				);
		
	}
}
