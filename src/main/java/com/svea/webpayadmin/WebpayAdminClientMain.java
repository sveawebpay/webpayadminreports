package com.svea.webpayadmin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.svea.webpay.common.auth.ListOfSveaCredentials;
import com.svea.webpay.common.auth.SveaCredential;
import com.svea.webpay.common.conv.JsonUtil;
import com.svea.webpay.common.reconciliation.PaymentReport;
import com.svea.webpay.common.reconciliation.PaymentReportFactory;
import com.svea.webpay.common.reconciliation.PaymentReportGroup;
import com.svea.webpay.common.reconciliation.conv.ReconInputConverter;
import com.svea.webpay.common.reconciliation.conv.ReconToBgMax;
import com.svea.webpay.common.reconciliation.conv.ReconToFlatExcel;
import com.svea.webpay.common.reconciliation.conv.ReconToFlatFormat;
import com.svea.webpay.common.reconciliation.conv.ReconToFlatJson;
import com.svea.webpayadmin.report.WebpayAdminReportFactory;
import com.svea.webpayadmin.report.WebpayCardReportFactory;
import com.svea.webpayadmin.report.WebpayCreditReportFactory;
import com.svea.webpayadmin.report.WebpayNoRiskReportFactory;

/**
 * Main class for creating consolidated reports for Svea Ekonomi.
 * 
 * 
 * @author Daniel Tamm
 *
 */
public class WebpayAdminClientMain {

	protected java.util.Date fromDate = null;
	protected java.util.Date untilDate = null;
	protected File of = null;
	protected File od = null;
	
	public static Logger log = org.slf4j.LoggerFactory.getLogger(WebpayAdminClientMain.class);	
	
	public static final String DEFAULT_TAXID = null;
	
	protected List<PaymentReportFactory> 	clients;
	protected List<SveaCredential>		credentials;
	
	protected String	orgNo;
	protected String	orgName;
	
	protected String	specifiedAccountNr;
	protected String	specifiedType;
	
	public static String[] allowedTypes = new String[] {
			SveaCredential.ACCOUNTTYPE_INVOICE,
			SveaCredential.ACCOUNTTYPE_PAYMENTPLAN,
			SveaCredential.ACCOUNTTYPE_CREDITCARD,
			SveaCredential.ACCOUNTTYPE_LOAN,
			SveaCredential.ACCOUNTTYPE_ADMIN,
			SveaCredential.ACCOUNTTYPE_ACCOUNT_CREDIT
	};
	
	/**
	 * Loads configuration file containing credentials.
	 * 
	 * @param configfile		Path to configuration file. If the file isn't found
	 * 							it's treated as a resource and the classpath is searched.
	 * @throws Exception
	 */
	protected void loadConfig(String configfile, boolean enrichAll) throws Exception {

		if (configfile.toLowerCase().endsWith(".json")) {
			credentials = SveaCredential.loadCredentialsFromJsonFile(configfile);
		} else if (configfile.toLowerCase().endsWith(".xml")) {
			credentials = SveaCredential.loadCredentialsFromXmlFile(configfile);
		}
		
		initClients(enrichAll);
		
	}
	
	/**
	 * Loads a json configuration file containing credentials and mappings. 
	 * 
	 * @param configFile
	 * @throws Exception
	 */
	protected void loadJsonConfig(String configFile, boolean enrichAll) throws Exception {
		
		ListOfSveaCredentials creds = JsonUtil.buildGson().fromJson(new FileReader(configFile), ListOfSveaCredentials.class);
		
		if (creds!=null && creds.getCredentials()!=null && !creds.getCredentials().isEmpty()) {
			credentials = creds.getCredentials();
			initClients(enrichAll);
		} else {
			System.out.println("No credentials found i file: " + configFile);
			System.exit(1);
		}
		
	}

	/**
	 * Creates configuration from supplied parameters.
	 * Use this to get all credentials connected to a specific identity user
	 * 
	 * @param	user	The identity user
	 * @param	pass	The password of the identity user
	 * @param 	enrich	If the enrich flag should be default
	 * @param 	kickback	If kickback should be detailed by default
	 */
	public void createConfig(String user, String pass, boolean enrich, boolean kickback) throws Exception {
		
		WebpayAdminClient client = new WebpayAdminClient();
		ListOfSveaCredentials creds = client.getCredentialsByIdentity(user, pass); 
		
		credentials = creds.getCredentials();
		if (enrich) {
			for (SveaCredential sc : credentials) {
				sc.setIncludeKickbacks(kickback);
				sc.setSkipEmail(false);
				sc.setSkipTaxId(false);
			}
		}
		
		initClients(enrich);		
	}
	
