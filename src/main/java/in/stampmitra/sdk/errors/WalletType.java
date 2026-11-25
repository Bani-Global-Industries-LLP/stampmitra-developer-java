package in.stampmitra.sdk.errors;

/** The wallet that is short on a 402 from an e-Stamp order ({@code error.details.wallet}). */
public enum WalletType {
    /** The Stamp duty wallet. */
    STAMP_DUTY,
    /** The Service wallet (the value keeps its {@code SERVICE_FEE} name for compatibility). */
    SERVICE_FEE
}
