package com.svea.webpayadmin;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;

import com.svea.webpay.common.auth.ListOfSveaCredentials;
import com.svea.webpay.common.auth.SveaCredential;
import com.svea.webpay.common.reconciliation.FeeDetail;
import com.svea.webpay.common.reconciliation.PaymentReportDetail;
import com.svea.webpay.common.reconciliation.PaymentReportGroup;
import com.svea.webpayadminservice.client.ArrayOfClientData;
import com.svea.webpayadminservice.client.ArrayOfDeliverOrderInformation;
import com.svea.webpayadminservice.client.ArrayOfGetInvoiceInformation;
import com.svea.webpayadminservice.client.ArrayOfInvoice;
import com.svea.webpayadminservice.client.ArrayOfPaymentPlanStatus;
import com.svea.webpayadminservice.client.ArrayOflong;
import com.svea.webpayadminservice.client.Authentication;
import com.svea.webpayadminservice.client.ClientData;
import com.svea.webpayadminservice.client.CustomerIdentity;
import com.svea.webpayadminservice.client.DeliverOrderInformation;
import com.svea.webpayadminservice.client.DeliveryRequest;
import com.svea.webpayadminservice.client.DeliveryResponse;
import com.svea.webpayadminservice.client.GetClientsByIdentityAccountRequest;
import com.svea.webpayadminservice.client.GetClientsByIdentityAccountResponse2;
import com.svea.webpayadminservice.client.GetInvoiceInformation;
import com.svea.webpayadminservice.client.GetInvoicesRequest;
import com.svea.webpayadminservice.client.GetInvoicesResponse2;
import com.svea.webpayadminservice.client.Invoice;
import com.svea.webpayadminservice.client.InvoiceDistributionType;
import com.svea.webpayadminservice.client.OrderType;
import com.svea.webpayadminservice.client.PaymentPlanListItem;
import com.svea.webpayadminservice.client.PaymentPlanStatus;
import com.svea.webpayadminservice.client.SearchPaymentPlanFilter;
import com.svea.webpayadminservice.client.SearchPaymentPlanSorting;
import com.svea.webpayadminservice.client.SearchPaymentPlansRequest;
import com.svea.webpayadminservice.client.SearchPaymentPlansResponse2;
import com.svea.webpayadminservice.client.SortDirection;
import com.svea.webpayadminservice.client.SortPaymentPlanProperty;
import com.svea.webpayadminservice.client.TextMatchType;

/**
 * Class for easier access to the client functions.
 * 
 * @author daniel
 *
 */
public class WebpayAdminClient extends WebpayAdminBase {

	public static Logger log = org.slf4j.LoggerFactory.getLogger(WebpayAdminClient.class);		
	
	// Set logging properties to avoid verbose logging / warnings from webpayadminservice-client
	static {
		
		try {

			// Find logging.properties
			URL url = WebpayAdminClient.class.getClassLoader().getResource("logging.properties");
			if (url!=null) {
				System.setProperty("java.util.logging.config.file", url.getFile());
			}
			
		} catch (Exception e) {
			
		}
		
	}
	
	
	/**
	 * Gets clients by identity account. This means that the user name and password
	 * that the client uses should be the supplied authentication. With the supplied
	 * authentication, the accounts belonging to that authentication are returned.

	 * @param	username	The client's username
	 * @param	password	The client's password
	 * 
	 * @return	ListOfSveaCredentials containing
	 * - AccountNo (clientId)
	 * - CountryCode
	 * - Currency
	 * - AccountType (converted from agreement type)
	 * - Username
	 * - Password
	 * 
	 * @throws Exception
	 */
	public ListOfSveaCredentials getCredentialsByIdentity(String username, String password) throws Exception {
		
		GetClientsByIdentityAccountRequest req = new GetClientsByIdentityAccountRequest();
		Authentication auth = new Authentication();
		auth.setUsername(username);
		auth.setPassword(password);
		req.setAuthentication(auth);
		
		GetClientsByIdentityAccountResponse2 response = adminServicePort.getClientsByIdentityAccount(req);
		
		if (response.getErrorMessage()!=null && response.getErrorMessage().trim().length()>0) {
			throw new Exception(response.getErrorMessage());
		}
		
		SveaCredential sc = null;
		ListOfSveaCredentials result = new ListOfSveaCredentials();
		List<SveaCredential> lsc = result.getCredentials();
		
		if (response.getClients()!=null) {
			ArrayOfClientData alist = response.getClients();
			List<ClientData> list = alist.getClientData();
			Map<String,SveaCredential> invoiceCreds = new TreeMap<String,SveaCredential>();
			
			for (ClientData cd : list) {
				sc = convert(cd);
				sc.setUsername(username);
				sc.setPassword(password);
				sc.createDefaultAccountMap();
				if (SveaCredential.ACCOUNTTYPE_INVOICE.equalsIgnoreCase(sc.getAccountType())) {
					// Set include card payments on all invoice accounts since
					// there's no general rule where the cards are reported.
					sc.setIncludeCardPayments(true);
					
					// invoiceCreds.put(sc.getAccountNo(), sc);
				}
				lsc.add(sc);
			}
			
			// Check for invoice accounts and set the lowest clientID to include card payments
			/*SveaCredential lowestInvoiceCred;
			if (!invoiceCreds.isEmpty()) {
				lowestInvoiceCred = invoiceCreds.get(invoiceCreds.keySet().iterator().next()); 
				lowestInvoiceCred.setIncludeCardPayments(true);
			}*/
			
		}
		
		return result;
	}
	
