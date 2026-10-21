# Al Safa website on localhost

This project captures the public pages and their own assets from
`https://alsafaadv.com/` as they appeared on October 10, 2026.
The original private PHP code and production database are not included.

## Run locally

From this directory, start either backend on port 8080:

```bash
python3 run-local.py
# Or, if Java 17 and Maven are installed:
mvn spring-boot:run
```

Open **http://localhost:8080/** and click **Get Quote**.
Start only one backend at a time. Both handle the quote, contact and career
forms and keep submissions in `data/submissions.jsonl`. Quote delivery
results are appended to `data/mail-delivery.jsonl`; uploaded resumes go to
`data/uploads/`. Back up `data/` if submissions matter. No database is
needed for these forms.

## Send quote requests to your inbox

Set the destination and your SMTP provider's settings **before** starting
the backend. Both Get Quote and Contact Us send to this inbox. The address submitted by the visitor is used as the email's
Reply-To; the message is sent From your authenticated mailbox.

```bash
export QUOTE_TO_EMAIL='your-inbox@example.com'
export MAIL_FROM='your-sender@example.com'
export SMTP_HOST='smtp.your-provider.example'
export SMTP_PORT='587'
export SMTP_USERNAME='your-sender@example.com'
export SMTP_PASSWORD='your-smtp-password-or-app-password'
export SMTP_AUTH='true'
export SMTP_STARTTLS='true'
export SMTP_SSL='false'
python3 run-local.py
```

For implicit TLS on port 465, set `SMTP_PORT=465`, `SMTP_SSL=true`,
`SMTP_STARTTLS=false`. Providers vary: use the SMTP endpoint, login
method and sender address supplied by yours. Keep the password in local
environment variables, not in the site or a checked-in file. On Windows
PowerShell, use `$env:QUOTE_TO_EMAIL='...'` and likewise for each variable.
The same variables work with the Java backend.

If mail is not configured or sending fails, the page explicitly says that
email was **not** sent. The request remains in `data/submissions.jsonl`; check
`data/mail-delivery.jsonl` for a matching `submissionId` with `sent`,
`failed`, or `not_configured`. There is no automatic retry. Do not assume
the email arrived until your inbox confirms it; `sent` records SMTP
acceptance, which cannot guarantee final delivery.

Public PHP URLs such as `/about.php` resolve to the captured HTML.
Some external fonts, maps, videos and social links still require internet.
# mimic-website
