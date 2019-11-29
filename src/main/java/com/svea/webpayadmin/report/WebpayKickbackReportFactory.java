package com.svea.webpayadmin.report;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.xml.datatype.XMLGregorianCalendar;

import com.svea.webpay.common.auth.SveaCredential;
import com.svea.webpay.common.reconciliation.FeeDetail;
import com.svea.webpay.common.reconciliation.PaymentReport;
import com.svea.webpay.common.reconciliation.PaymentReportDetail;
import com.svea.webpay.common.reconciliation.PaymentReportFactory;
import com.svea.webpay.common.reconciliation.PaymentReportGroup;
import com.svea.webpayadmin.WebpayAdminBase;
import com.svea.webpayadminservice.client.ArrayOfKickbackReportRow;
import com.svea.webpayadminservice.client.GetKickbackReportRequest;
import com.svea.webpayadminservice.client.GetKickbackReportResponse2;
import com.svea.webpayadminservice.client.KickbackReportRow;

/**
 * Client to read payment reconciliation information from Kickbacks
 * 
 * This client uses the SveaWebPay Administration Service API v 190318.
 * 
 * @author Daniel Tamm
 *
 */

public class WebpayKickbackReportFactory extends ReportFactoryBase implements PaymentReportFactory {
	
	/**
	 * Empty constructor
	 */
	public WebpayKickbackReportFactory() {
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
	 *            NOTE. The groups created are not added to the report in this
	 *            particular case
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
			// Increment fromCal for next iteration
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
			gr.setPaymentType(SveaCredential.ACCOUNTTYPE_PAYMENTPLAN);
			gr.setPaymentTypeReference(cre.getAccountNo());
			gr.setVatAcct(cre.getRemappedAccountFor(FeeDetail.ACCTTYPE_VAT));
			gr.setCurrency(cre.getCurrency());
	
			// ========== NORMAL PAYMENTS ===========

			// Find all admin/no-risk transactions for the given date
			List<KickbackReportRow> kickbacks = getKickbackReport(date);
			if (kickbacks==null)
				kickbacks = new ArrayList<KickbackReportRow>();
			PaymentReportDetail d;
			FeeDetail fee;
			for (KickbackReportRow kickback : kickbacks) {
				
				d = new PaymentReportDetail();
				d.setInvoiceId(kickback.getInvoiceId()!=null ? kickback.getInvoiceId().toString() : null);
				d.setCustomerId(kickback.getCustomerId()!=null ? kickback.getCustomerId().toString() : null);
				d.setPaidAmt(new Double(0));
				
				fee = new FeeDetail();
				fee.setFeeType(FeeDetail.FEETYPE_KICKBACK);
				fee.setFee(kickback.getAmount().doubleValue());
				d.addFee(fee);
				
				// Calculate received amt
				d.calculateReceivedAmt();

				gr.addDetail(d);
			}
			
			// Don't add the group to the report since we want to use the groups to replace.
			// Only add to result list
			resultList.add(gr);
		}
		
		return resultList;
	}

	public List<KickbackReportRow> getKickbackReport(java.sql.Timestamp date) throws Exception {
		
		GetKickbackReportRequest request = new GetKickbackReportRequest();
		request.setClientId(new Long(cre.getAccountNo()));
		request.setAuthentication(svea.getAuth());
		// Kickbacks are reported one day later than the service reports the kickback
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.add(Calendar.DATE, -1);
		XMLGregorianCalendar xdate = convert(new java.sql.Timestamp(cal.getTimeInMillis()));
		request.setRunDate(xdate);

		GetKickbackReportResponse2 response = svea.getServicePort().getKickbackReport(request);
		
		if (response.getErrorMessage()!=null && response.getErrorMessage().trim().length()>0)
			throw new Exception("Can't read kickback report for account " + cre.getAccountNo() + ": " + response.getErrorMessage());
		
		ArrayOfKickbackReportRow res = response.getKickbacks();
		
		return res!=null ? res.getKickbackReportRow() : null;
	}
	

}
