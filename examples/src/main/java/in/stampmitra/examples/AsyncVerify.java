package in.stampmitra.examples;

import in.stampmitra.sdk.StampMitra;
import in.stampmitra.sdk.models.GstinVerification;
import in.stampmitra.sdk.models.PanVerification;
import java.util.concurrent.CompletableFuture;

/** Runs two verifications concurrently with the {@code *Async} methods. */
public final class AsyncVerify {

    private AsyncVerify() {
    }

    public static void main(String[] args) {
        StampMitra client = Examples.client();

        CompletableFuture<PanVerification> pan = client.verify().panAsync("ABCDE1234F");
        CompletableFuture<GstinVerification> gstin = client.verify().gstinAsync("27AABCT1332L1ZZ");

        pan.thenCombine(gstin, (p, g) -> "pan valid=" + p.isValid() + ", gstin valid=" + g.isValid())
                .exceptionally(error -> "failed: " + error.getCause().getMessage())
                .thenAccept(System.out::println)
                .join();
    }
}