	/**
	 * Converts a ClientData to SveaCredential
	 * 
	 * @param cd		ClientData
	 * @return	A SveaCredential containing
	 * - AccountNo (clientId)
	 * - CountryCode
	 * - Currency
	 * - AccountType ( a number? )
	 */
	private SveaCredential convert(ClientData cd) {
		
		SveaCredential sc = new SveaCredential();
		
		sc.setAccountNo(cd.getClientId().toString());
		sc.setCountryCode(cd.getCountry());
		sc.setCurrency(cd.getCurrency());
		
		// Do mapping between agreement type and accounttype
		// Null if no mapping is found
		sc.setAccountType(SveaCredential.AGREEMENT_TYPE_MAP.get(cd.getAgreementType()));
		
		return sc;
		
	}
	
	/**
	 * Delivers given order.
	 * 
	 * Invoice number is returned in DeliveryResponse.getOrdersDelivered.getDeliverOrderResult().getDeliveryReferenceNumber()
	 * 
	 * @param sveaOrderId
	 * @param ot					
	 * @param printType				0 = Svea Prints, 1 = Client prints
	 * @return
	 */
	public DeliveryResponse deliverOrder(long sveaOrderId, OrderType ot, Integer printType) {
		
		DeliveryRequest req = new DeliveryRequest();
		
		req.setAuthentication(auth);
		req.setInvoiceDistributionType(InvoiceDistributionType.NOT_DEFINED);
		ArrayOfDeliverOrderInformation aa = new ArrayOfDeliverOrderInformation();
		req.setOrdersToDeliver(aa);
		List<DeliverOrderInformation> ol = aa.getDeliverOrderInformation();
		DeliverOrderInformation doi = new DeliverOrderInformation();
		doi.setSveaOrderId(sveaOrderId);
		doi.setOrderType(ot);
		doi.setPrintType(printType);
		doi.setClientId(Long.parseLong(cre.getAccountNo()));
		ol.add(doi);
		
		DeliveryResponse dr = adminServicePort.deliverOrders(req);
		
		return dr;
	}
	
