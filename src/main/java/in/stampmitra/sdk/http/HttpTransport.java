package in.stampmitra.sdk.http;

import java.io.IOException;

/**
 * Pluggable HTTP layer. The default implementation, {@link JdkHttpTransport}, uses {@code java.net.http.HttpClient}.
 * Implement this interface to adapt another client (OkHttp, Apache HttpClient, Vert.x, ...).
 *
 * <p>Contract for implementations:
 *
 * <ul>
 *   <li><b>Never follow redirects.</b> The request carries the API key; a 3xx must be returned as-is so the SDK can
 *       report it instead of forwarding credentials to another host.
 *   <li>Return every HTTP status as a {@link TransportResponse}; do not throw for 4xx/5xx.
 *   <li>Honour {@link TransportRequest#getTimeout()} as the total time allowed for this attempt and signal a timeout
 *       with {@link java.net.http.HttpTimeoutException}, {@link java.net.SocketTimeoutException} or
 *       {@link TransportTimeoutException}.
 *   <li>Throw {@link IOException} for connection-level failures. Other exceptions are not retried.
 *   <li>Be thread-safe: one transport serves all calls of a client.
 *   <li>Never log request headers (they contain the API key).
 * </ul>
 */
@FunctionalInterface
public interface HttpTransport {

    /**
     * Sends one HTTP request and blocks until the complete response is available.
     *
     * @param request the request to send
     * @return the response, for any HTTP status
     * @throws IOException on connection failures and timeouts
     */
    TransportResponse send(TransportRequest request) throws IOException;
}
