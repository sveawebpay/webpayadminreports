package com.svea.webpayadmin.report;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.xml.datatype.XMLGregorianCalendar;

import com.svea.webpay.common.auth.SveaCredential;
import com.svea.webpay.common.conv.JsonUtil;
import com.svea.webpay.common.reconciliation.FeeDetail;
import com.svea.webpay.common.reconciliation.PaymentReport;
import com.svea.webpay.common.reconciliation.PaymentReportDetail;
import com.svea.webpay.common.reconciliation.PaymentReportFactory;
import com.svea.webpay.common.reconciliation.PaymentReportGroup;
import com.svea.webpay.common.reconciliation.VatType;
import com.svea.webpayadmin.WebpayAdminBase;
import com.svea.webpayadminservice.client.AccountCreditRegressReportDataRow;
import com.svea.webpayadminservice.client.AccountCreditRegressReportRequest;
import com.svea.webpayadminservice.client.AccountCreditRegressReportResponse;
import com.svea.webpayadminservice.client.AccountingAgreementType25ReportDataRow;
import com.svea.webpayadminservice.client.AccountingAgreementType25ReportRequest;
import com.svea.webpayadminservice.client.AccountingAgreementType25ReportResponse;
import com.svea.webpayadminservice.client.ArrayOfAccountCreditRegressReportDataRow;
import com.svea.webpayadminservice.client.ArrayOfAccountingAgreementType25ReportDataRow;
import com.svea.webpayadminservice.client.Authentication;

/**
 * Client to read payment reconciliation information from Svea Invoices (normal invoices and payment plan).
 * 
 * This client uses the SveaWebPay Administration Service API v 1.30.
 * 
 * @author Daniel Tamm
 *
 */

public class WebpayCreditReportFactory extends ReportFactoryBase implements PaymentReportFactory {
	
	/**
	 * Empty constructor
	 */
	public WebpayCreditReportFactory() {
		super();
	}
	
	@Override
	public PaymentReportFactory init(SveaCredential aCre) {

		cre = aCre;
		svea = new WebpayAdminBase();
		svea.initCredentials(aCre);
		
		return this;
	}
	