	/**
	 * Method that enriches a payment detail list with information from SeachPaymentPlansResponse
	 */
	public void enrichFromPaymentPlan(String paymentTypeReference, List<PaymentReportDetail> details, boolean enrichAll, boolean skipTaxId) throws Exception {
		
		if (details==null || details.size()==0) return;
		
		SearchPaymentPlansRequest req = new SearchPaymentPlansRequest();
		req.setAuthentication(auth);
		
		
		// Sorting
		SearchPaymentPlanSorting sort = new SearchPaymentPlanSorting();
		sort.setSortDirection(SortDirection.DESCENDING);
		sort.setSortPaymentPlanProperty(SortPaymentPlanProperty.SVEA_ORDER_ID);
		req.setSearchPaymentPlanSorting(sort);
		
		SearchPaymentPlanFilter filter = new SearchPaymentPlanFilter();
		ArrayOflong clientIds = new ArrayOflong();
		List<Long> ids = clientIds.getLong();
		ids.add(Long.parseLong(cre.getAccountNo()));
		filter.setClientIds(clientIds);
		ArrayOfPaymentPlanStatus aops = new ArrayOfPaymentPlanStatus();
		List<PaymentPlanStatus> pps = aops.getPaymentPlanStatus();
		filter.setAcceptedStatus(aops);
		pps.add(PaymentPlanStatus.CANCELLED);
		pps.add(PaymentPlanStatus.FINISHED);
		pps.add(PaymentPlanStatus.ERROR);
		pps.add(PaymentPlanStatus.WAITING_FOR_CONTRACT);
		pps.add(PaymentPlanStatus.WAITING_TO_BE_SENT);
		
		filter.setTextMatchType(TextMatchType.CONTRACT_NUMBER);
		req.setSearchPaymentPlanFilter(filter);
		
		SearchPaymentPlansResponse2 response;
		
		for (PaymentReportDetail d : details) {
			
			if (enrichAll || (d.getEnrichFromInvoice()!=null && d.getEnrichFromInvoice())) {
			
				filter.setTextMatch(d.getInvoiceId());
				response = adminServicePort.searchPaymentPlans(req);
				
				if (response.getResultCode()!=0) {
					throw new Exception(response.getErrorMessage());
				}
				
				if (response.getPaymentPlanListItems()!=null && response.getPaymentPlanListItems().getPaymentPlanListItem()!=null) {
					List<PaymentPlanListItem> rows = response.getPaymentPlanListItems().getPaymentPlanListItem();
					if (rows.size()>0) {
						PaymentPlanListItem pp = rows.get(0);
						enrichWithPaymentPlanDetails(d, pp, cre.isSkipTaxId());
					}
				}
				
			}
		}
		
	}
	
	
	/**
	 * Method that enriches a payment detail list with information from WSDL-method GetInvoices
	 * 
	 * @param details			The details to be enriched.
	 * @param enrichAll			If true, all details are enriched. If false, only details with flag enrichFromInvoice are enriched.
	 * @param skipTaxId			If true, tax id is not included
	 * @param skipEmail			If true, email is not included
	 * @throws Exception 
	 */
	public void enrichFromInvoice(String paymentTypeReference, List<PaymentReportDetail> details, boolean enrichAll, boolean skipTaxId, boolean skipEmail) throws Exception {
		
		if (details==null || details.size()==0) return;
		
		List<String> invoicesToRetrieve = new ArrayList<String>();
		List<PaymentReportDetail> paymentDetails = new ArrayList<PaymentReportDetail>();
		
		for (PaymentReportDetail d : details) {
			if (d.getInvoiceId()!=null && d.getInvoiceId().trim().length()>0) {
				if (enrichAll || (d.getEnrichFromInvoice()!=null && d.getEnrichFromInvoice())) {
					invoicesToRetrieve.add(d.getInvoiceId());
					paymentDetails.add(d);
				}
			}
		}
		
		if (invoicesToRetrieve.size()>0) {
		
			GetInvoicesRequest req = new GetInvoicesRequest();
			req.setAuthentication(auth);
			
			ArrayOfGetInvoiceInformation aa = new ArrayOfGetInvoiceInformation();
			req.setInvoicesToRetrieve(aa);
			List<GetInvoiceInformation> il = aa.getGetInvoiceInformation();
			
			GetInvoiceInformation ii;
			for (String invoiceNo : invoicesToRetrieve) {
				ii = new GetInvoiceInformation();
				ii.setClientId(Long.parseLong(paymentTypeReference));
				ii.setInvoiceId(Long.parseLong(invoiceNo));
				il.add(ii);
			}
			
			GetInvoicesResponse2 res = adminServicePort.getInvoices(req);
			
			if (res.getResultCode()!=0 && res.getResultCode()!=24001) {		// Don't throw error on invoice not found
				throw new Exception(res.getResultCode() + " : " + res.getErrorMessage());
			}
			
			// Create an invoice map for fast access in case the invoices are unsorted
			ArrayOfInvoice aoi = res.getInvoices();
			if (aoi!=null) {
			
				List<Invoice> invoiceList = aoi.getInvoice();
				
				Map<String,Invoice> invoiceMap = new TreeMap<String,Invoice>();
				for (Invoice i : invoiceList) {
					invoiceMap.put(new Long(i.getInvoiceId()).toString(), i);
				}
				
				for (PaymentReportDetail d : paymentDetails) {
					enrichWithInvoiceDetails(d, invoiceMap.get(d.getInvoiceId()), skipTaxId, skipEmail);
				}
				
			}
			
		}
		
	}

