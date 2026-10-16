package in.stampmitra.examples;

import in.stampmitra.sdk.StampMitra;
import in.stampmitra.sdk.errors.RateLimitException;
import in.stampmitra.sdk.errors.StampMitraException;
import in.stampmitra.sdk.models.PanVerification;

/** Verifies a PAN. Every call is billed, including results with {@code valid=false}. */
public final class VerifyPan {

    private VerifyPan() {
    }

    public static void main(String[] args) {
        StampMitra client = Examples.client();
        try {
            PanVerification pan = client.verify().pan("ABCDE1234F", "Rahul Kumar");
            System.out.println((pan.isValid() ? "valid" : "invalid") + " name-match=" + pan.getNameMatchResult()
                    + " reference=" + pan.getReferenceId());
            System.out.println("request attempts: " + pan.getResponse().getAttempts());
        } catch (RateLimitException e) {
            System.err.println("rate limited, retry later (retry-after=" + e.getRetryAfterSeconds() + ")");
        } catch (StampMitraException e) {
            System.err.println(e.getStatus() + " " + e.getErrorCode() + " " + e.getMessage() + " request-id="
                    + e.getRequestId());
        }
    }
}
