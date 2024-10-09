package com.svea.webpayadmin.report;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.XMLGregorianCalendar;

import com.svea.webpay.common.auth.SveaCredential;
import com.svea.webpay.common.reconciliation.FeeDetail;
import com.svea.webpay.common.reconciliation.PaymentReportDetail;
import com.svea.webpay.common.reconciliation.PaymentReportGroup;
import com.svea.webpay.common.reconciliation.VatType;
import com.svea.webpayadmin.WebpayAdminBase;
import com.svea.webpayadmin.WebpayAdminClient;
import com.svea.webpayadminservice.client.AccountingReportRow;
import com.svea.webpayadminservice.client.FinancialReportHeader;
import com.svea.webpayadminservice.client.FinancialReportRow;
import com.svea.webpayadminservice.client.GetAccountingReportRequest;
import com.svea.webpayadminservice.client.GetAccountingReportResponse;
import com.svea.webpayadminservice.client.GetAccountingReportResponse2;
import com.svea.webpayadminservice.client.GetFinancialReportRequest;
import com.svea.webpayadminservice.client.GetFinancialReportResponse;
import com.svea.webpayadminservice.client.GetFinancialReportResponse2;

public class ReportFactoryBase {

	protected WebpayAdminBase svea;
	protected SveaCredential cre;
	
	/**
	 * Empty constructor
	 */
	public ReportFactoryBase() {
		super();
	}
	
	public SveaCredential getSveaCredential() {
		return cre;
	}
	
	
	/**
	 * Reads an accounting report of a specific date
	 * 
	 * @param date
	 * @return
	 * @throws DatatypeConfigurationException
	 */
	public List<AccountingReportRow> getAccountingReport(java.sql.Timestamp date)
			throws Exception {

		GetAccountingReportRequest request = new GetAccountingReportRequest();
		if (cre.getAccountNo()!=null) {
			request.setClientId(new Integer(cre.getAccountNo()));
		}

		request.setAuthentication(svea.getAuth());
		XMLGregorianCalendar acctDate = convert(date);

		request.setFromDate(acctDate);
		request.setToDate(acctDate);
		GetAccountingReportResponse response = svea.getServicePort()
				.getAccountingReport(request);

		if (response==null)
			throw new Exception("No response.");
		
		if (response.getReportRows()!=null)
			return response.getReportRows().getAccountingReportRow();
		else {
			throw new Exception(response.getErrorMessage());
		}

	}
	
	public XMLGregorianCalendar convert(java.sql.Timestamp date)
			throws DatatypeConfigurationException {

		GregorianCalendar cal = new GregorianCalendar();
		cal.setTimeInMillis(date.getTime());
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.HOUR_OF_DAY, 0);

