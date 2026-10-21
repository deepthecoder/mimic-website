package local.alsafa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class LocalPagesController {
    private static final Logger LOG = LoggerFactory.getLogger(LocalPagesController.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$");
    private final LocalSubmissionService submissions;
    private final QuoteEmailService quotes;

    public LocalPagesController(LocalSubmissionService submissions, QuoteEmailService quotes) {
        this.submissions = submissions;
        this.quotes = quotes;
    }

    @GetMapping({"/index.php", "/about.php", "/services.php", "/capabilities.php",
            "/gallery.php", "/contact.php", "/career.php", "/blogs.php",
            "/creativeline.php", "/ksa.php", "/video.php",
            "/service-posm-display.php", "/service-designing-modelling.php",
            "/service-permanent-display.php", "/service-custom-kiosks.php",
            "/service-window-branding.php", "/service-retail-fitout.php",
            "/service-digital-printing.php", "/service-flatbed-printing.php",
            "/service-brand-activations.php", "/service-corporate-gifts.php",
            "/service-brand-promotors.php"})
    public String oldPublicUrl(jakarta.servlet.http.HttpServletRequest request) {
        return "forward:" + request.getServletPath() + ".html";
    }

    @GetMapping("/blog-details.php")
    public String blogDetail(@RequestParam(defaultValue = "1") String id) {
        return "forward:/blog-details-" + ("2".equals(id) ? "2" : "1") + ".html";
    }

    @GetMapping("/contact.php.html")
    public ResponseEntity<Resource> contactPage() {
        return staticPage("contact.php.html");
    }

    @GetMapping("/career.php.html")
    public ResponseEntity<Resource> careerPage() {
        return staticPage("career.php.html");
    }

    private static ResponseEntity<Resource> staticPage(String filename) {
        Resource page = new ClassPathResource("static/" + filename);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8")).body(page);
    }

    @PostMapping(value = "/quote", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> quote(@RequestParam Map<String, String> fields) throws IOException {
        if (!valid(fields, "name", "email", "mobile", "service")) {
            return page("index.html", "<form class=\"form-fields\"",
                    "Please enter a name, valid email, mobile number and service.", HttpStatus.BAD_REQUEST);
        }
        String id = submissions.save("quote", fields);
        return email(fields, id, "index.html", "<form class=\"form-fields\"",
                "Thanks! Your quote request was sent.");
    }

    private ResponseEntity<String> email(Map<String, String> fields, String id,
            String resource, String formPrefix, String success) throws IOException {
        if (!quotes.configured()) {
            submissions.delivery(id, "not_configured");
            return page(resource, formPrefix,
                    "Request saved locally, but email is not configured. Ask the site owner to configure SMTP and the recipient address.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        try {
            quotes.send(fields, id);
            try {
                submissions.delivery(id, "sent");
            } catch (IOException ex) {
                LOG.warn("Email sent, but delivery status was not recorded for {}", id, ex);
            }
            return page(resource, formPrefix, success, HttpStatus.OK);
        } catch (RuntimeException ex) {
            LOG.error("Could not email submission {}", id, ex);
            submissions.delivery(id, "failed");
            return page(resource, formPrefix,
                    "Request saved locally, but email delivery failed. Please contact us directly.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @PostMapping(value = {"/contact.php", "/contact.php.html"},
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> contact(@RequestParam Map<String, String> fields) throws IOException {
        // Older homepage copies posted quote requests to the contact URL.
        if (fields.containsKey("mobile")) {
            return quote(fields);
        }
        if (!valid(fields, "name", "email", "contact", "message")) {
            return page("contact.php.html", "<form action=\"contact.php.html\"",
                    "Please enter your name, valid email, phone number and message.", HttpStatus.BAD_REQUEST);
        }
        String id = submissions.save("contact", fields);
        return email(fields, id, "contact.php.html", "<form action=\"contact.php.html\"",
                "Thanks! Your message was sent.");
    }

    @PostMapping(value = {"/career.php", "/career.php.html"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> career(@RequestParam Map<String, String> fields,
            @RequestParam(value = "upload[]", required = false) MultipartFile resume) throws IOException {
        if (!valid(fields, "name", "email", "contact", "posting", "message")) {
            return page("career.php.html", "<form method=\"post\" role=\"form\"",
                    "Please complete all required fields with a valid email.", HttpStatus.BAD_REQUEST);
        }
        Map<String, String> saved = new HashMap<>(fields);
        try {
            saved.put("resume", submissions.saveResume(resume));
        } catch (IllegalArgumentException ex) {
            return page("career.php.html", "<form method=\"post\" role=\"form\"",
                    ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
        submissions.save("career", saved);
        return page("career.php.html", "<form method=\"post\" role=\"form\"",
                "Thanks! Your application was saved locally.", HttpStatus.OK);
    }

    private static boolean valid(Map<String, String> fields, String... required) {
        for (String field : required) {
            String value = fields.get(field);
            if (value == null || value.isBlank() || value.length() > (field.equals("message") ? 10000 : 256)) {
                return false;
            }
        }
        String email = fields.get("email");
        return email != null && EMAIL.matcher(email).matches();
    }

    private static ResponseEntity<String> page(String resource, String formPrefix, String text,
            HttpStatus status) throws IOException {
        String html;
        try (var input = new ClassPathResource("static/" + resource).getInputStream()) {
            html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String role = status.is2xxSuccessful() ? "status" : "alert";
        String notice = "<div role=\"" + role + "\" style=\"padding:14px;margin:14px 0;"
                + "background:#fff4db;color:#26221e;border-radius:6px\">" + text + "</div>";
        html = html.replace(formPrefix, notice + "\n" + formPrefix);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8")).body(html);
    }
}
