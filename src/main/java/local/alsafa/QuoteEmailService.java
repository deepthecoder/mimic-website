package local.alsafa;

import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class QuoteEmailService {
    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String recipient;
    private final String from;

    public QuoteEmailService(ObjectProvider<JavaMailSender> sender,
            @Value("${spring.mail.host:}") String host,
            @Value("${app.quote.to-email:}") String recipient,
            @Value("${app.quote.from-email:}") String from) {
        this.sender = sender;
        this.host = host;
        this.recipient = recipient;
        this.from = from;
    }

    public boolean configured() {
        return !host.isBlank() && !recipient.isBlank() && !from.isBlank()
                && sender.getIfAvailable() != null;
    }

    public void send(Map<String, String> fields, String id) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(recipient);
        mail.setReplyTo(fields.get("email"));
        mail.setSubject(fields.containsKey("mobile") ? "New Get Quote request" : "New Contact request");
        mail.setText("Submission ID: " + id + "\n\n"
                + "Name: " + fields.getOrDefault("name", "") + "\n"
                + "Company: " + fields.getOrDefault("company", "") + "\n"
                + "Email: " + fields.getOrDefault("email", "") + "\n"
                + "Phone: " + fields.getOrDefault("mobile", fields.getOrDefault("contact", "")) + "\n"
                + "Service: " + fields.getOrDefault("service", "") + "\n"
                + "Message: " + fields.getOrDefault("message", "") + "\n");
        sender.getObject().send(mail);
    }
}
