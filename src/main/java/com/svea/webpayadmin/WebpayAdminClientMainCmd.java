package com.svea.webpayadmin;

import java.io.File;
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

import com.svea.webpay.common.auth.SveaCredential;
import com.svea.webpay.common.conv.JsonUtil;

public class WebpayAdminClientMainCmd extends WebpayAdminClientMain {

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
		options.addOption("t", "type", true, "Specify type of account.");
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

			List<String> missingOpts = new ArrayList<String>();
			
			if (!cmd.hasOption("c") && !cmd.hasOption("j")) {
				
				// Check that all other options are set
				if (user==null)
					missingOpts.add("If config file is not specified, user must be specified");
				if (pass==null)
					missingOpts.add("If config file is not specified, password must be specified");
				if (accountNr!=null && type==null || (type!=null && !isAllowedType(type))) {
					StringBuffer str = new StringBuffer();
					str.append("If config file is not specified and account is specified, type must be specified.\n");
					str.append(printAllowedTypes().toString());
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
			} else {
				// We have a config file, we can still specify accountNr and/or type
				if (type!=null) {
					if (!isAllowedType(type)) {
						missingOpts.add("Invalid type. Allowed types are " + printAllowedTypes().toString());
					}
					main.specifiedType = type;
				}
				
				if (accountNr!=null) {
					main.specifiedAccountNr = accountNr;
				}
				
				if (cmd.hasOption("c")) {
					main.loadConfig(cmd.getOptionValue("c"), enrich);
				}
				
				if (cmd.hasOption("j")) {
					main.loadJsonConfig(cmd.getOptionValue("j"), enrich);
				}
				
			}
			
			if (cmd.hasOption("d")) {
				main.fromDate = JsonUtil.getDateFormat().parse(cmd.getOptionValue("d")); 
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
				main.untilDate = JsonUtil.getDateFormat().parse(cmd.getOptionValue("untildate"));
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
				if (missingOpts.size()>0)
					throw new MissingOptionException(missingOpts);
				else {
					main.runQuery(format, noprune);
				}
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