	/**
	 * Creates configuration from supplied parameters
	 * Use this to get a specific account type for a specific identity user.
	 * 
	 * The method tries to find the credential by first calling getCredentialsByIdentity
	 * and use that credential to start with (to get country code for instance).
	 * 
	 * @param 	accountNr	The accountNr to use.
	 * @param	user	The identity user
	 * @param	pass	The password of the identity user
	 * @param 	enrich	If the enrich flag should be default
	 * @param 	kickback	If kickback should be detailed by default
	 * 
	 * 
	 * @throws Exception	If something goes wrong. 
	 */
	public void createConfig(String accountNr, String user, String pass, String type, boolean enrich, boolean kickback) throws Exception {
		
		WebpayAdminClient client = new WebpayAdminClient();		
		ListOfSveaCredentials creds = client.getCredentialsByIdentity(user, pass);		
		
		credentials = new ArrayList<SveaCredential>();
		SveaCredential cred = null;
		// See if we can find the credential
		if (creds.getCredentials()!=null) {
			for (SveaCredential sc : creds.getCredentials()) {
				if (sc.getAccountNo().equals(accountNr)) {
					cred = sc;
					break;
				}
				
			}
		}
		
		if (cred==null)
			new SveaCredential(accountNr, user, pass, type);
		
		if (SveaCredential.ACCOUNTTYPE_INVOICE.equalsIgnoreCase(type)) {
			// Include card payments by default
			cred.setIncludeCardPayments(true);
		}
		cred.setIncludeKickbacks(kickback);
		cred.setSkipEmail(false);
		cred.setSkipTaxId(false);

		// Create default account map since none is supplied.
		cred.createDefaultAccountMap();
		
		credentials.add(cred);
		
		initClients(enrich);
		
	}
	
	public List<SveaCredential> getCredentials() {
		return credentials;
	}

	public void setCredentials(List<SveaCredential> credentials) {
		this.credentials = credentials;
	}
	
	public java.util.Date getFromDate() {
		return fromDate;
	}

	public void setFromDate(java.util.Date fromDate) {
		this.fromDate = fromDate;
	}

	public java.util.Date getUntilDate() {
		return untilDate;
	}

	public void setUntilDate(java.util.Date untilDate) {
		this.untilDate = untilDate;
	}

	/**
	 * Initializes the clients from the given credentials.
	 * 
	 * The credentials determine what kinds of report clients are created.
	 * 
	 */
	public void initClients(boolean enrichAll) {

		clients = new ArrayList<PaymentReportFactory>();
		PaymentReportFactory client = null;
		
		for (SveaCredential cre : credentials) {
			
			if (orgNo!=null && cre.getOrgNo()==null) {
				cre.setOrgNo(orgNo);
			}
			if (orgName!=null && cre.getOrgName()!=null) {
				cre.setOrgName(orgName);
			}
			
			if (enrichAll) {
				cre.setEnrichFromInvoice(true);
			}
			
			client = null;
			if (cre.getAccountNo()!=null && cre.getAccountNo().trim().length()>0 
					&& (specifiedAccountNr==null || cre.getAccountNo().equals(specifiedAccountNr))
					&& (specifiedType==null || cre.getAccountType().equalsIgnoreCase(specifiedType))
					) {
			
				if (SveaCredential.ACCOUNTTYPE_INVOICE.equals(cre.getAccountType()) || SveaCredential.ACCOUNTTYPE_PAYMENTPLAN.equals(cre.getAccountType())) {
					client = new WebpayAdminReportFactory().init(cre);
				} else if (SveaCredential.ACCOUNTTYPE_CREDITCARD.equals(cre.getAccountType())) {
					client = new WebpayCardReportFactory().init(cre);
				} else if (SveaCredential.ACCOUNTTYPE_ACCOUNT_CREDIT.equals(cre.getAccountType())) {
					client = new WebpayCreditReportFactory().init(cre);
				} else if (SveaCredential.ACCOUNTTYPE_ADMIN.equals(cre.getAccountType())) {
					client = new WebpayNoRiskReportFactory().init(cre);
				}
				
			}
			
			if (client!=null)
				clients.add(client);
			
		}
		
		
	}
	