		return javax.xml.datatype.DatatypeFactory.newInstance()
				.newXMLGregorianCalendar(cal);

	}
	
	/**
	 * Get financial reports and sets country code from report header.
	 * 
	 * @param date
	 * @return
	 * @throws DatatypeConfigurationException
	 */
	public GetFinancialReportResponse getFinancialReport(java.sql.Timestamp date)
			throws DatatypeConfigurationException {

		GetFinancialReportRequest request = new GetFinancialReportRequest();
		request.setClientId(new Integer(cre.getAccountNo()));
		request.setAuthentication(svea.getAuth());
		XMLGregorianCalendar acctDate = convert(date);

		request.setFromDate(acctDate);
		request.setToDate(acctDate);
		GetFinancialReportResponse response = svea.getServicePort()
				.getFinancialReport(request);

		// Set country code
		FinancialReportHeader fh = response.getReportHeader();
		if (fh!=null && fh.getAddress()!=null) {
			cre.setCountryCode(fh.getAddress().getCountryCode());
		}

		return response;

	}

	/**
	 * Reads amount that Svea reports to have landed on the bankaccount
	 * 
	 * 
	 * @param date
	 * @return
	 * @throws Exception
	 */
	public double getAmountOnBankFromSvea(java.sql.Timestamp date)
			throws Exception {

		double result = 0d;

		if (!svea.isPartPayment()) {

			// Invoices
			List<AccountingReportRow> rows = getAccountingReport(date);

			for (AccountingReportRow r : rows) {
				if (r.getAccountNumber() == Integer.parseInt(cre.getAccountMap().get(FeeDetail.ACCTTYPE_CASH)) || 
					r.getId()==FeeDetail.AccountingReportRowIdMap.get(FeeDetail.ACCTTYPE_CASH)) {
					result = r.getDebit().subtract(r.getCredit()).doubleValue();
				}
			}

		} else {

			// Payment plan
			GetFinancialReportResponse fr = getFinancialReport(date);
			List<FinancialReportRow> rows = fr.getReportRows()!=null ? fr.getReportRows().getFinancialReportRow() : null;

			if (rows == null) {
				System.err.println("Could not read amount on bank for date "
						+ date);
				return 0.0;
			}

			//
			int sumRowId = 152; // Default
			// Check for Finland
			// if ("27314".equals(m_authInfo.getAccountNo())) {
			// sumRowId = 150;
			// };

			for (FinancialReportRow r : rows) {
				if (r.getRowId() == sumRowId) {
					result = r.getDebit().subtract(r.getCredit()).doubleValue();
					/*
					 * if (result==0.0) { result = r.getDebit().doubleValue(); }
					 */
				}
			}

		}

		return result;
	}
	
	/**
	 * This method calls the FinancialReport and creates deviation records from the 
	 * content of the FinancialReport.
	 * 
	 * ReportRows with RowId = 0 are the primary target for manual deviations.
	 * Other non manual deviations are due dates and annual fees.
	 * 
	 * @param gr
	 * @param date
	 * @throws Exception
	 */
	protected void fillDeviations(PaymentReportGroup gr, java.sql.Timestamp date) throws Exception {
		
		GetFinancialReportResponse frr = getFinancialReport(date);
		FinancialReportHeader fh = frr.getReportHeader();
		List<FinancialReportRow> result = frr.getReportRows()!=null ? frr.getReportRows().getFinancialReportRow() : null;
		
		PaymentReportDetail d = null;
		List<PaymentReportDetail> listToEnrich = new ArrayList<PaymentReportDetail>();
		
		boolean payoutFound = false;
		
		if (result==null) {
			return;
		}
		
		for (FinancialReportRow r : result) {
			
			if (r.getRowId()==152 || // SEK paid out (invoice / partpayment)
				r.getRowId()==151 || // NOK
				r.getRowId()==150 || // EUR
				r.getRowId()==167    // SEK paid out (account-credit)
				) {
				// Summary / paidout row for part payments in SEK
				gr.setTotalReceivedAmt(r.getDebit().subtract(r.getCredit()).doubleValue());
				payoutFound = true;
				continue;
			}
			
			if (r.getRowId()==168 || // SEK withheld account-credit (tillgodo)
				r.getRowId()==107	 // SEK, NOK withheld invoice, paymentplan (tillgodo)
					) 	 
			{
				// Summary / paidout row for part payments in SEK
				if (fh.getClientTypeId()==24 || fh.getClientTypeId()==25) { // Part payments, account credit // TODO: Is this a bug or feature
					gr.setEndingBalance(r.getCredit().subtract(r.getDebit()).doubleValue());
				} else {
					gr.setEndingBalance(r.getDebit().subtract(r.getCredit()).doubleValue());
				}
				gr.setTotalReceivedAmt(0D);
				payoutFound = true;
				continue;
			}
			
			// AVGÅR TIDIGARE REDOVISNING
			if (r.getRowId()==106 ||		// 106 = Withheld clearance SEK / NOK
				r.getRowId()==163 			// 163 = Withheld clearance SEK (account-credit)
					) 
			{
				gr.setOpeningBalance(r.getCredit().subtract(r.getDebit()).doubleValue());
				continue;
			}
			
			if (
					r.getRowId()==18 ||		// Reminder's fee
					r.getRowId()==20 ||		// Monthly fee
					r.getRowId()==41 ||		// Email invoice without specifications
					r.getRowId()==53 ||		// Due date without specifications
					r.getRowId()==99 ||		// Revenue interest
					r.getRowId()==100 ||		// Deposit account
					r.getRowId()==39		// Fee for admin invoices (fakturaservice)
					) {				
			
				FeeDetail ddf = new FeeDetail();
				ddf.setFee(r.getDebit().subtract(r.getCredit()).doubleValue());
				if (r.getRowId()==53)
					ddf.setFeeType(FeeDetail.FEETYPE_DUEDATE);
				else if (r.getRowId()==41) 
					ddf.setFeeType(FeeDetail.FEETYPE_EMAILINVOICE);
				else if (r.getRowId()==99)
					ddf.setFeeType(FeeDetail.REVENUE_INTEREST);
				else if (r.getRowId()==39)
					ddf.setFeeType(FeeDetail.FEETYPE_ADM);
				else if (r.getRowId()==18)
					ddf.setFeeType(FeeDetail.FEETYPE_REMINDER);
				else if (r.getRowId()==20)
					ddf.setFeeType(FeeDetail.FEETYPE_SUBSCRIPTION);
				else if (r.getRowId()==100) {
					ddf.setFeeType(FeeDetail.ACCTTYPE_DEPOSIT);
				}
				
				ddf.calculateVat(VatType.getVatRate(cre.getCountryCode(), 
													ddf.getFeeType(), 
													cre.getAccountType(),
													date, cre.isCompany()));
				gr.addOtherFee(ddf);
				
			}
			
			if (r.getRowId()==0  				// Free comments / adjustments 
					|| r.getRowId()==125		// 125 = Annual Fee?
					|| r.getRowId()==169		// 169 = Manual regression SEK
								) {  
				d = null;
				d = PaymentReportDetail.parseDeviation(
						cre.getCountryCode(), 
						cre.getAccountType(), 
						gr.getReconciliationDateAsDate(),
						cre.isCompany(), 
						r.getDescription(), 
						r.getCredit().subtract(r.getDebit()).doubleValue(), 
						r.getCount(), 
						r.getRowId());
				
				if (d!=null && d.getPaidAmt()!=null) {
					gr.addDetail(d);
					// Since this is a deviation and summarized as such (on account 9999), add as other fee as well to cancel out
					if (r.getRowId()==0) {
						FeeDetail deviation = new FeeDetail();
						deviation.setFee(-FeeDetail.getFeeSum(d.getFees()));
						deviation.setFeeVat(0d);  // Don't use VAT for deviation cancellation
						deviation.setFeeType(FeeDetail.FEETYPE_DEVIATIONS);
						gr.addOtherFee(deviation);
					}
					
					if (d.getInvoiceId()!=null || d.getOrderId()!=null)
						listToEnrich.add(d);
				}
				
			}
			
		}
		
		WebpayAdminClient enrichClient = new WebpayAdminClient();
		enrichClient.initCredentials(cre);
		
		if (cre.getAccountType().equalsIgnoreCase(SveaCredential.ACCOUNTTYPE_INVOICE)) {
			
			enrichClient.enrichFromInvoice(gr.getPaymentTypeReference(), listToEnrich, false, cre.isSkipTaxId(), cre.isSkipEmail());
			
		} else if (cre.getAccountType().equalsIgnoreCase(SveaCredential.ACCOUNTTYPE_PAYMENTPLAN)) {
			
			enrichClient.enrichFromPaymentPlan(gr.getPaymentTypeReference(), listToEnrich, false, cre.isSkipTaxId());
			
		}
		
		// If no payout is found
		if (!payoutFound) {
			gr.setTotalReceivedAmt(0D);
		}
		
	}

	/**
	 * Tries to figure out fees from the accounting suggestion and add VAT to the group.
	 * NOTE! Be careful not to overlap these fees with the fees fetched from fillDeviations. The best is to 
	 * try and resolve the fee / amounts using fillDeviations.
	 * If a check is commented out, it has been moved to fillDeviations.
	 * 
	 * @param gr				The group to work on.
	 * @param date				The date this group belongs to.
	 * @return					A list of fees (if applicable).
	 * @throws Exception		If something goes wrong,
	 */
	public List<FeeDetail> getOtherFeesFromAccountingSuggestion(PaymentReportGroup gr, Timestamp date) throws Exception {
		
		// Get a treemap of the accounting suggestion represented as fees
		Map<String,FeeDetail> fees = getFeeSummaries(date);

		// Create an empty list with other fees
		List<FeeDetail> otherFees = new ArrayList<FeeDetail>();
		
		// Pick out fees from the accounting suggestion
		for (FeeDetail f : fees.values()) {
			
			if ("0".equals(f.getAccountNr()) && f.getFee()==0d)
				continue;

			// Balance accounts
			
			// Don't include cash or receivables accounts
			if (f.getAccountNr().equals(cre.getAccountMap().get(FeeDetail.ACCTTYPE_CASH)) ||
				f.getAccountNr().equals(cre.getAccountMap().get(FeeDetail.ACCTTYPE_RECEIVABLES)))
				continue;
			
			// Check for VAT
			if (f.getAccountNr().equals(cre.getAccountMap().get(FeeDetail.ACCTTYPE_VAT))) {
				// Add to total VAT
				gr.setTotalVatAmt(gr.getTotalVatAmt()+f.getFee());
				continue;
			}
			
			// Check for Debt
			// if (f.getAccountNr().equals(cre.getAccountMap().get(FeeDetail.ACCTTYPE_DEBT))) {
			//   f.setFeeType(FeeDetail.ACCTTYPE_DEBT);
			//  	otherFees.add(f);
			//	continue;
			// }
			
			// Check for deposit to "depositionskonto"
			// if (f.getAccountNr().equals(cre.getAccountMap().get(FeeDetail.ACCTTYPE_DEPOSIT))) {
			//	f.setFeeType(FeeDetail.ACCTTYPE_DEPOSIT);
			//	otherFees.add(f);
			//	continue;
			// }

			// Check for DEVIATIONS
			// if (f.getAccountNr().equals(cre.getAccountMap().get(FeeDetail.ACCTTYPE_DEVIATIONS))) {
			//	f.setFeeType(FeeDetail.FEETYPE_DEVIATIONS);
			//	otherFees.add(f);
			//	continue;
			// }
			
			// Accounts on P/L accounts
			
			// Check for POSTAGE
			if (f.getAccountNr().equals(cre.getAccountMap().get(FeeDetail.FEETYPE_POSTAGE))) {
				f.setFeeType(FeeDetail.FEETYPE_POSTAGE);
				f.calculateVat(VatType.getVatRate(cre.getCountryCode(), f.getFeeType(), cre.getAccountType(), date, cre.isCompany()));
				otherFees.add(f);
				continue;
			}
			
			// Check for Revenue Reminder
			if (f.getAccountNr().equals(cre.getAccountMap().get(FeeDetail.REVENUE_REMINDER))) {
				f.setFeeType(FeeDetail.REVENUE_REMINDER);
				f.calculateVat(VatType.getVatRate(cre.getCountryCode(), f.getFeeType(), cre.getAccountType(), date, cre.isCompany()));
				otherFees.add(f);
				continue;
			}
			
			// If admin invoices, don't check any more cost accounts, but break/continue here
			if (SveaCredential.ACCOUNTTYPE_ADMIN.equals(cre.getAccountType())) {
				continue;
			}
			
			// Check for ADM
			if (f.getAccountNr().equals(cre.getAccountMap().get(FeeDetail.FEETYPE_ADM))) {
				f.setFeeType(FeeDetail.FEETYPE_ADM);
				f.calculateVat(VatType.getVatRate(cre.getCountryCode(), f.getFeeType(), cre.getAccountType(), date, cre.isCompany()));
				otherFees.add(f);
				continue;
			}
			
			// Check for CREDIT
			if (f.getAccountNr().equals(cre.getAccountMap().get(FeeDetail.FEETYPE_CREDIT))) {
				f.setFeeType(FeeDetail.FEETYPE_CREDIT);
				f.calculateVat(VatType.getVatRate(cre.getCountryCode(), f.getFeeType(), cre.getAccountType(), date, cre.isCompany()));
				otherFees.add(f);
				continue;
			}
				
		}

		return otherFees;
		
	}
	
	/**
	 * Reads amount that Svea reports to have landed on the bankaccount.
	 * Adjusts account mappings if necessary.
	 * 
	 * @param date
	 * @return
	 * @throws Exception
	 */
	private Map<String,FeeDetail> getFeeSummaries(java.sql.Timestamp date)
			throws Exception {

		Map<String,FeeDetail> result = new TreeMap<String,FeeDetail>();

		// Invoices
		List<AccountingReportRow> rows = getAccountingReport(date);
		
		// Check for custom accounts
		for (AccountingReportRow r : rows) {
			if (r.getId()==FeeDetail.AccountingReportRowIdMap.get(FeeDetail.ACCTTYPE_CASH)) {
				cre.addAccountMapping(FeeDetail.ACCTTYPE_CASH, Long.toString(r.getAccountNumber()));
			}
			if (r.getId()==FeeDetail.AccountingReportRowIdMap.get(FeeDetail.ACCTTYPE_RECEIVABLES)) {
				cre.addAccountMapping(FeeDetail.ACCTTYPE_RECEIVABLES, Long.toString(r.getAccountNumber()));
			}
			if (r.getId()==FeeDetail.AccountingReportRowIdMap.get(FeeDetail.ACCTTYPE_VAT)) {
				cre.addAccountMapping(FeeDetail.ACCTTYPE_VAT, Long.toString(r.getAccountNumber()));
			}
		}
		
		FeeDetail f = null;
		String acctType;
		
		Map<String,String> reverseAccountMap = cre.getReverseAccountMap();
		
		for (AccountingReportRow r : rows) {
			
			if (r.getAccountNumber()==0)
				continue;
			
			f = result.get(Long.toString(r.getAccountNumber()));
			if (f==null) {
				f = new FeeDetail();
				f.setAccountNr(Long.toString(r.getAccountNumber()));
				f.setFee(0d);
				result.put(f.getAccountNr(), f);
				acctType = reverseAccountMap.get(f.getAccountNr());
				if (acctType!=null) {
					f.setFeeType(acctType);
				} 
			}
			f.setFee(f.getFee() + r.getDebit().subtract(r.getCredit()).doubleValue());
		}

		
		return result;
	}
	
}
