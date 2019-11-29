package com.svea.webpayadmin.test;

import org.junit.Before;
import org.junit.Test;

import com.svea.webpay.common.auth.ListOfSveaCredentials;
import com.svea.webpay.common.auth.SveaCredential;
import com.svea.webpayadmin.WebpayAdminClient;

public class TestGetCredentialsByIdentity {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void test() throws Exception {
		
		WebpayAdminClient client = new WebpayAdminClient();
		// TODO: Make this test read credentials from configuration file (not in source control).
		ListOfSveaCredentials creds = client.getCredentialsByIdentity("USER", "PASS");
		if (creds==null || creds.getCredentials()==null) {
			System.out.println("No credentials");
		} else {
			for (SveaCredential cr : creds.getCredentials()) {
				System.out.println(cr.getAccountNo() + " : " + cr.getCurrency() + " : " + cr.getCountryCode() + " : " + cr.getAccountType());;
			}
		}
		
	}

}
