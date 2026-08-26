package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibot.domain.Lead;
import com.alibot.domain.LeadStatus;
import com.alibot.domain.Order;
import com.alibot.domain.Role;
import com.alibot.domain.User;
import com.alibot.repository.UserRepository;
import com.alibot.service.dto.CreateOrderCommand;
import com.alibot.service.dto.SubmitLeadCommand;
import com.alibot.service.exception.ForbiddenException;
import com.alibot.service.exception.ValidationException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ТЗ п.10-11 — Lead (маркетинговая заявка) и её конвертация в Order. Раньше в проекте
 * сознательно не было отдельной сущности — только nullable lead_id/crm_id/source на Order;
 * теперь есть полноценный жизненный цикл: пришёл -> конвертирован в заказ либо отклонён.
 */
@SpringBootTest
@ActiveProfiles("test")
class LeadServiceTest {

    @Autowired
    private LeadService leadService;
    @Autowired
    private UserRepository userRepository;

    private AuthenticatedActor admin(long telegramId) {
        User user = userRepository.save(User.builder()
                .telegramUserId(telegramId).role(Role.ADMIN).name("Admin").active(true).build());
        return new AuthenticatedActor(user.getId(), telegramId, Role.ADMIN, null);
    }

    private AuthenticatedActor masterActor(long telegramId) {
        User user = userRepository.save(User.builder()
                .telegramUserId(telegramId).role(Role.MASTER).name("Master").active(true).build());
        return new AuthenticatedActor(user.getId(), telegramId, Role.MASTER, null);
    }

    @Test
    void submitCreatesLeadVisibleInPendingList() {
        AuthenticatedActor admin = admin(1401L);
        Lead lead = leadService.submit(new SubmitLeadCommand(
                "Ирина Соколова", "+79990001401", "Холодильник", "Не морозит, звонила с сайта",
                "website", "ext-1401"), admin);

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.NEW);
        assertThat(leadService.listPending(admin)).extracting(Lead::getId).contains(lead.getId());
    }

    @Test
    void submitRejectsBlankContact() {
        AuthenticatedActor admin = admin(1402L);
        assertThatThrownBy(() -> leadService.submit(
                new SubmitLeadCommand("", "+79990001402", null, null, null, null), admin))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> leadService.submit(
                new SubmitLeadCommand("Имя", "  ", null, null, null, null), admin))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void masterCannotSubmitOrListLeads() {
        AuthenticatedActor master = masterActor(1403L);
        assertThatThrownBy(() -> leadService.submit(
                new SubmitLeadCommand("Клиент", "+79990001403", null, null, null, null), master))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> leadService.listPending(master)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void convertCreatesOrderLinkedToLeadAndMarksConverted() {
        AuthenticatedActor admin = admin(1404L);
        Lead lead = leadService.submit(new SubmitLeadCommand(
                "Пётр Волков", "+79990001404", "Стиральная машина", "Не сливает", "avito", null), admin);

        CreateOrderCommand orderFields = new CreateOrderCommand(
                lead.getCustomerName(), lead.getCustomerPhone(), "Стиральная машина", "Bosch", null,
                "Не сливает воду", null, "ул. Тестовая, 1", LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), LocalTime.of(12, 0), null, null, null, null, null);
        Order order = leadService.convertToOrder(lead.getId(), orderFields, admin);

        assertThat(order.getLeadId()).isEqualTo(lead.getId().toString());
        assertThat(order.getSource()).isEqualTo("avito"); // унаследован от лида, раз не передан явно

        var reloaded = leadService.listAll(admin).stream()
                .filter(l -> l.getId().equals(lead.getId())).findFirst().orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(LeadStatus.CONVERTED);
        assertThat(reloaded.getConvertedOrderId()).isEqualTo(order.getId());
        // Конвертированный лид больше не в списке ожидающих обработки.
        assertThat(leadService.listPending(admin)).extracting(Lead::getId).doesNotContain(lead.getId());
    }

    @Test
    void cannotConvertOrRejectAlreadyProcessedLead() {
        AuthenticatedActor admin = admin(1405L);
        Lead lead = leadService.submit(new SubmitLeadCommand(
                "Спам-заявка", "+79990001405", null, null, null, null), admin);
        leadService.reject(lead.getId(), "Спам", admin);

        assertThatThrownBy(() -> leadService.reject(lead.getId(), "Повторно", admin))
                .isInstanceOf(ValidationException.class);

        CreateOrderCommand orderFields = new CreateOrderCommand(
                "Кто-то", "+79990009999", "Холодильник", null, null, "Проблема", null,
                "Адрес", LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0),
                null, null, null, null, null);
        assertThatThrownBy(() -> leadService.convertToOrder(lead.getId(), orderFields, admin))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectRequiresNonBlankReason() {
        AuthenticatedActor admin = admin(1406L);
        Lead lead = leadService.submit(new SubmitLeadCommand(
                "Клиент", "+79990001406", null, null, null, null), admin);
        assertThatThrownBy(() -> leadService.reject(lead.getId(), "  ", admin))
                .isInstanceOf(ValidationException.class);
    }
}
