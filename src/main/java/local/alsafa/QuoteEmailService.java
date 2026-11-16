
package local.alsafa;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class QuoteEmailService {

    private final RestClient restClient;
    private final String apiKey;
    private final String recipient;
    private final String from;

    public QuoteEmailService(
            RestClient.Builder restClientBuilder,
            @Value("${app.email.api-key:}") String apiKey,
            @Value("${app.quote.to-email:}") String recipient,
            @Value("${app.quote.from-email:}") String from) {

        this.restClient = restClientBuilder
                .baseUrl("https://api.resend.com")
                .build();

        this.apiKey = apiKey;
        this.recipient = recipient;
        this.from = from;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank()
                && recipient != null && !recipient.isBlank()
                && from != null && !from.isBlank();
    }

    public void send(Map<String, String> fields, String id) {

        if (!configured()) {
            throw new IllegalStateException(
                    "Email service is not configured.");
        }

        Map<String, Object> email = new LinkedHashMap<>();

        email.put("from", from);
        email.put("to", recipient);
        email.put("subject",
                fields.containsKey("mobile")
                        ? "New Get Quote request"
                        : "New Contact request");

        String visitorEmail = fields.getOrDefault("email", "");

        if (!visitorEmail.isBlank()) {
            email.put("reply_to", visitorEmail);
        }

        email.put("text",
                "Submission ID: " + id + "\n\n"
                + "Name: " + fields.getOrDefault("name", "") + "\n"
                + "Company: " + fields.getOrDefault("company", "") + "\n"
                + "Email: " + visitorEmail + "\n"
                + "Phone: " + fields.getOrDefault(
                        "mobile",
                        fields.getOrDefault("contact", "")) + "\n"
                + "Service: " + fields.getOrDefault("service", "") + "\n"
                + "Message: " + fields.getOrDefault("message", "") + "\n");

        restClient.post()
                .uri("/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(email)
                .retrieve()
                .toBodilessEntity();
    }
}
