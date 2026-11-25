package in.stampmitra.examples;

import in.stampmitra.sdk.StampMitra;
import in.stampmitra.sdk.models.StampDutyWalletTopup;
import in.stampmitra.sdk.requests.WalletTopupRequest;

/**
 * Reads both wallets (Stamp duty and Service) and the Stamp Duty tax invoices. Pass {@code --topup} to start a Stamp Duty top-up of
 * 1000 rupees: it creates a payment order and an invoice, so run it only on purpose.
 */
public final class EstampWallets {

    private EstampWallets() {
    }

    public static void main(String[] args) {
        StampMitra client = Examples.client();

        System.out.println("stamp duty wallet: " + client.wallets().stampDuty().getBalance().getBalance());
        System.out.println("service wallet: " + client.wallets().serviceFee().getBalance().getBalance());
        System.out.println("transactions: " + client.wallets().stampDuty().listTransactions(20, null).getItems().size());
        client.wallets().stampDuty().listInvoices(20, null).getItems()
                .forEach(i -> System.out.println("invoice " + i.getInvoiceNumber() + " fee tax " + i.getGstAmount()));

        if (args.length > 0 && args[0].equals("--topup")) {
            // The wallet is credited exactly what you add. The processing fee (6%) and the 18% GST on that fee are
            // charged on top; show the payer the figures the API returns, the SDK computes nothing.
            StampDutyWalletTopup topup = client.wallets().stampDuty().topup(WalletTopupRequest.builder().baseAmount(1000).build());
            System.out.println("pay " + topup.getTotalCharged() + " (fee " + topup.getProcessingFeeAmount() + ", GST " + topup.getGstAmount()
                    + "), wallet credited " + topup.getCreditAmount());
        }
    }
}
