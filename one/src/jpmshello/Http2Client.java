package jpmshello;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static java.time.temporal.ChronoUnit.SECONDS;

class Http2Client {

    private Http2Client() {}

    static void sendAsync() {
        try {
            HttpRequest request = getHttpRequest();

            CompletableFuture<HttpResponse<String>> response = HttpClient.newBuilder()
                    .proxy(ProxySelector.getDefault())
                    .build()
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response status code: " + response.get().statusCode());
            System.out.println("Response headers: " + response.get().headers());
            System.out.println("Response body: " + response.get().body());
        } catch (URISyntaxException | InterruptedException | ExecutionException e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }

    private static HttpRequest getHttpRequest() throws URISyntaxException {
        return HttpRequest.newBuilder()
                        .uri(new URI("https://postman-echo.com/get"))
                        .version(HttpClient.Version.HTTP_2)
                        .timeout(Duration.of(10, SECONDS))
                        .GET()
                        .build();
    }


    static void sendMultipleRequest() {
        try {
            ExecutorService executor = Executors.newFixedThreadPool(6);

            HttpClient httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            HttpRequest mainRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://http2.akamai.com/demo/h2_demo_frame.html"))
                    .build();

            HttpResponse<String> mainResponse = httpClient.send(mainRequest, HttpResponse.BodyHandlers.ofString());

            List<Future<?>> futures = new ArrayList<>();

            // For each image resource in the main HTML, send a request on a separate thread
            mainResponse.body().lines()
                    .filter(line -> line.trim().startsWith("<img height"))
                    .map(line -> line.substring(line.indexOf("src='") + 5, line.indexOf("'/>")))
                    .forEach(image -> {
                        Future imgFuture = executor.submit(() -> {
                            HttpRequest imgRequest = HttpRequest.newBuilder()
                                    .uri(URI.create("https://http2.akamai.com" + image))
                                    .build();
                            try {
                                HttpResponse<String> imageResponse = httpClient.send(imgRequest, HttpResponse.BodyHandlers.ofString());
                                System.out.println("Loaded " + image + ", status code: " + imageResponse.statusCode());
                            } catch (IOException | InterruptedException ex) {
                                System.out.println("Error during image request for " + image + ", Exception: " + ex);
                            }
                        });
                        futures.add(imgFuture);
                    });

            // Wait for all submitted image loads to be completed
            futures.forEach(f -> {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException ex) {
                    System.out.println("Error waiting for image load" + ex);
                }
            });
        } catch (IOException | InterruptedException e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }
}
