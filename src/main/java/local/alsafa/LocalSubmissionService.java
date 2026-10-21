package local.alsafa;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalSubmissionService {
    private static final Set<String> FIELDS = Set.of(
            "name", "company", "email", "mobile", "contact", "service", "posting", "message", "resume");
    private final ObjectMapper json;
    private final Path directory;

    public LocalSubmissionService(ObjectMapper json, @Value("${app.data-dir:./data}") String directory) {
        this.json = json;
        this.directory = Path.of(directory);
    }

    public synchronized String save(String type, Map<String, String> submitted) throws IOException {
        String id = UUID.randomUUID().toString();
        Map<String, String> fields = new LinkedHashMap<>();
        FIELDS.stream().sorted().filter(submitted::containsKey).forEach(key ->
                fields.put(key, submitted.get(key).strip()));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("type", type);
        row.put("submittedAt", Instant.now().toString());
        row.put("fields", fields);
        append("submissions.jsonl", row);
        return id;
    }

    public synchronized void delivery(String id, String state) throws IOException {
        append("mail-delivery.jsonl", Map.of("submissionId", id,
                "state", state, "at", Instant.now().toString()));
    }

    public String saveResume(MultipartFile resume) throws IOException {
        if (resume == null || resume.isEmpty()) {
            return "";
        }
        if (resume.getSize() > 4L * 1024 * 1024) {
            throw new IllegalArgumentException("Resume exceeds the 4 MB limit.");
        }
        String original = resume.getOriginalFilename() == null ? "" : resume.getOriginalFilename();
        String extension = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT) : "";
        if (!Set.of("pdf", "doc", "docx").contains(extension)) {
            throw new IllegalArgumentException("Resume must be a PDF, DOC or DOCX file.");
        }
        Path uploads = directory.resolve("uploads");
        Files.createDirectories(uploads);
        String filename = UUID.randomUUID() + "." + extension;
        try (var input = resume.getInputStream()) {
            Files.copy(input, uploads.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        }
        return filename;
    }

    private void append(String file, Map<String, ?> row) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(file), json.writeValueAsString(row) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
