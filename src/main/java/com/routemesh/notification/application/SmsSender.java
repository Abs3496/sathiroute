package com.routemesh.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Optional Twilio SMS delivery. Credentials are supplied only through environment configuration. */
@Component
public class SmsSender {
    private static final Logger log = LoggerFactory.getLogger(SmsSender.class);
    private final RestClient client;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    public SmsSender(RestClient.Builder builder,
                     @Value("${routemesh.sms.twilio-account-sid:}") String accountSid,
                     @Value("${routemesh.sms.twilio-auth-token:}") String authToken,
                     @Value("${routemesh.sms.from-number:}") String fromNumber) {
        this.client = builder.build();
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    public void send(String phone, String message) {
        if (phone == null || phone.isBlank()) return;
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            log.warn("SMS skipped because Twilio credentials or sender number are not configured");
            return;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.put("To", List.of(phone));
        form.put("From", List.of(fromNumber));
        form.put("Body", List.of(message));
        String credentials = Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        client.post().uri("https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Basic " + credentials)
                .body(form).retrieve().toBodilessEntity();
    }
}
