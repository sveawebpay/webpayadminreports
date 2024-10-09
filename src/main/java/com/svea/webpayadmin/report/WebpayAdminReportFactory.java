package com.svea.webpayadmin.report;

import java.sql.Timestamp;
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
import com.svea.webpayadminservice.client.ArrayOfInvoiceReportRow;
import com.svea.webpayadminservice.client.ArrayOfPaymentPlanReportRow;
import com.svea.webpayadminservice.client.ArrayOfRegressionReportRow;
import com.svea.webpayadminservice.client.GetInvoiceReportRequest;
import com.svea.webpayadminservice.client.GetInvoiceReportResponse;
import com.svea.webpayadminservice.client.GetInvoiceReportResponse2;
import com.svea.webpayadminservice.client.GetPaymentPlanReportRequest;
import com.svea.webpayadminservice.client.GetPaymentPlanReportResponse;
import com.svea.webpayadminservice.client.GetPaymentPlanReportResponse2;
import com.svea.webpayadminservice.client.GetRegressionReportRequest;
import com.svea.webpayadminservice.client.GetRegressionReportResponse;
import com.svea.webpayadminservice.client.GetRegressionReportResponse2;
import com.svea.webpayadminservice.client.InvoiceReportRow;
import com.svea.webpayadminservice.client.PaymentPlanReportRow;
import com.svea.webpayadminservice.client.RegressionReportRow;
import com.svea.webpayadminservice.client.SpecificationReportType;

/**
 * Client to read payment reconciliation information from Svea Invoices (normal invoices and payment plan).
 * 
 * This client uses the SveaWebPay Administration Service API v 1.29.
 * 
 * @author Daniel Tamm
 *
 */

public class WebpayAdminReportFactory extends ReportFactoryBase implements PaymentReportFactory {
	
	@Override
	public PaymentReportFactory init(SveaCredential aCre) {

		cre =  aCre;
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
			if (!svea.isPartPayment()) {

				// Fill deviations
				fillDeviations(gr, date);
				
				// Fill others
				fillFromInvoiceReport(gr, date);
				
			} else {
				// ============= PAYMENT PLANS ==============
				// Fill deviations
				fillDeviations(gr, date);
				// Save total received amount which is retrieved when parsing deviations
				Double totalReceivedAmount = gr.getTotalReceivedAmt();
				Double deviationAmount = gr.calculateReceivedAmt();
				// Set total received amount to zero to get a proper calculated value
				gr.setTotalReceivedAmt(deviationAmount);
				// Fill others
				fillFromPaymentPlan(report, gr, date);
				// Set total received amount. It should always be the same, but if it isn't
				// it will be highlighted as a deviation.
				gr.setTotalReceivedAmt(totalReceivedAmount);

			}
			
			// Set destination account here since it may have been remapped above
			String dstAcct = cre.getRemappedAccountFor(FeeDetail.ACCTTYPE_CASH);
			if (dstAcct!=null) {
				gr.setDstAcct(dstAcct);
			}
			
			report.addPaymentReportGroup(gr);
			resultList.add(gr);
			// Map accounts
			if (gr.getTotalInvoiceFees()!=null) {
				for (FeeDetail fd : gr.getTotalInvoiceFees()) {
					FeeDetail.remapFeeAccount(cre, fd);
				}
			}
			if (gr.getTotalOtherFees()!=null) {
				for (FeeDetail fd : gr.getTotalOtherFees()) {
					FeeDetail.remapFeeAccount(cre, fd);
				}
			}
			
			// ============ CARD AND DIRECT PAYMENTS ============
			// Check if card payments are reconciled on this credential as well
			List<PaymentReportGroup> cardGroups = null;
			if (cre.isIncludeCardPayments()) {
				cardGroups = fillCardPayments(report, gr, date);
				List<PaymentReportGroup> directGroups = null;
				directGroups = fillDirectPayments(report, gr, date);
				for (PaymentReportGroup prg : directGroups) {
					cardGroups.add(prg);
				}
				resultList.addAll(cardGroups);
			}
	
			// Check if admin payments are reconciled on this credential as well
			List<PaymentReportGroup> adminGroups = null;
			if (cre.isIncludeAdminPayments()) {
				adminGroups = fillNoRiskPayments(report, gr, date);
				resultList.addAll(adminGroups);
				
				// Add admin groups to card groups for rounding comparison
				if (adminGroups!=null && adminGroups.size()>0) {
					if (cardGroups==null) {
						cardGroups = adminGroups;
					} else {
						cardGroups.addAll(adminGroups);
					}
				}
			}
			
			// Add rounding and check the cardGroups
			FeeDetail rounding = gr.calculateRoundingFee(gr.getTotalReceivedAmt(), cardGroups);
			if (rounding!=null) {
				double diffRounding = rounding.getFeeTotal() - gr.getEndingBalance();
				if (diffRounding!=0) {
					rounding.setFee(diffRounding);
					rounding.setAccountNr(cre.getAccountMap().get(rounding.getFeeType()));
					gr.addOtherFee(rounding);
					gr.updateTotalFees();
				}
			}

			// Cancel out fees that cancel eachother out
			gr.cancelOtherFees(FeeDetail.FEETYPE_CREDIT, new String[] {FeeDetail.FEETYPE_DEVIATIONS, FeeDetail.FEETYPE_ROUNDING});
			
			// Replace small deviations as rounding
			gr.replaceDeviationsAsRounding();
			
			// Check if VAT should be rounded to group value PBI 120761
			gr.roundVatToGroupValue();

		} // End if iterate through the date span