	/**
	 * Enriches a payment detail with information from a payment plan list item.
	 * 
	 * @param dst
	 * @param src
	 * @param skipTaxId
	 * @return
	 */
	public PaymentReportDetail enrichWithPaymentPlanDetails(PaymentReportDetail dst, PaymentPlanListItem src, boolean skipTaxId) {
		
		if (dst.getPayerName()==null || dst.getPayerName().trim().length()==0) {
			dst.setPayerName(src.getCustomerName());
		}
		
		if (!skipTaxId) {
			dst.setPayerOrgNo(src.getNationalIdNumber());
		}

		if (dst.getClientOrderNo()==null || dst.getClientOrderNo().trim().length()==0) {
			dst.setClientOrderNo(src.getClientOrderId());
		}
		
		dst.setOrderId(Long.toString(src.getSveaOrderId()));
		
		return dst;
	}
	
	
	/**
	 * Enriches a payment detail with information from an invoice record
	 * 
	 * @param dst
	 * @param src
	 * @param skipTaxId
	 * @param skipEmail
	 * @return
	 */
	public PaymentReportDetail enrichWithInvoiceDetails(PaymentReportDetail dst, Invoice src, boolean skipTaxId, boolean skipEmail) {

		if (src==null) return dst;

		CustomerIdentity c = src.getCustomer();

		if (c!=null) {
			if (!skipTaxId)
				dst.setPayerOrgNo(c.getNationalIdNumber());
			if (!skipEmail && c.getEmail()!=null && c.getEmail().trim().length()>0) {
				dst.addReference(PaymentReportDetail.REF_EMAIL, c.getEmail());
			}
			// TODO: Perhaps make an own variable for skipping zipCode
			if (!skipEmail && c.getZipCode()!=null) {
				dst.addReference(PaymentReportDetail.REF_ZIPCODE, c.getZipCode());
			}
			if (dst.getPayerName()==null || dst.getPayerName().trim().length()==0) {
				dst.setPayerName(c.getFullName());
			}
		}

		if (dst.getCustomerId()==null || dst.getCustomerId().trim().length()==0) {
			dst.setCustomerId(Long.toString(src.getCustomerId()));
		}
		
		if (dst.getClientOrderNo()==null || dst.getClientOrderNo().trim().length()==0) {
			dst.setClientOrderNo(src.getClientOrderId());
		}
		
		dst.setOrderId(Long.toString(src.getSveaOrderId()));
		
		if (src.getClientReference()!=null && src.getClientReference().trim().length()>0) {
			dst.addReference(PaymentReportDetail.REF_CLIENT_REF, src.getClientReference());
		}
		
		return dst;
	}

	/**
	 * Updates fees that have missing accounting suggestions with accounts from the credential parameter.
	 * 
	 * @param cre
	 * @param grp		The group to be updated
	 */
	public static void updateUndefinedFeeAccountsFromCredential(SveaCredential cre, PaymentReportGroup grp) {

		if (cre==null || grp==null || grp.isEmpty()) return;

		if (grp.getPaymentReportDetail()!=null) {
			
			for (PaymentReportDetail d : grp.getPaymentReportDetail()) {
				updateFeeAccountsIfNotSet(cre, d.getFees());
			}
			
		}
		
		updateFeeAccountsIfNotSet(cre, grp.getTotalInvoiceFees());
		updateFeeAccountsIfNotSet(cre, grp.getTotalOtherFees());
		
	}

	/**
	 * Updates fee accounts in a list of fees if they are not already set.
	 * 
	 * @param cre
	 * @param fees
	 */
	private static void updateFeeAccountsIfNotSet(SveaCredential cre, List<FeeDetail> fees) {

		if (fees==null) return;
		
		for (FeeDetail f : fees) {
			if (f.getAccountNr()==null || f.getAccountNr().trim().length()==0) {
				f.setAccountNr(cre.getRemappedAccountFor(f.getFeeType()));
			}
		}
		
	}
	
}
