package com.svea.webpayadmin.report;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.XMLGregorianCalendar;

import com.svea.webpay.common.auth.SveaCredential;
import com.svea.webpay.common.reconciliation.FeeDetail;
import com.svea.webpay.common.reconciliation.PaymentReport;
import com.svea.webpay.common.reconciliation.PaymentReportDetail;
import com.svea.webpay.common.reconciliation.PaymentReportFactory;
import com.svea.webpay.common.reconciliation.PaymentReportGroup;
import com.svea.webpay.common.reconciliation.VatType;
import com.svea.webpayadmin.WebpayAdminBase;
import com.svea.webpayadminservice.client.ArrayOfSpecificationRow;
import com.svea.webpayadminservice.client.GetSpecificationReportRequest;
import com.svea.webpayadminservice.client.GetSpecificationReportResponse2;
import com.svea.webpayadminservice.client.SpecificationReportType;
import com.svea.webpayadminservice.client.SpecificationRow;

/**
 * Client to read payment reconciliation information from Svea Invoices (card payments).
 * 
 * Can also be used to read direct bank payments.
 * 
 * Use setSpecificationReportType to change report type. Default is card payments.
 * 
 * This client uses the SveaWebPay Administration Service API v 1.30.
 * 
 * @author Daniel Tamm
 *
 */

public class WebpayCardReportFactory implements PaymentReportFactory {
	
	/**
	 * Pattern for finding retry records
	 */
	public String retryExpression = "(.*?)(\\-WRet)";
	public Pattern retryPattern;
	
	private WebpayAdminBase svea;	
	protected SveaCredential cre;
	
	private SpecificationReportType specificationReportType = SpecificationReportType.CARD;
	
	/**
	 * Empty constructor
	 */
	public WebpayCardReportFactory() {
		super();
		retryPattern = Pattern.compile(retryExpression);
	}
	
	public SveaCredential getSveaCredential() {
		return cre;
	}
	
	@Override
	public PaymentReportFactory init(SveaCredential aCre) {

		cre = aCre;
		
		svea = new WebpayAdminBase();
		svea.initCredentials(aCre);
		
		return this;
	}
	
	
	public SpecificationReportType getSpecificationReportType() {
		return specificationReportType;
	}

	public void setSpecificationReportType(
			SpecificationReportType specificationReportType) {
		this.specificationReportType = specificationReportType;
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

			// ========== NORMAL PAYMENTS ===========

			// Find all card transactions for the given date
			List<SpecificationRow> invoices = getSpecificationReport(date);
			PaymentReportDetail d;
			FeeDetail fee;
			String clientOrderNumber;
			Matcher m;
			for (SpecificationRow invoice : invoices) {

				if (!invoice.isSveaIsPaymentFacilitator()) {
					// Don't process this payment if we're not facilitating it.
					continue;
				}
				clientOrderNumber = invoice.getClientOrderNumber();
				
				// Check for retry-indicators and remove them if existing
				m = retryPattern.matcher(clientOrderNumber);
				if (m.matches()) {
					clientOrderNumber = m.group(1);
				}
				
				d = new PaymentReportDetail();
				d.setClientOrderNo(clientOrderNumber);
				d.setPaymentId(Long.toString(invoice.getTransactionId()));
				d.setPaidAmt(invoice.getAmount().doubleValue());

				// Check for fees
				if (invoice.getFee().signum() != 0) {
					fee = new FeeDetail(FeeDetail.FEETYPE_CREDIT, invoice
							.getFee().doubleValue(), 0D 
					);
					fee.setAccountNr(cre.getRemappedAccountFor(fee.getFeeType()));
					fee.setFeeVat(fee.calculateVat(VatType.getVatRate(cre.getCountryCode(), fee.getFeeType(), SveaCredential.ACCOUNTTYPE_CREDITCARD, date, cre.isCompany())));
					d.addFee(fee);
				}
				
				// Calculate received amt
				d.calculateReceivedAmt();

				gr.addDetail(d);
			}
			
			gr.updateTotalFees();
			// Calculate total vat amount from totalfees
			gr.setTotalVatAmt(FeeDetail.getVatSum(gr.getTotalInvoiceFees()));
			
			// Remap other fees
			FeeDetail.remapFeeAccounts(cre, gr.getTotalOtherFees());
			// Remap invoice fees on group level
			FeeDetail.remapFeeAccounts(cre, gr.getTotalInvoiceFees());
			
			report.addPaymentReportGroup(gr);
			resultList.add(gr);
		}

		return resultList;
	}
	
	/**
	 * Get regression report
	 * 
	 * @param date
	 * @return
	 * @throws Exception
	 */
	public List<SpecificationRow> getSpecificationReport(java.sql.Timestamp date)
			throws Exception {

		GetSpecificationReportRequest request = new GetSpecificationReportRequest();
		request.setClientId(new Long(cre.getAccountNo()));
		request.setAuthentication(svea.getAuth());
		request.setReportType(specificationReportType);
		XMLGregorianCalendar xdate = convert(date);
		request.setFromDate(xdate);
		request.setToDate(xdate);

		GetSpecificationReportResponse2 response = svea.getServicePort()
				.getSpecificationReport(request);

		if (response.getErrorMessage()!=null && response.getErrorMessage().trim().length()>0)
			throw new Exception("Can't read invoice report for account " + cre.getAccountNo() + ": " + response.getErrorMessage());
		
		ArrayOfSpecificationRow res = response.getRows();
		
		return res!=null ? res.getSpecificationRow() : null;

	}


	private XMLGregorianCalendar convert(java.sql.Timestamp date)
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
	

}