		return resultList;
	}

	
	/**
	 * Fills no risk payments into supplied group
	 * 
	 * @param parentGroup				The parent group.
	 * @param date				The date
	 * @throws Exception 		If something goes wrong.
	 */
	private List<PaymentReportGroup> fillNoRiskPayments(PaymentReport report, PaymentReportGroup parentGroup, Timestamp date) throws Exception {
		
		WebpayNoRiskReportFactory nrFactory = new WebpayNoRiskReportFactory();
		nrFactory.init(cre);
		
		List<PaymentReportGroup> groups = nrFactory.createBankStatementLines(report, date, date);

		if (groups!=null) {
			for (PaymentReportGroup g : groups) {
				g.setPaymentType(SveaCredential.ACCOUNTTYPE_ADMIN);
				// Remove payment fees from the parent group
				if (!cre.isIgnoreFees()) {
					FeeDetail adminFees = new FeeDetail();
					
					// Set feetype to credit since that's what we want to remove from parent
					adminFees.setFeeType(FeeDetail.FEETYPE_CREDIT);  
					adminFees.setFee(FeeDetail.getFeeSum(g.getTotalInvoiceFees()) + FeeDetail.getFeeSum(g.getTotalOtherFees()));
					adminFees.setFeeVat(FeeDetail.getVatSum(g.getTotalInvoiceFees()) + FeeDetail.getVatSum(g.getTotalOtherFees()));
					adminFees.setFee(-adminFees.getFee());
					adminFees.setFeeVat(-adminFees.getFeeVat());

					List<FeeDetail> otherFees = new ArrayList<FeeDetail>();
					otherFees.add(adminFees);
					
					parentGroup.combineTotalOtherFees(otherFees, new String[]{FeeDetail.FEETYPE_CREDIT}, true);
					parentGroup.updateTotalFees();
					
				}
			}
		}
		
		return groups;
	}
	
	/**
	 * Adds card payments to this report. 
	 * 
	 * @param report			The report where additional groups are to be added
	 * @param parentGroup		The parent group, where the card payments are totalled.
	 * @param date
	 * @throws Exception
	 */
	private List<PaymentReportGroup> fillCardPayments(PaymentReport report, PaymentReportGroup parentGroup, java.sql.Timestamp date) throws Exception {
		
		WebpayCardReportFactory cardFactory = new WebpayCardReportFactory();
		cardFactory.init(cre);
		
		List<PaymentReportGroup> groups = cardFactory.createBankStatementLines(report, date, date);
		
		if (groups!=null) {
			for (PaymentReportGroup g : groups) {
				if (g.isEmpty())
					continue;
				g.setPaymentType(SveaCredential.ACCOUNTTYPE_CREDITCARD);
				// Remove card invoice fees from the parent group
				if (!cre.isIgnoreFees()) {
					FeeDetail cardFees = new FeeDetail();
					
					// Set feetype to credit since that's what we want to remove from parent
					cardFees.setFeeType(FeeDetail.FEETYPE_CREDIT);  
					cardFees.setFee(FeeDetail.getFeeSum(g.getTotalInvoiceFees()) + FeeDetail.getFeeSum(g.getTotalOtherFees()));
					// Use the same VAT-rate as for FEETYPE_CREDIT on type INVOICE since that's how we calculate
					cardFees.calculateVat(VatType.getVatRate(cre.getCountryCode(), cardFees.getFeeType(), cre.getAccountType(), date, cre.isCompany()));
					// OLD cardFees.setFeeVat(FeeDetail.getVatSum(g.getTotalInvoiceFees()) + FeeDetail.getVatSum(g.getTotalOtherFees()));
					cardFees.setFee(-cardFees.getFee());
					cardFees.setFeeVat(-cardFees.getFeeVat());

					List<FeeDetail> otherFees = new ArrayList<FeeDetail>();
					otherFees.add(cardFees);
					
					parentGroup.combineTotalOtherFees(otherFees, new String[]{FeeDetail.FEETYPE_CREDIT}, true);
					parentGroup.updateTotalFees();
					
				}
			}
		}

		return groups;
	}
	
	
	/**
	 * Adds direct payments to this report. 
	 * 
	 * @param report			The report where additional groups are to be added
	 * @param parentGroup		The parent group, where the card payments are totalled.
	 * @param date
	 * @throws Exception
	 */
	private List<PaymentReportGroup> fillDirectPayments(PaymentReport report, PaymentReportGroup parentGroup, java.sql.Timestamp date) throws Exception {
		
		WebpayCardReportFactory cardFactory = new WebpayCardReportFactory();
		cardFactory.init(cre);
		cardFactory.setSpecificationReportType(SpecificationReportType.BANK);
		
		List<PaymentReportGroup> groups = cardFactory.createBankStatementLines(report, date, date);
		if (groups!=null) {
			for (PaymentReportGroup g : groups) {
				g.setPaymentType(SveaCredential.ACCOUNTTYPE_DIRECT_BANK);
				// Remove payment fees from the parent group
				if (!cre.isIgnoreFees()) {
					FeeDetail cardFees = new FeeDetail();
					
					// Set feetype to credit since that's what we want to remove from parent
					cardFees.setFeeType(FeeDetail.FEETYPE_CREDIT);  
					cardFees.setFee(FeeDetail.getFeeSum(g.getTotalInvoiceFees()) + FeeDetail.getFeeSum(g.getTotalOtherFees()));
					cardFees.setFeeVat(FeeDetail.getVatSum(g.getTotalInvoiceFees()) + FeeDetail.getVatSum(g.getTotalOtherFees()));
					cardFees.setFee(-cardFees.getFee());
					cardFees.setFeeVat(-cardFees.getFeeVat());

					List<FeeDetail> otherFees = new ArrayList<FeeDetail>();
					otherFees.add(cardFees);
					
					parentGroup.combineTotalOtherFees(otherFees, new String[]{FeeDetail.FEETYPE_CREDIT}, true);
					parentGroup.updateTotalFees();
					
				}
			}
		}

		return groups;
	}
	
	
	/**
	 * Fills from invoice and regression report (this is for normal invoice payments).
	 * 
	 * @param gr
	 * @param date
	 * @throws Exception
	 */
	private void fillFromInvoiceReport(PaymentReportGroup gr, java.sql.Timestamp date) throws Exception {
		
		// Get vat rates
		double vatRateAdmFee = VatType.getVatRate(
				cre.getCountryCode(), 
				FeeDetail.FEETYPE_ADM, 
				gr.getPaymentType(), 
				date,
				cre.isCompany());
		
		double vatRateCreditFee = VatType.getVatRate(
				cre.getCountryCode(), 
				FeeDetail.FEETYPE_CREDIT, 
				gr.getPaymentType(),
				date,
				cre.isCompany()); 
		
		// Find all transactions for the given date
		List<InvoiceReportRow> invoices = getInvoiceReport(date);
		PaymentReportDetail d;
		FeeDetail fee;
		for (InvoiceReportRow invoice : invoices) {

			d = new PaymentReportDetail();
			d.setCustomerId(Long.toString(invoice.getCustomerId()));
			d.setInvoiceId(Long.toString(invoice.getInvoiceId()));
			d.setPayerName(invoice.getName());
			d.setPaidAmt(invoice.getAmount().doubleValue());
			d.setClientOrderNo(invoice.getClientOrderNumber());
			if (invoice.getCheckoutOrderId()!=null && invoice.getCheckoutOrderId()>0)
				d.setCheckoutOrderId(invoice.getCheckoutOrderId().toString());
			if (invoice.getSveaOrderId()!=null && invoice.getSveaOrderId()>0) {
				d.setOrderId(invoice.getSveaOrderId().toString());
			}
			if (invoice.getSveaOrderCreationDate()!=null) {
				XMLGregorianCalendar odate = invoice.getSveaOrderCreationDate();
				d.setOrderDate(JsonUtil.getDateFormat().format(odate.toGregorianCalendar().getTime()));
			}

			// Check for fees
			if (invoice.getAdministrationFee().signum() != 0) {
				fee = new FeeDetail(FeeDetail.FEETYPE_ADM, invoice		
						.getAdministrationFee().doubleValue(), 0D // VAT when company
				);
				FeeDetail.remapFeeAccount(cre, fee);
				fee.calculateVat(vatRateAdmFee);
				d.addFee(fee);
			}

			if (invoice.getCreditFee().signum() != 0) {
				fee = new FeeDetail(FeeDetail.FEETYPE_CREDIT, invoice
						.getCreditFee().doubleValue(), 0D // VAT?
				);
				FeeDetail.remapFeeAccount(cre, fee);
				fee.calculateVat(vatRateCreditFee);
				d.addFee(fee);
			}
			
			// Calculate received amt
			d.calculateReceivedAmt();

			gr.addDetail(d);
		}

		// Regressions
		List<RegressionReportRow> regressions = getRegressionReport(date);
		
		if (regressions!=null && regressions.size()>0) {
			
			double totalRegressions = 0d;
			
			for (RegressionReportRow rr : regressions) {
				
				d = new PaymentReportDetail();
				
				d.setCustomerId(Long.toString(rr.getCustomerId()));
				d.setInvoiceId(Long.toString(rr.getInvoiceId()));
				d.setPayerName(rr.getName());
				d.setPaidAmt(-rr.getTransactionAmount().doubleValue());
				if (rr.getCheckoutOrderId()!=null && rr.getCheckoutOrderId()>0)
					d.setCheckoutOrderId(rr.getCheckoutOrderId().toString());
				if (rr.getClientOrderNumber()!=null && rr.getClientOrderNumber().trim().length()>0) {
					d.setClientOrderNo(rr.getClientOrderNumber());
				}
				if (rr.getSveaOrderId()!=null && rr.getSveaOrderId()>0) {
					d.setOrderId(Long.toString(rr.getSveaOrderId()));
				}
				if (rr.getSveaOrderCreationDate()!=null) {
					XMLGregorianCalendar odate = rr.getSveaOrderCreationDate();
					d.setOrderDate(JsonUtil.getDateFormat().format(odate.toGregorianCalendar().getTime()));
				}
				
				totalRegressions += rr.getTransactionAmount().doubleValue();
				
				// Check for fees
				if (rr.getFee().signum() != 0) {
					fee = new FeeDetail(FeeDetail.FEETYPE_ADM, rr.getFee()
							.doubleValue(), 0D // TODO: Is regression fees always of type adm?
					);
					fee.calculateVat(vatRateAdmFee);
					FeeDetail.remapFeeAccount(cre, fee);
					d.addFee(fee);
				}

				// Calculate received amt
				d.calculateReceivedAmt();

				gr.addDetail(d);
				
			}
			
			if (totalRegressions!=0d) {
				
				PaymentReportDetail regressionDetail = gr.findDetailWithFeeAmount(FeeDetail.FEETYPE_DEVIATIONS,  -totalRegressions);
				if (regressionDetail!=null) {
					
					// If the regression detail is found, remove it now.
					gr.removeDetail(regressionDetail);
				} else {
					
					// Add a reverse fee for regressions since it's summarized in financial report.
					FeeDetail reverseRegressionAmtFee = new FeeDetail();
					reverseRegressionAmtFee.setFee(-totalRegressions);
					reverseRegressionAmtFee.setFeeVat(0d);
					reverseRegressionAmtFee.setFeeType(FeeDetail.FEETYPE_REGRESS);
					reverseRegressionAmtFee.setAccountNr(cre.getAccountMap().get(FeeDetail.ACCTTYPE_DEVIATIONS));
					gr.addOtherFee(reverseRegressionAmtFee);
				}
			}
			
		}
		
		List<FeeDetail> otherFees = getOtherFeesFromAccountingSuggestion(gr, date);
		
		// Update the total fees in the group (rounding)
		gr.updateTotalFees();
	
		// Add the other fees to the other fee list, but exclude if the fee is a credit fee
		gr.combineTotalOtherFees(otherFees, new String[]{FeeDetail.FEETYPE_CREDIT, FeeDetail.FEETYPE_ADM}, true);
		
		// TODO: This VAT-calculation should be before combineTotalOtherFees
		// Get summary amount that should land on the account today
		double sveasBankTotal = getAmountOnBankFromSvea(date);
		if (sveasBankTotal!=gr.getTotalReceivedAmt()) {
			gr.setTotalReceivedAmt(sveasBankTotal);
			// Check for VAT correction
			// If there's unaccounted VAT, add that to make it even out
			// This is to compensate for the card transactions
			double totalVat = gr.getTotalVatAmt();
			double invoiceVat = FeeDetail.getVatSum(gr.getTotalInvoiceFees());
			double otherVat = FeeDetail.getVatSum(gr.getTotalOtherFees());
			double diffVat = totalVat - invoiceVat - otherVat;
			diffVat = FeeDetail.roundFee(diffVat, FeeDetail.DEFAULT_ROUNDING_DECIMALS);
			
			if (diffVat>0) { 
				FeeDetail add = new FeeDetail();
				add.setFee(0D);
				add.setFeeVat(diffVat);
				FeeDetail.add(otherFees, add);
			}
		}
		
		// Remap other fees
		FeeDetail.remapFeeAccounts(cre, gr.getTotalOtherFees());
		// Remap invoice fees on group level
		FeeDetail.remapFeeAccounts(cre, gr.getTotalInvoiceFees());
		
	}
	
	/**
	 * Fills report group with details from payment plan
	 * 
	 * @param gr
	 * @param date
	 * @throws Exception
	 */
	private void fillFromPaymentPlan(PaymentReport report, PaymentReportGroup gr, java.sql.Timestamp date) throws Exception {

		PaymentReportDetail d;
		FeeDetail fee;

		// Find all transactions for the given date
		List<PaymentPlanReportRow> invoices = getPaymentPlanReport(date);

		for (PaymentPlanReportRow invoice : invoices) {

			d = new PaymentReportDetail();

			d.setCustomerId(Long.toString(invoice.getCustomerId()));
			d.setInvoiceId(Long.toString(invoice.getContractId()));
			d.setClientOrderNo(invoice.getOrderId());
			d.setPayerName(invoice.getName());
			d.setPaidAmt(invoice.getAmount().doubleValue());
			if (invoice.getCheckoutOrderId()!=null && invoice.getCheckoutOrderId()>0)
				d.setCheckoutOrderId(invoice.getCheckoutOrderId().toString());
			if (invoice.getSveaOrderId()!=null && invoice.getSveaOrderId()>0) {
				d.setOrderId(invoice.getSveaOrderId().toString());
			}
			if (invoice.getSveaOrderCreationDate()!=null) {
				XMLGregorianCalendar odate = invoice.getSveaOrderCreationDate();
				d.setOrderDate(JsonUtil.getDateFormat().format(odate.toGregorianCalendar().getTime()));
			}

			// Check for fees
			if (invoice.getAdministrationFee().signum() != 0) {
				fee = new FeeDetail(FeeDetail.FEETYPE_ADM, invoice
						.getAdministrationFee().doubleValue(), 0D // No
																	// VAT
				);
				FeeDetail.remapFeeAccount(cre, fee);
				d.addFee(fee);
			}

			// Calculate received amt
			d.setReceivedAmt(invoice.getPayToClientAmount()
					.doubleValue());

			gr.addDetail(d);
		}
		
		
		// Read regressions
		List<RegressionReportRow> regressions = getRegressionReport(date);
		
		if (regressions!=null && regressions.size()>0) {
			
			for (RegressionReportRow rr : regressions) {
				
				d = new PaymentReportDetail();
				
				d.setCustomerId(Long.toString(rr.getCustomerId()));
				d.setInvoiceId(Long.toString(rr.getInvoiceId()));
				d.setClientOrderNo(rr.getClientOrderNumber());
				d.setPayerName(rr.getName());
				d.setPaidAmt(-rr.getTransactionAmount().doubleValue()+(rr.getFee()!=null ? rr.getFee().doubleValue() : 0d));
				if (rr.getCheckoutOrderId()!=null && rr.getCheckoutOrderId()>0)
					d.setCheckoutOrderId(rr.getCheckoutOrderId().toString());
				if (rr.getSveaOrderId()>0) {
					d.setOrderId(Long.toString(rr.getSveaOrderId()));
				}
				if (rr.getSveaOrderCreationDate()!=null) {
					XMLGregorianCalendar odate = rr.getSveaOrderCreationDate();
					d.setOrderDate(JsonUtil.getDateFormat().format(odate.toGregorianCalendar().getTime()));
				}
				
				// Check for fees
				if (rr.getFee().signum() != 0) {
					fee = new FeeDetail(FeeDetail.FEETYPE_ADM, rr.getFee()
							.doubleValue(), 0D 
					);
					fee.calculateVat(VatType.getVatRate(cre.getCountryCode(), fee.getFeeType(), cre.getAccountType(), date, cre.isCompany()));
					FeeDetail.remapFeeAccount(cre, fee);
					d.addFee(fee);
				}

				// Calculate received amt
				d.calculateReceivedAmt();

				gr.addDetail(d);
				
			}
			
			
		}
		
		// ============ KICKBACK ============
		// Check if we have kickback-fees and if they should be expanded
		if (cre.isIncludeKickbacks()) {
			List<PaymentReportDetail> detailList = gr.getPaymentReportDetail();
			List<PaymentReportDetail> kickbackSums = new ArrayList<PaymentReportDetail>();
			
			if (detailList!=null && detailList.size()>0) {
			
				for (PaymentReportDetail detail : detailList) {
					
					if (detail.getPaidAmt()==0D 
							&& detail.getFees()!=null 
							&& detail.getFees().size()==1 
							&& FeeDetail.FEETYPE_KICKBACK.equals(detail.getFees().get(0).getFeeType())) {
						
						kickbackSums.add(detail);
					}
	
				}
				
				if (kickbackSums.size()>0) {
					
					List<PaymentReportGroup> kickbackGroups = null;
					WebpayKickbackReportFactory wkr = new WebpayKickbackReportFactory();
					wkr.init(cre);
					kickbackGroups = wkr.createBankStatementLines(report, date, date);
					List<PaymentReportGroup> groupsToAdd = new ArrayList<PaymentReportGroup>();
					
					// Compare kickback groups with the kickback fees
					for (PaymentReportDetail kbs : kickbackSums) {
	
						for (PaymentReportGroup kgs : kickbackGroups) {
							
							if (kbs.getReceivedAmt().doubleValue() == kgs.getTotalReceivedAmt().doubleValue()) {
								// Replace this with the group
								groupsToAdd.add(kgs);
								gr.removeDetail(kbs);
							}
							
						}
						
					}
					
					for (PaymentReportGroup ggr : groupsToAdd) {
						for (PaymentReportDetail dd : ggr.getPaymentReportDetail()) {
							gr.addDetail(dd);
						}
					}
					
				}
				
			}
		}
		
	}
	
	
	
	/**
	 * Get regression report
	 * 
	 * @param date
	 * @return
	 * @throws Exception
	 */
	public List<RegressionReportRow> getRegressionReport(java.sql.Timestamp date)
			throws Exception {

		GetRegressionReportRequest request = new GetRegressionReportRequest();
		request.setClientId(new Integer(cre.getAccountNo()));
		request.setAuthentication(svea.getAuth());
		XMLGregorianCalendar xdate = convert(date);
		request.setFromDate(xdate);
		request.setToDate(xdate);

		GetRegressionReportResponse response = svea.getServicePort()
				.getRegressionReport(request);

		ArrayOfRegressionReportRow res = response.getReportRows();
		
		return res!=null ? res.getRegressionReportRow() : null;

	}

	/**
	 * Get invoice report
	 * 
	 * @param date
	 * @return
	 * @throws Exception
	 */
	public List<InvoiceReportRow> getInvoiceReport(java.sql.Timestamp date)
			throws Exception {

		if (cre.getAccountNo()==null || cre.getAccountNo().trim().length()==0) return null;
		GetInvoiceReportRequest request = new GetInvoiceReportRequest();
		request.setClientId(new Integer(cre.getAccountNo()));
		request.setAuthentication(svea.getAuth());
		XMLGregorianCalendar xdate = convert(date);
		request.setFromDate(xdate);
		request.setToDate(xdate);
		
		GetInvoiceReportResponse response = null;
		try {
			response = svea.getServicePort()
				.getInvoiceReport(request);
		} catch (Exception e) {
			throw new Exception(svea.getEndpointAddress() + ": " + e.getMessage());
		}

		if (response.getErrorMessage()!=null && response.getErrorMessage().trim().length()>0)
			throw new Exception("Can't read invoice report for account " + cre.getAccountNo() + ": " + response.getErrorMessage());
		
		ArrayOfInvoiceReportRow res = response.getReportRows();
		return res.getInvoiceReportRow();

	}

	/**
	 * Get payment plan report
	 * 
	 * @param date
	 * @return
	 * @throws Exception
	 */
	public List<PaymentPlanReportRow> getPaymentPlanReport(
			java.sql.Timestamp date) throws Exception {

		GetPaymentPlanReportRequest request = new GetPaymentPlanReportRequest();
		request.setClientId(new Integer(cre.getAccountNo()));
		request.setAuthentication(svea.getAuth());
		XMLGregorianCalendar xdate = convert(date);
		request.setFromDate(xdate);
		request.setToDate(xdate);

		GetPaymentPlanReportResponse response = svea.getServicePort()
				.getPaymentPlanReport(request);

		ArrayOfPaymentPlanReportRow res = response.getReportRows();
		if (res == null)
			return null;
		return res.getPaymentPlanReportRow();

	}


	
	

	

}