	/**
	 * Creates bank statement lines for a Svea Account. The svea account is
	 * selected in the constructor of this class.
	 * 
	 * @param report
	 *            The report to be filled in. Must not be null. The report is
	 *            filled in with payments for the account.
	 * @param fromDate
	 *            From date. If null, today's date is used.
	 * @param untilDate
	 *            Until date. If null, untilDate is set to fromDate.
	 *            
	 * @return	The string returned contains messages from the generation of the payment report.
	 * 			The actual report is found in the report parameter.
	 * 
	 * @throws Exception
	 */
	public List<PaymentReportGroup> createBankStatementLines(PaymentReport report,
			java.util.Date fromDate, java.util.Date untilDate) throws Exception {
		
		List<PaymentReportGroup> resultList = new ArrayList<PaymentReportGroup>();

		// Make sure the from and until dates are without time.
		// If dates are null, today's date is used

		Calendar fromCal = Calendar.getInstance();
		if (fromDate != null)
			fromCal.setTime(fromDate);
		Calendar untilCal = Calendar.getInstance();
		if (untilDate != null)
			untilCal.setTime(untilDate);
		else
			untilCal.setTime(fromDate);

		fromCal.set(Calendar.HOUR_OF_DAY, 0);
		fromCal.set(Calendar.MINUTE, 0);
		fromCal.set(Calendar.SECOND, 0);
		fromCal.set(Calendar.MILLISECOND, 0);

		untilCal.set(Calendar.HOUR_OF_DAY, 0);
		untilCal.set(Calendar.MINUTE, 0);
		untilCal.set(Calendar.SECOND, 0);
		untilCal.set(Calendar.MILLISECOND, 0);

		// Get date
		java.sql.Timestamp date = null;
		PaymentReportGroup gr = null;

		// Iterate through the dates
		while (!fromCal.after(untilCal)) {

			date = new java.sql.Timestamp(fromCal.getTimeInMillis());
			// Increment fromCal
			fromCal.add(Calendar.DATE, 1);
			
			gr = new PaymentReportGroup();
			String dstAcct = cre.getRemappedAccountFor(FeeDetail.ACCTTYPE_CASH);
			if (dstAcct!=null) {
				gr.setDstAcct(dstAcct);
			}
			String reconcileAcct = cre.getRemappedAccountFor(FeeDetail.ACCTTYPE_RECONCILE);
			if (reconcileAcct!=null) {
				gr.setReconcileAcct(reconcileAcct);
			}
			gr.setReconciliationDate(new java.util.Date(date.getTime()));
			gr.setPaymentType(cre.getAccountType());
			gr.setPaymentTypeReference(cre.getAccountNo());
			gr.setVatAcct(cre.getRemappedAccountFor(FeeDetail.ACCTTYPE_VAT));
			gr.setCurrency(cre.getCurrency());

			// Find all transactions for the given date
			try {
			
				// Fill deviations
				fillDeviations(gr, date);

				// Save total received amount which is retrieved when parsing deviations
				Double totalReceivedAmount = gr.getTotalReceivedAmt();
				Double deviationAmount = gr.calculateReceivedAmt();
				// Set total received amount to zero to get a proper calculated value
				gr.setTotalReceivedAmt(deviationAmount);
				
				// Fill normal payments
				fillAccountingAgreements(gr, date);
				
				// fill regressions
				fillAccountingAgreementRegressions(gr, date);
				
				// Set total received amount. It should always be the same, but if it isn't
				// it will be highlighted as a deviation.
				gr.setTotalReceivedAmt(totalReceivedAmount);
				
				gr.updateTotalFees();
				// Calculate total vat amount from totalfees
				gr.setTotalVatAmt(FeeDetail.getVatSum(gr.getTotalInvoiceFees()));
				
				// Remap other fees
				FeeDetail.remapFeeAccounts(cre, gr.getTotalOtherFees());
				// Remap invoice fees on group level
				FeeDetail.remapFeeAccounts(cre, gr.getTotalInvoiceFees());
				
				report.addPaymentReportGroup(gr);
				resultList.add(gr);
				
				// Add rounding
				FeeDetail rounding = gr.calculateRoundingFee(gr.getTotalReceivedAmt(), null);
				if (rounding!=null) {
					if (!rounding.getFeeTotal().equals(gr.getEndingBalance())) {
						rounding.setAccountNr(cre.getAccountMap().get(rounding.getFeeType()));
						PaymentReportDetail d = new PaymentReportDetail();
						d.addFee(rounding);
						d.setPaidAmt(0d);
						d.setReceivedAmt(-rounding.getFeeTotal());
						gr.addDetail(d);
						gr.updateTotalFees();
					}
				}

				// Cancel out fees that cancel eachother out
				gr.cancelOtherFees(FeeDetail.FEETYPE_CREDIT, new String[] {FeeDetail.FEETYPE_DEVIATIONS, FeeDetail.FEETYPE_ROUNDING});
				
				// Replace small deviations as rounding
				gr.replaceDeviationsAsRounding();
				
				// Check if VAT should be rounded to group value PBI 120761
				gr.roundVatToGroupValue();
				
				
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("WebpayCreditReportFactory: " + e.getMessage());
			}
		}

		return resultList;
	}
	

	private void fillAccountingAgreementRegressions(PaymentReportGroup gr, java.sql.Timestamp date) throws Exception {

		PaymentReportDetail d;

		// Check for regressions
		List<AccountCreditRegressReportDataRow> creditinvoices = getCreditRegressReport(date);
		for (AccountCreditRegressReportDataRow invoice : creditinvoices) {

			d = new PaymentReportDetail();
			d.setClientOrderNo(invoice.getCreditForOrderId());
			d.setReceivedAmt(invoice.getAmount().doubleValue());
			d.setPaidAmt(d.getReceivedAmt());
			d.setCustomerId(Integer.toString(invoice.getCreditForCustomerId()));
			d.setPayerName(invoice.getCustomerName());
			if (invoice.getCheckoutOrderId()!=null)
				d.setCheckoutOrderId(invoice.getCheckoutOrderId().toString());
			if (invoice.getSveaOrderCreationDate()!=null) {
				d.setOrderDate(JsonUtil.dfmt.format(invoice.getSveaOrderCreationDate().toGregorianCalendar().getTime()));
			}
			if (invoice.getSveaOrderId()!=null)
			d.setOrderId(invoice.getSveaOrderId().toString());
			d.setInvoiceId(Integer.toString(invoice.getInvoiceId()));

			gr.addDetail(d);
		}
	
	}
	
