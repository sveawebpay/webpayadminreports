package com.svea.webpayadmin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.UnrecognizedOptionException;
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

	private java.util.Date fromDate = null;
	private java.util.Date untilDate = null;
	private File of = null;
	private File od = null;
	
	public static Logger log = org.slf4j.LoggerFactory.getLogger(WebpayAdminClientMain.class);	
	
	public static final String DEFAULT_TAXID = null;
	
	private List<PaymentReportFactory> 	clients;
	private List<SveaCredential>		credentials;
	
	private String	orgNo;
	private String	orgName;
	
	/**
	 * Loads configuration file containing credentials.
	 * 
	 * @param configfile		Path to configuration file. If the file isn't found
	 * 							it's treated as a resource and the classpath is searched.
	 * @throws Exception
	 */
	private void loadConfig(String configfile, boolean enrichAll) throws Exception {

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
	private void loadJsonConfig(String configFile, boolean enrichAll) throws Exception {
		
		ListOfSveaCredentials creds = JsonUtil.gson.fromJson(new FileReader(configFile), ListOfSveaCredentials.class);
		
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
	 */
	private void createConfig(String user, String pass, boolean enrich, boolean kickback) throws Exception {
		
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
	 * @return
	 * @throws Exception 
	 */
	private void createConfig(String accountNr, String user, String pass, String type, boolean enrich, boolean kickback) throws Exception {
		
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
			if (cre.getAccountNo()!=null && cre.getAccountNo().trim().length()>0) {
			
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
			
			of = new File(filePrefix + "-"+ JsonUtil.dfmt.format(fromDate) + (fromDate.equals(untilDate) ? "" : "-" + JsonUtil.dfmt.format(untilDate)));
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
	 * Main class for running the client
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		
		WebpayAdminClientMain main = new WebpayAdminClientMain();
		
		Options options = new Options();
		options.addOption("u", "user", true, "User supplied by Svea Ekonomi to fetch reports. Can be specified in config-file.");
		options.addOption("p", "pass", true, "Password supplied by Svea Ekonomi to fetch reports. Can be specified in config-file.");
		options.addOption("a", "account", true, "Specify account when using user as argument. Not mandatory");
		options.addOption("t", "type", true, "Specify type of account. Mandatory when account is used.");
		options.addOption("k", "kickback", false, "Read kickbacks on this account (as well as normal transactions)");
		options.addOption("format", true, "Select other format than json. Available formats are 'xlsx', 'csv', 'flat-json' and 'bgmax'");
		options.addOption("enrich", false, "Enrich data with as much information as possible.");
		options.addOption("outfile", true, "Output to file instead of stdout");
		options.addOption("outdir", true, "Output to directory (and use outfile name if present)");
		options.addOption("d","fromdate", true, "From date in format yyyy-MM-dd. If omitted, yesterday's date is used");
		options.addOption("untildate", true, "Until date in format yyyy-MM-dd");
		options.addOption("recipientorgnr", true, "Sets recipient org nr to this in output");
		options.addOption("recipientname", true, "Sets recipient name to this in output");
		options.addOption("c", "configfile", true, "Xml-configuration file where credentials are stored. Use a config file when detailed configuration is needed.");
		options.addOption("j", "jsonconfigfile", true, "Json-configuration file where credentials and settings are store. Use a config file when details configuration is needed.");
		options.addOption("noprune", false, "Return report type groups even if they are empty. Good to use to check what accounts are actually checked.");
		options.addOption("debug", true, "Enable debug");
		options.addOption("savejsonconfigfile", true, "Save credentials as json file");
		
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		
		String user = null;
		String pass = null;
		String accountNr = null;
		String type = null;
		String format = null;
		boolean enrich = false;
		boolean noprune = false;
		boolean kickback = false;
		String outfile = null;
		
		try {

			CommandLine cmd = parser.parse(options, args);

			if (cmd.hasOption("enrich")) {
				enrich = true;
			}
			
			if (cmd.hasOption("c")) {
				main.loadConfig(cmd.getOptionValue("c"), enrich);
			}
			
			if (cmd.hasOption("j")) {
				main.loadJsonConfig(cmd.getOptionValue("j"), enrich);
			}
		
			if (cmd.hasOption("u")) {
				user = cmd.getOptionValue("u");
			}

			if (cmd.hasOption("k")) {
				kickback = true;
			}
			
			
			if (cmd.hasOption("noprune")) {
				noprune = true;
			}
					
			if (cmd.hasOption("p"))
				pass = cmd.getOptionValue("p");

			if (cmd.hasOption("a"))
				accountNr = cmd.getOptionValue("a");
			
			if (cmd.hasOption("t")) 
				type = cmd.getOptionValue("t");
			
			if (!cmd.hasOption("c") && !cmd.hasOption("j")) {
				
				List<String> missingOpts = new ArrayList<String>();
				
				// Check that all other options are set
				if (user==null)
					missingOpts.add("If config file is not specified, user must be specified");
				if (pass==null)
					missingOpts.add("If config file is not specified, password must be specified");
				if (accountNr!=null && type==null) {
					StringBuffer str = new StringBuffer();
					str.append("If config file is not specified and account is specified, type must be specified.\n");
					str.append("  Possible types are:\n");
					str.append("  " + SveaCredential.ACCOUNTTYPE_INVOICE + "\n");
					str.append("  " + SveaCredential.ACCOUNTTYPE_PAYMENTPLAN + "\n");
					str.append("  " + SveaCredential.ACCOUNTTYPE_CREDITCARD + "\n");
					str.append("  " + SveaCredential.ACCOUNTTYPE_LOAN + "\n");
					str.append("  " + SveaCredential.ACCOUNTTYPE_ACCOUNT_CREDIT);
					missingOpts.add(str.toString());
				}
				
				if (missingOpts.size()>0)
					throw new MissingOptionException(missingOpts);
				else {
					// Nothing is missing
					if (accountNr!=null)
						main.createConfig(accountNr, user, pass, type, enrich, kickback);
					else
						main.createConfig(user, pass, enrich, kickback);
				}
			}
			
			if (cmd.hasOption("d")) {
				main.fromDate = JsonUtil.dfmt.parse(cmd.getOptionValue("d")); 
			}

			if (cmd.hasOption("format")) {
				format = cmd.getOptionValue("format");
				format = format.toLowerCase();
				if (!format.equals("xlsx") && 
					!format.equals("csv") && 
					!format.equals("flat-json") &&
					!format.equals("json") &&
					!format.equals("bgmax")) {
					throw new MissingOptionException("Available formats are: json, xlsx, csv, flat-json and bgmax. If format is omitted json is used."); 
				}
			}
			
			if (cmd.hasOption("outfile")) {
				outfile = cmd.getOptionValue("outfile");
				main.of = new File(outfile);
			}
			
			if (cmd.hasOption("outdir")) {
				String outdir = cmd.getOptionValue("outdir");
				main.od = new File(outdir);
			}
			
			if (cmd.hasOption("untildate")) {
				main.untilDate = JsonUtil.dfmt.parse(cmd.getOptionValue("untildate"));
			}
			if (main.fromDate==null) {
				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.DATE, -1);
				main.fromDate = cal.getTime();
			}
			if (main.untilDate==null)
				main.untilDate = main.fromDate;

			if (cmd.hasOption("recipientorgnr")) {
				main.orgNo = cmd.getOptionValue("recipientorgnr");
			}
			
			if (cmd.hasOption("recipientname")) {
				main.orgName = cmd.getOptionValue("recipientname");
			}
			
			if (cmd.hasOption("savejsonconfigfile")) {
				SveaCredential.saveCredentialsAsJson(main.credentials, cmd.getOptionValue("savejsonconfigfile"));
			} else {
				main.runQuery(format, noprune);
			}
			
		} catch (MissingOptionException me) {
			System.out.println(me.getMessage());
			formatter.printHelp("WebpayAdminClientMain", options);
		} catch (UnrecognizedOptionException uo) {
			System.out.println(uo.getMessage());
			formatter.printHelp("WebpayAdminClientMain", options);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
	}

}
