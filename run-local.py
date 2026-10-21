"""Run the mirrored pages and their local quote, contact and career forms."""

import json
import os
import re
import smtplib
import ssl
import threading
import uuid
from datetime import datetime, timezone
from email.message import EmailMessage
from email.parser import BytesParser
from email.policy import default
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

SITE = Path(__file__).resolve().parent / "src/main/resources/static"
DATA = Path(os.environ.get("DATA_DIR", "./data"))
LOCK = threading.Lock()
EMAIL = re.compile(r"^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$")
FIELDS = {"name", "company", "email", "mobile", "contact", "service", "posting", "message", "resume"}


def truthy(name, default_value):
    return os.environ.get(name, default_value).lower() in {"1", "true", "yes"}


def record(filename, row):
    with LOCK:
        DATA.mkdir(parents=True, exist_ok=True)
        with (DATA / filename).open("a", encoding="utf-8") as output:
            output.write(json.dumps(row, ensure_ascii=False) + "\n")


def save(kind, fields):
    ident = str(uuid.uuid4())
    record("submissions.jsonl", {
        "id": ident,
        "type": kind,
        "submittedAt": datetime.now(timezone.utc).isoformat(),
        "fields": {key: str(value).strip() for key, value in fields.items() if key in FIELDS},
    })
    return ident


def delivery(ident, state):
    record("mail-delivery.jsonl", {
        "submissionId": ident, "state": state,
        "at": datetime.now(timezone.utc).isoformat(),
    })


def required(fields, *names):
    return all(
        fields.get(key, "").strip() and len(fields[key]) <= (10000 if key == "message" else 256)
        for key in names
    ) and bool(EMAIL.fullmatch(fields.get("email", "")))


def mail_is_configured():
    return all(os.environ.get(key) for key in ("SMTP_HOST", "MAIL_FROM", "QUOTE_TO_EMAIL")) and (
        not truthy("SMTP_AUTH", "true")
        or all(os.environ.get(key) for key in ("SMTP_USERNAME", "SMTP_PASSWORD"))
    )


def send_quote(fields, ident):
    message = EmailMessage()
    message["From"] = os.environ["MAIL_FROM"]
    message["To"] = os.environ["QUOTE_TO_EMAIL"]
    message["Reply-To"] = fields["email"]
    message["Subject"] = "New Get Quote request" if "mobile" in fields else "New Contact request"
    message.set_content(
        f"Submission ID: {ident}\n\n"
        + "\n".join(f"{label}: {fields.get(key, '')}" for label, key in (
            ("Name", "name"), ("Company", "company"), ("Email", "email"),
            ("Phone", "mobile" if "mobile" in fields else "contact"),
            ("Service", "service"), ("Message", "message")
        ))
    )
    port = int(os.environ.get("SMTP_PORT", "587"))
    connection_type = smtplib.SMTP_SSL if truthy("SMTP_SSL", "false") else smtplib.SMTP
    with connection_type(os.environ["SMTP_HOST"], port, timeout=5) as smtp:
        if truthy("SMTP_STARTTLS", "true") and not truthy("SMTP_SSL", "false"):
            smtp.starttls(context=ssl.create_default_context())
        if truthy("SMTP_AUTH", "true"):
            smtp.login(os.environ["SMTP_USERNAME"], os.environ["SMTP_PASSWORD"])
        smtp.send_message(message)


