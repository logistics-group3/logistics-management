package com.sparta.logistics.order.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 테스트 전용 HTTP 서버. Feign을 Mock으로 바꾸지 않고 실제 요청·응답·타임아웃을 확인한다. */
public final class HttpStubServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentLinkedQueue<RecordedRequest> requests = new ConcurrentLinkedQueue<>();
    private volatile Function<RecordedRequest, Reply> responder = request -> Reply.json(404, "{}");

    public HttpStubServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reset(Function<RecordedRequest, Reply> responder) {
        requests.clear();
        this.responder = responder;
    }

    public List<RecordedRequest> requests(String method, String pathPrefix) {
        return requests.stream()
                .filter(request -> request.method().equals(method) && request.path().startsWith(pathPrefix))
                .toList();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> headers = exchange.getRequestHeaders().entrySet().stream()
                    .collect(Collectors.toMap(entry -> entry.getKey().toLowerCase(Locale.ROOT),
                            entry -> entry.getValue().get(0)));
            RecordedRequest request = new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), headers, exchange.getRequestURI().getRawQuery());
            requests.add(request);
            Reply reply = responder.apply(request);
            if (reply.delayMillis() > 0) {
                Thread.sleep(reply.delayMillis());
            }
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(reply.status(), reply.status() == 204 ? -1 : body.length);
            if (reply.status() != 204) {
                exchange.getResponseBody().write(body);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            // 타임아웃 테스트에서는 클라이언트가 먼저 연결을 닫으므로 응답 쓰기가 실패할 수 있다.
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    public record RecordedRequest(String method, String path, String body, Map<String, String> headers, String rawQuery) {
        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    public record Reply(int status, String body, long delayMillis) {
        public static Reply json(int status, String body) {
            return new Reply(status, body, 0);
        }

        public Reply delayed(long millis) {
            return new Reply(status, body, millis);
        }
    }
}