	/**
	 * Fills the report using clients.
	 * 
	 * @return
	 */
	public PaymentReport fillReport() {
		
		PaymentReport report = new PaymentReport();
		report.setTaxId(orgNo);
		report.setOrgName(orgName);
		
		List<PaymentReportGroup> groups = null;
		
		for (PaymentReportFactory c : clients) {

			try {
			
				log.debug("Reading for " + c.getSveaCredential().toString());
				groups = c.createBankStatementLines(report, fromDate, untilDate);
				if (groups!=null) {
					// Check for fee settings
					if (c.getSveaCredential().isIgnoreFees()) {
						for (PaymentReportGroup g : groups) {
							g.clearFees();
						}
					}
					// Check for enrichment options
					if (c.getSveaCredential().isEnrichFromInvoice()) {
						WebpayAdminClient enrichClient = new WebpayAdminClient();
						enrichClient.initCredentials(c.getSveaCredential());
						for (PaymentReportGroup group : groups) {
							enrichClient.enrichFromInvoice(
									group.getPaymentTypeReference(),
									group.getPaymentReportDetail(),
									true,	// enrich all since this is a group-wide option
									c.getSveaCredential().isSkipTaxId(), 
									c.getSveaCredential().isSkipEmail());
						}
					}
				}
			
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}

		return report;
		
	}
	
	/**
	 * Runs the report queries with the given clients / credentials.
	 * Note: The class must be initialized with credentials and initClients must have been called.
	 * 
	 * @param	format	The output format of the file.
	 * @param   noPrune	Set to true if the json-file should not be pruned before saved.
	 */
	public void runQuery(String format, boolean noPrune) {

		// Fetch the report
		PaymentReport report = fillReport();
		if (!noPrune) {
			report.pruneReport();
		}
		
		// Check for outdir and file
		if (od!=null && of==null) {
			
			if (report.getTaxId()==null) {
				report.setTaxId(DEFAULT_TAXID);
			}
			String filePrefix = report.getTaxId();
			// If there's no tax id, use username as filePrefix.
			if (filePrefix==null) {
				for (SveaCredential cr : credentials) {
					if (cr.getUsername()!=null && cr.getUsername().trim().length()>0) {
						filePrefix = cr.getUsername();
						break;
					}
				}
			}
			if (filePrefix==null) {
				filePrefix = "ReconciliationReport";
			}
			
			of = new File(filePrefix + "-"+ JsonUtil.getDateFormat().format(fromDate) + (fromDate.equals(untilDate) ? "" : "-" + JsonUtil.getDateFormat().format(untilDate)));
		}
		
		if (od!=null && of!=null) {
			of = new File(od.getAbsolutePath() + File.separator + of.getName());
		}

		ReconInputConverter converter = null;
		
		if (format==null || format.equals("json")) {
		
			// Convert report to json-format
			String output = JsonUtil.PaymentReportToJson(report); 
	
			PrintStream out = null; 
			
			if (of!=null) {
				if (!of.getAbsolutePath().toLowerCase().endsWith(".json")) {
					of = new File(of.getAbsolutePath() + ".json");
				}
				try {
					out = new PrintStream(of);
				} catch (FileNotFoundException fe) {
					fe.printStackTrace(System.err);
				}
			} else {
				out = System.out;
			}
			out.println(output);
			if (out!=System.out) {
				out.close();
				System.out.println(of.getAbsolutePath());
			}
			
		} else {
			
			if (format.equals("csv")){
				converter = new ReconToFlatFormat();
			} else if (format.equals("xlsx")) {
				converter = new ReconToFlatExcel();
			} else if (format.equals("flat-json")) {
				converter = new ReconToFlatJson();
			} else if (format.equals("bgmax")) {
				converter = new ReconToBgMax();
			} else {
				System.err.print("Unknown format " + format);
				System.exit(1);
			}
			
			converter.setOutFile(of);
			
			try {
				List<StringBuffer> result = converter.convertFromRecon(report);
				for (StringBuffer s : result) {
					System.out.println(s.toString());
				}
			} catch (Exception e) {
				
			}
			
		}
		
	}

	/**
	 * Prints allowed types to a stringbuffer
	 * 
	 * @return	A string buffer with allowed types
	 */
	protected static StringBuffer printAllowedTypes() {
		StringBuffer str = new StringBuffer();
		str.append("  Possible types are:");
		for (String s : allowedTypes) {
			str.append("\n  " + s);
		}
		return str;
	}

	/**
	 * Returns true if the type is allowed.
	 * 
	 * @param t		The type to compare
	 * @return		If the type is an allowed type
	 */
	protected static boolean isAllowedType(String t) {
		
		if (t==null || t.trim().length()==0) return false;
		for (String s : allowedTypes) {
			if (t.equalsIgnoreCase(s)) {
				return true;
			}
		}
		return false;
		
	}
	

}