class LocalSite(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(SITE), **kwargs)

    def do_GET(self):
        request_url = urlsplit(self.path)
        path = request_url.path
        if path == "/blog-details.php":
            blog_id = parse_qs(request_url.query).get("id", ["1"])[0]
            self.path = "/blog-details-" + ("2" if blog_id == "2" else "1") + ".html"
        elif path.endswith(".php") and not (SITE / path.lstrip("/")).exists():
            self.path = path + ".html"
        super().do_GET()

    def do_POST(self):
        path = urlsplit(self.path).path
        if path not in {"/quote", "/contact.php", "/contact.php.html", "/career.php", "/career.php.html"}:
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        size = int(self.headers.get("Content-Length", "0"))
        if size < 1 or size > 5 * 1024 * 1024:
            self.send_error(HTTPStatus.REQUEST_ENTITY_TOO_LARGE)
            return
        body = self.rfile.read(size)
        if path.startswith("/career.php"):
            if not self.headers.get("Content-Type", "").startswith("multipart/form-data;"):
                self.send_error(HTTPStatus.UNSUPPORTED_MEDIA_TYPE)
                return
            request = BytesParser(policy=default).parsebytes(
                b"Content-Type: " + self.headers["Content-Type"].encode() + b"\r\nMIME-Version: 1.0\r\n\r\n" + body
            )
            fields = {}
            resume = None
            for part in request.iter_parts():
                key = part.get_param("name", header="content-disposition")
                if key == "upload[]":
                    resume = part
                elif key in FIELDS:
                    fields[key] = part.get_content()
            self.career(fields, resume)
        else:
            if not self.headers.get("Content-Type", "").startswith("application/x-www-form-urlencoded"):
                self.send_error(HTTPStatus.UNSUPPORTED_MEDIA_TYPE)
                return
            fields = {key: values[0] for key, values in
                      parse_qs(body.decode("utf-8"), keep_blank_values=True).items()}
            if path == "/quote" or "mobile" in fields:
                self.quote(fields)
            else:
                self.contact(fields)

    def quote(self, fields):
        page, marker = "index.html", '<form class="form-fields"'
        if not required(fields, "name", "email", "mobile", "service"):
            self.result(page, marker, "Please enter a name, valid email, mobile number and service.", 400)
            return
        ident = save("quote", fields)
        self.email(fields, ident, page, marker, "Thanks! Your quote request was sent.")

    def email(self, fields, ident, page, marker, success):
        if not mail_is_configured():
            delivery(ident, "not_configured")
            self.result(page, marker, "Request saved locally, but email is not configured. Ask the site owner to configure SMTP and the recipient address.", 503)
            return
        try:
            send_quote(fields, ident)
        except (OSError, smtplib.SMTPException, ValueError) as exc:
            self.log_error("Could not email submission %s: %s", ident, exc)
            delivery(ident, "failed")
            self.result(page, marker, "Request saved locally, but email delivery failed. Please contact us directly.", 503)
            return
        try:
            delivery(ident, "sent")
        except OSError as exc:
            self.log_error("Email sent, but delivery status was not recorded for %s: %s", ident, exc)
        self.result(page, marker, success, 200)

    def contact(self, fields):
        page, marker = "contact.php.html", '<form action="contact.php.html"'
        if not required(fields, "name", "email", "contact", "message"):
            self.result(page, marker, "Please enter your name, valid email, phone number and message.", 400)
            return
        ident = save("contact", fields)
        self.email(fields, ident, page, marker, "Thanks! Your message was sent.")

    def career(self, fields, resume):
        page, marker = "career.php.html", '<form method="post" role="form"'
        if not required(fields, "name", "email", "contact", "posting", "message"):
            self.result(page, marker, "Please complete all required fields with a valid email.", 400)
            return
        if resume and resume.get_filename():
            ext = Path(resume.get_filename()).suffix.lower()
            content = resume.get_payload(decode=True)
            if ext not in {".pdf", ".doc", ".docx"} or len(content) > 4 * 1024 * 1024:
                self.result(page, marker, "Resume must be a PDF, DOC or DOCX file under 4 MB.", 400)
                return
            uploads = DATA / "uploads"
            uploads.mkdir(parents=True, exist_ok=True)
            filename = str(uuid.uuid4()) + ext
            (uploads / filename).write_bytes(content)
            fields["resume"] = filename
        save("career", fields)
        self.result(page, marker, "Thanks! Your application was saved locally.", 200)

    def result(self, page, marker, notice, status):
        html = (SITE / page).read_text(encoding="utf-8")
        role = "status" if status == 200 else "alert"
        banner = (
            f'<div role="{role}" style="padding:14px;margin:14px 0;'
            f'background:#fff4db;color:#26221e;border-radius:6px">{notice}</div>'
        )
        html = html.replace(marker, banner + "\n" + marker)
        content = html.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 8080), LocalSite)
    print("Open http://localhost:8080/  (Ctrl+C to stop)", flush=True)
    server.serve_forever()
