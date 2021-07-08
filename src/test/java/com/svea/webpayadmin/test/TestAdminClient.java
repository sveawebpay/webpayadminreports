package com.svea.webpayadmin.test;

import static org.junit.Assert.fail;

import org.junit.Test;

import com.svea.webpay.common.conv.JsonUtil;
import com.svea.webpay.common.reconciliation.PaymentReport;
import com.svea.webpay.common.reconciliation.PaymentReportFactory;

public class TestAdminClient extends WebpayAdminClientTest {

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
				JsonUtil.buildGson().toJson(credentials)
				);
		
	}
	
}
