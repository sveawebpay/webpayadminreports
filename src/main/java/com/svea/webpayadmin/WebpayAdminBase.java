package com.svea.webpayadmin;

import java.net.URL;
import java.util.Map;


import javax.xml.ws.BindingProvider;

import com.svea.webpay.common.auth.SveaCredential;
import com.svea.webpayadminservice.client.AdminService;
import com.svea.webpayadminservice.client.Authentication;
import com.svea.webpayadminservice.client.IAdminService;

public class WebpayAdminBase {

	protected boolean isTest = false;
	protected boolean isPaymentPlan = false;
	
	protected static final String testWSAdminAddress = "https://webpayadminservicestage.svea.com/AdminService.svc";

	protected URL wsdlAdminURL = AdminService.WSDL_LOCATION;
	protected AdminService		adminService;
	protected IAdminService		adminServicePort;
	protected Authentication	auth;

	protected SveaCredential		cre;
	
	protected String				existingSpi;
	protected String				existingSoapFactory;
	
	public WebpayAdminBase() {
		
		// When running in a different JVM / container, the SPI and SOAP Factory might be some other
		// than below.
		existingSpi = System.getProperty("javax.xml.ws.spi.Provider");
		existingSoapFactory = System.getProperty("javax.xml.soap.SOAPFactory");

		System.setProperty("javax.xml.ws.spi.Provider","org.apache.cxf.jaxws.spi.ProviderImpl");
//		System.setProperty("javax.xml.soap.SOAPFactory", "com.sun.xml.messaging.saaj.soap.SAAJMetaFactoryImpl");
		
		// Read logging properties to remove warnings from 
		// com.sun.xml.ws.policy.EffectiveAlternativeSelector
		URL url = ClassLoader.getSystemResource("logging.properties");
		if (url!=null) {
			System.setProperty("java.util.logging.config.file", url.getFile());
		}
		
		adminService = new AdminService(wsdlAdminURL);
		adminServicePort = adminService.getAdminSoapService();
		
	}

	public void initCredentials(SveaCredential aCre) {
		
		this.cre = aCre;
		
		auth = new Authentication();
		auth.setUsername(cre.getUsername());
		auth.setPassword(cre.getPassword());
		
		if (SveaCredential.ACCOUNTTYPE_PAYMENTPLAN.equalsIgnoreCase(cre.getAccountType())) {
			isPaymentPlan = true;
		}
		
	}
	
	/**
	 * Return credentials set
	 * 
	 * @return		Credentials
	 */
	public SveaCredential getCredentials() {
		return cre;
	}
	
	public AdminService getAdminService() {
		return adminService;
	}

	public void setAdminService(AdminService adminService) {
		this.adminService = adminService;
	}

	public IAdminService getAdminServicePort() {
		return adminServicePort;
	}

	public void setAdminServicePort(IAdminService adminServicePort) {
		this.adminServicePort = adminServicePort;
	}

	public IAdminService getServicePort() {
		return adminServicePort;
	}
	
	public void closeClient() {
		System.setProperty("javax.xml.ws.spi.Provider", existingSpi);
		System.setProperty("javax.xml.soap.SOAPFactory", existingSoapFactory);
	}
	
	public void setPartPayment(boolean flag) {
		isPaymentPlan = flag;
	}
	
	public boolean isPartPayment() {
		return isPaymentPlan;
	}

	public Authentication getAuth() {
		return auth;
	}
	public void setAuth(Authentication auth) {
		this.auth = auth;
	}

	public String getEndpointAddress() {
		
		BindingProvider bp = (BindingProvider)getServicePort();
		Map<String,Object> map = bp.getRequestContext();
		Object endpointAddress = map.get(BindingProvider.ENDPOINT_ADDRESS_PROPERTY);

		return endpointAddress.toString();
	}
	
	/**
	 * True if test endpoints are to be used
	 * @return
	 */
	public boolean isTest() {
		return isTest;
	}
	
	public void setTest(boolean isTest) {
		this.isTest = isTest;
		if (isTest) {
				BindingProvider bp = (BindingProvider)getServicePort();
				bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, testWSAdminAddress);
		}
				
	}
	
}