	private void fillAccountingAgreements(PaymentReportGroup gr, java.sql.Timestamp date) throws Exception {

		List<AccountingAgreementType25ReportDataRow> invoices = getCreditReport(date);
		PaymentReportDetail d;
		FeeDetail fee;
		for (AccountingAgreementType25ReportDataRow invoice : invoices) {

			d = new PaymentReportDetail();
			d.setClientOrderNo(invoice.getReferenceNumber());
			d.setReceivedAmt(invoice.getPaidAmount().doubleValue());
			d.setPaidAmt(invoice.getAmount().doubleValue());
			d.setCustomerId(Integer.toString(invoice.getAccountNumber()));
			d.setPayerName(invoice.getName());
			if (invoice.getCheckoutOrderId()!=null)
				d.setCheckoutOrderId(invoice.getCheckoutOrderId().toString());
			if (invoice.getSveaOrderCreationDate()!=null) {
				d.setOrderDate(JsonUtil.dfmt.format(invoice.getSveaOrderCreationDate().toGregorianCalendar().getTime()));
			}
			if (invoice.getSveaOrderId()!=null)
			d.setOrderId(invoice.getSveaOrderId().toString());

			double feeAmt = d.getPaidAmt() - d.getReceivedAmt();
			// Check for fees
			if (feeAmt!=0) {
				fee = new FeeDetail();
				if (gr.getReconciliationDateAsDate().after(JsonUtil.dfmt.parse("2019-02-15")))
					fee.setFeeType(FeeDetail.FEETYPE_ADM);
				else
					fee.setFeeType(FeeDetail.FEETYPE_CREDIT);
				fee.setFee(feeAmt);
				FeeDetail.remapFeeAccount(cre, fee);
				fee.calculateVat(VatType.getVatRate(cre.getCountryCode(), fee.getFeeType(), SveaCredential.ACCOUNTTYPE_ACCOUNT_CREDIT, date, cre.isCompany()));						
				d.addFee(fee);
			}
			
			// Calculate received amt
			d.calculateReceivedAmt();

			gr.addDetail(d);
		}
		
	}
	
	/**
	 * Get regression report
	 * 
	 * @param date
	 * @return
	 * @throws Exception
	 */
	public List<AccountingAgreementType25ReportDataRow> getCreditReport(java.sql.Timestamp date)
			throws Exception {

		AccountingAgreementType25ReportRequest request = new AccountingAgreementType25ReportRequest();
		request.setClientId(new Long(cre.getAccountNo()));
		Authentication auth = new Authentication();
		auth.setUsername(cre.getUsername());
		auth.setPassword(cre.getPassword());
		request.setAuthentication(auth);
		XMLGregorianCalendar xdate = convert(date);
		request.setFromDate(xdate);
		request.setToDate(xdate);

		AccountingAgreementType25ReportResponse response = svea.getServicePort()
				.generateAccountingAgreementType25Report(request);

		if (response.getErrorMessage()!=null && response.getErrorMessage().trim().length()>0)
			throw new Exception("Can't read invoice report for account " + cre.getAccountNo() + ": " + response.getErrorMessage());
		
		ArrayOfAccountingAgreementType25ReportDataRow res = response.getRows();
		
		return res!=null ? res.getAccountingAgreementType25ReportDataRow() : null;

	}
	
	/**
	 * Get regression report
	 * 
	 * @param date
	 * @return
	 * @throws Exception
	 */
	public List<AccountCreditRegressReportDataRow> getCreditRegressReport(java.sql.Timestamp date)
			throws Exception {

		String[] reportNames = new String[] {"AccountCredit", "Invoice", "PaymentPlan"};
		
		AccountCreditRegressReportRequest request = new AccountCreditRegressReportRequest();
		request.setClientId(new Long(cre.getAccountNo()));
		Authentication auth = new Authentication();
		auth.setUsername(cre.getUsername());
		auth.setPassword(cre.getPassword());
		request.setAuthentication(auth);
		XMLGregorianCalendar xdate = convert(date);
		request.setFromDate(xdate);
		request.setToDate(xdate);

		List<AccountCreditRegressReportDataRow> result = new ArrayList<AccountCreditRegressReportDataRow>();
		
		AccountCreditRegressReportResponse response = null;
		ArrayOfAccountCreditRegressReportDataRow res = null;

		for (String reportName : reportNames) {

			request.setReportName(reportName);
			
			response = svea.getServicePort().generateAccountCreditRegressReport(request);
	
			if (response.getErrorMessage()!=null && response.getErrorMessage().trim().length()>0)
				throw new Exception("Can't read invoice regress report for account " + cre.getAccountNo() + ": " + response.getErrorMessage());
			
			res = response.getRows();
			if (res!=null && res.getAccountCreditRegressReportDataRow()!=null) {
				result.addAll(res.getAccountCreditRegressReportDataRow());
			}
		}
		
		return result;

	}
	
	

}
