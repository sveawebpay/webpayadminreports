package com.svea.webpayadmin.test;

import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import com.svea.webpay.common.auth.ListOfSveaCredentials;
import com.svea.webpay.common.auth.SveaCredential;
import com.svea.webpayadmin.WebpayAdminClient;

public class TestGetCredentialsByIdentity extends WebpayAdminClientTest {


	@Test
	public void testGetCredentialsByIdentity() throws Exception {
		
		WebpayAdminClient client = new WebpayAdminClient();
		
		if (credentials==null) {
			fail("No credentials");
			return;
		}
		
		List<SveaCredential> credentialList = credentials.getCredentials();
		SveaCredential cr = credentialList.size()>0 ? credentialList.get(0) : null;
		if (cr==null) {
			fail("No credentials");
		}
		
		// TODO: Make this test read credentials from configuration file (not in source control).
		ListOfSveaCredentials creds = client.getCredentialsByIdentity(cr.getUsername(), cr.getPassword());
		if (creds==null || creds.getCredentials()==null) {
			System.out.println("No credentials");
		} else {
			for (SveaCredential cre : creds.getCredentials()) {
				System.out.println(cre.getAccountNo() + " : " + cre.getCurrency() + " : " + cre.getCountryCode() + " : " + cre.getAccountType());;
			}
		}
		
	}

}
