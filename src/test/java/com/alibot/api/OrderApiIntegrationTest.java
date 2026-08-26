package com.alibot.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibot.domain.CommissionType;
import com.alibot.domain.Master;
import com.alibot.domain.MasterStatus;
import com.alibot.domain.Order;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.UserRepository;
import com.alibot.service.AuthenticatedActor;
import com.alibot.service.OrderService;
import com.alibot.service.dto.CreateOrderCommand;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Прогоняет заказ через РЕАЛЬНЫЙ HTTP + JSON-сериализацию (TestRestTemplate), а не только через
 * прямые вызовы сервисного слоя, как остальные интеграционные тесты. Это специально закрывает
 * дыру, из-за которой в проде вылезла LazyInitializationException на Order.master: с
 * spring.jpa.open-in-view=false Hibernate-сессия закрывается на выходе из @Transactional
 * сервисного метода, а OrderResponse.from(order) обращается к order.getMaster() уже после
 * этого — предыдущие тесты гоняли Order как объект внутри одного теста и никогда не пересекали
 * границу контроллер -> JSON, поэтому баг не проявлялся, пока не появился реальный заказ с
 * назначенным мастером.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderApiIntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private OrderService orderService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MasterRepository masterRepository;

    private static final String API_KEY = "test-api-key";

    @Test
    void listAndGetOrderWithAssignedMasterDoesNotLazyInitFail() {
        User adminUser = userRepository.save(User.builder()
                .telegramUserId(9001L).role(Role.ADMIN).name("Admin").active(true).build());
        User masterUser = userRepository.save(User.builder()
                .telegramUserId(9002L).role(Role.MASTER).name("Master").active(true).build());
        Master master = masterRepository.save(Master.builder()
                .user(masterUser).name("Ахмед").status(MasterStatus.ACTIVE)
                .commissionType(CommissionType.MANUAL).active(true).build());

        AuthenticatedActor admin = new AuthenticatedActor(adminUser.getId(), 9001L, Role.ADMIN, null);
        CreateOrderCommand cmd = new CreateOrderCommand(
                "Клиент", "+79990000002", "Холодильник", "Bosch", null, "Не морозит", null,
                "Адрес", LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
                master.getId(), null, null, null, "test");
        Order order = orderService.create(cmd, admin);
        assertThat(order.getMaster()).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", API_KEY);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> listResponse = rest.exchange(
                "/api/v1/orders?view=active", HttpMethod.GET, request, String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).contains("\"masterName\":\"Ахмед\"");

        ResponseEntity<String> getResponse = rest.exchange(
                "/api/v1/orders/" + order.getId(), HttpMethod.GET, request, String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).contains("\"masterName\":\"Ахмед\"");

        ResponseEntity<String> searchResponse = rest.exchange(
                "/api/v1/orders/search?q=" + order.getNumber(), HttpMethod.GET, request, String.class);
        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchResponse.getBody()).contains("\"masterName\":\"Ахмед\"");
    }
}
