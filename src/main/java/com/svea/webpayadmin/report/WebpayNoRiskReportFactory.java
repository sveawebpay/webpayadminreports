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
import com.svea.webpayadmin.WebpayAdminBase;
import com.svea.webpayadmin.WebpayAdminClient;
import com.svea.webpayadminservice.client.ArrayOfInvoicePaidToClientRow;
import com.svea.webpayadminservice.client.GetDebtInvoicesPaidToClientForReportRequest;
import com.svea.webpayadminservice.client.GetDebtInvoicesPaidToClientForReportResponse2;
import com.svea.webpayadminservice.client.GetInvoicesPaidToClientForReportRequest;
import com.svea.webpayadminservice.client.GetInvoicesPaidToClientForReportResponse2;
import com.svea.webpayadminservice.client.InvoicePaidToClientRow;

/**
 * Client to read payment reconciliation information from Svea No Risk Invoices (also called admin-invoices).
 * 
 * This client uses the SveaWebPay Administration Service API v 1.30.
 * 
 * @author Daniel Tamm
 *
 */

public class WebpayNoRiskReportFactory extends ReportFactoryBase implements PaymentReportFactory {
	
	/**
	 * Empty constructor
	 */
	public WebpayNoRiskReportFactory() {
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
			gr.setPaymentType(SveaCredential.ACCOUNTTYPE_ADMIN);
			gr.setPaymentTypeReference(cre.getAccountNo());
			gr.setVatAcct(cre.getRemappedAccountFor(FeeDetail.ACCTTYPE_VAT));
			gr.setCurrency(cre.getCurrency());
			
			// Check if this is a stand-alone report
			if (SveaCredential.ACCOUNTTYPE_ADMIN.equals(cre.getAccountType())) {

				// Parse report deviations
				fillDeviations(gr, date);
				
			}

			// ========== NORMAL PAYMENTS ===========

			// Find all admin/no-risk transactions for the given date
			List<InvoicePaidToClientRow> invoices = getInvoicesPaidToClient(date);
			if (invoices==null)
				invoices = new ArrayList<InvoicePaidToClientRow>();
			List<InvoicePaidToClientRow> collectionInvoices = getDebtInvoicesPaidToClient(date);
			if (collectionInvoices!=null && collectionInvoices.size()>0) {
				invoices.addAll(collectionInvoices);
			}
			PaymentReportDetail d;
			FeeDetail fee;
			for (InvoicePaidToClientRow invoice : invoices) {
				
				d = new PaymentReportDetail();
				if (invoice.getCheckoutOrderId()!=null && invoice.getCheckoutOrderId()!=0) {
					d.setCheckoutOrderId(invoice.getCheckoutOrderId().toString());
				}
				d.setInvoiceId(invoice.getInvoiceId()!=null ? invoice.getInvoiceId().toString() : null);
				d.setOrderId(invoice.getSveaOrderId()!=null ? invoice.getSveaOrderId().toString() : null);
				if (invoice.getSveaOrderCreationDate()!=null) {
					d.setOrderDate(JsonUtil.getDateFormat().format(invoice.getSveaOrderCreationDate().toGregorianCalendar().getTime()));
				}
				d.setPaidAmt(invoice.getAmount().doubleValue());
				// Enrich from invoice since clientOrderId is missing.
				d.setEnrichFromInvoice(true);

				// TODO: Fees doesn't seem to be charged?
				// Check for fees
				
				/**
				if (invoice.getCreditFee().signum() != 0) {
					fee = new FeeDetail(FeeDetail.FEETYPE_CREDIT, invoice
							.getCreditFee().doubleValue(), 0D 
					);
					d.addFee(fee);
				}
				if (invoice.getAdministrationFee().signum() != 0) {
					fee = new FeeDetail(FeeDetail.FEETYPE_ADM, invoice
							.getAdministrationFee().doubleValue(), 0D 
					);
					d.addFee(fee);
				}
				
				for (FeeDetail f : d.getFees()) {
					f.setAccountNr(cre.getRemappedAccountFor(f.getFeeType()));
					f.setFeeVat(f.calculateVat(VatType.getVatRate(cre.getCountryCode(), f.getFeeType(), SveaCredential.ACCOUNTTYPE_ADMIN, date, cre.isCompany())));
				}
				*/
				
				// Calculate received amt
				d.calculateReceivedAmt();

				gr.addDetail(d);
			}
			
			List<FeeDetail> otherFees = null;
			
			// Check if this is a stand-alone report
			if (SveaCredential.ACCOUNTTYPE_ADMIN.equals(cre.getAccountType())) {

				// Check accounting suggestion for other fees
				otherFees = getOtherFeesFromAccountingSuggestion(gr, date);
				
			}
			
			gr.updateTotalFees();
			
			if (otherFees!=null && otherFees.size()>0) {
				gr.combineTotalOtherFees(otherFees, new String[] {FeeDetail.FEETYPE_CREDIT, FeeDetail.FEETYPE_ADM}, true);
				gr.updateTotalFees();
			}
			
			// Calculate total vat amount from totalfees
			gr.setTotalVatAmt(FeeDetail.getVatSum(gr.getTotalInvoiceFees()) + FeeDetail.getVatSum(gr.getTotalOtherFees()));
			gr.setTotalReceivedAmt(gr.calculateReceivedAmt());
			
			// Check totals if this is a stand-alone report
			if (SveaCredential.ACCOUNTTYPE_ADMIN.equals(cre.getAccountType())) {
				
				// Check Svea's total
				double sveasBankTotal = getAmountOnBankFromSvea(date);
				if (sveasBankTotal!=gr.calculateReceivedAmt()) {
					gr.setTotalReceivedAmt(sveasBankTotal);
					
					// TODO: Look at WebpayCreditReport and WebpayAdminReport for new rounding check
					// Add rounding
					FeeDetail rounding = gr.calculateRoundingFee(gr.getTotalReceivedAmt(), null);
					if (rounding!=null) {
						rounding.setAccountNr(cre.getAccountMap().get(rounding.getFeeType()));
						gr.addOtherFee(rounding);
						gr.updateTotalFees();
					}
				}

				// Cancel out fees that cancel eachother out
				gr.cancelOtherFees(FeeDetail.FEETYPE_CREDIT, new String[] {FeeDetail.FEETYPE_DEVIATIONS, FeeDetail.FEETYPE_ROUNDING});
				
				// Replace small deviations as rounding
				gr.replaceDeviationsAsRounding();
		
			}
			
			// Remap other fees
			FeeDetail.remapFeeAccounts(cre, gr.getTotalOtherFees());
			// Remap invoice fees on group level
			FeeDetail.remapFeeAccounts(cre, gr.getTotalInvoiceFees());
			
			report.addPaymentReportGroup(gr);
			resultList.add(gr);
		}

