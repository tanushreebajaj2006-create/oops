package com.servicedesk.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.exception.NotificationDeliveryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Notification adapters")
class NotificationAdapterTest {

  @Test
  @DisplayName(
      "TC-76 e-mail adapter translates to the provider's format and maps errors to domain"
          + " exceptions")
  void emailAdapter() {
    SimulatedMailService provider = new SimulatedMailService();
    MailServiceEmailAdapter adapter =
        new MailServiceEmailAdapter(provider, "noreply@servicedesk.local");

    adapter.send("asha@corp.com", "INC1 resolved", "Fixed <script>alert(1)</script> & done");
    SimulatedMailService.MailRequest sent = provider.outbox().get(0);
    assertThat(sent.from()).isEqualTo("noreply@servicedesk.local");
    assertThat(sent.to()).containsExactly("asha@corp.com");
    assertThat(sent.htmlContent())
        .isEqualTo("<p>Fixed &lt;script&gt;alert(1)&lt;/script&gt; &amp; done</p>");

    provider.setOnline(false);
    assertThatThrownBy(() -> adapter.send("asha@corp.com", "x", "y"))
        .isInstanceOf(NotificationDeliveryException.class)
        .hasMessageContaining("503");
    assertThat(provider.outbox()).hasSize(1);
  }

  @Test
  @DisplayName("TC-77 SMS adapter converts +E.164 numbers to MSISDN and reports gateway rejections")
  void smsAdapter() {
    SimulatedSmsGateway gateway = new SimulatedSmsGateway();
    SmsGatewayAdapter adapter = new SmsGatewayAdapter(gateway, "SVCDSK");

    adapter.send("+919876543210", "ServiceDesk: INC1 [P1] assigned to you");
    assertThat(gateway.sent())
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.msisdn()).isEqualTo("919876543210");
              assertThat(m.senderId()).isEqualTo("SVCDSK");
            });

    assertThatThrownBy(() -> adapter.send("not-a-number", "hi"))
        .isInstanceOf(NotificationDeliveryException.class)
        .hasMessageContaining("Invalid MSISDN");
    gateway.setOnline(false);
    assertThatThrownBy(() -> adapter.send("+919876543210", "hi"))
        .isInstanceOf(NotificationDeliveryException.class)
        .hasMessageContaining("offline");
  }
}