		if (gr.getPaymentReportDetail()!=null) {
			WebpayAdminClient enrichClient = new WebpayAdminClient();
			enrichClient.initCredentials(cre);
			enrichClient.enrichFromInvoice(gr.getPaymentTypeReference(), gr.getPaymentReportDetail(), false, cre.isSkipTaxId(), cre.isSkipEmail());
		}
		
		return resultList;
	}
	
	/**
	 * Get Admin Invoice report.
	 * 
	 * @param date
	 * @return
	 * @throws Exception
	 */
	public List<InvoicePaidToClientRow> getInvoicesPaidToClient(java.sql.Timestamp date)
			throws Exception {

		GetInvoicesPaidToClientForReportRequest request = new GetInvoicesPaidToClientForReportRequest();
		request.setClientId(new Long(cre.getAccountNo()));
		request.setAuthentication(svea.getAuth());
		XMLGregorianCalendar xdate = convert(date);
		request.setFromDate(xdate);
		request.setToDate(xdate);

		GetInvoicesPaidToClientForReportResponse2 response = svea.getServicePort().getInvoicesPaidToClientForReport(request);

		if (response.getErrorMessage()!=null && response.getErrorMessage().trim().length()>0)
			throw new Exception("Can't read admin invoice report for account " + cre.getAccountNo() + ": " + response.getErrorMessage());
		
		ArrayOfInvoicePaidToClientRow res = response.getInvoices();
		
		return res!=null ? res.getInvoicePaidToClientRow() : null;

	}

	/**
	 * Get Debt (collection) Invoice report.
	 * 
	 * @param date
	 * @return
	 * @throws Exception
	 */
	public List<InvoicePaidToClientRow> getDebtInvoicesPaidToClient(java.sql.Timestamp date)
			throws Exception {

		GetDebtInvoicesPaidToClientForReportRequest request = new GetDebtInvoicesPaidToClientForReportRequest();
		request.setClientId(new Long(cre.getAccountNo()));
		request.setAuthentication(svea.getAuth());
		XMLGregorianCalendar xdate = convert(date);
		request.setFromDate(xdate);
		request.setToDate(xdate);

		GetDebtInvoicesPaidToClientForReportResponse2 response = svea.getServicePort().getDebtInvoicesPaidToClientForReport(request);

		if (response.getErrorMessage()!=null && response.getErrorMessage().trim().length()>0)
			throw new Exception("Can't read debt invoice report for account " + cre.getAccountNo() + ": " + response.getErrorMessage());
		
		ArrayOfInvoicePaidToClientRow res = response.getInvoices();
		
		return res!=null ? res.getInvoicePaidToClientRow() : null;

	}
	

}
