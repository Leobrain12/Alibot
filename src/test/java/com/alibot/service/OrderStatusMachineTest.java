package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibot.domain.OrderStatus;
import com.alibot.service.exception.InvalidTransitionException;
import org.junit.jupiter.api.Test;

/** ТЗ п.15 — таблица переходов должна быть согласованной и не пропускать невалидные переходы. */
class OrderStatusMachineTest {

    private final OrderStatusMachine machine = new OrderStatusMachine();

    @Test
    void allowsDocumentedHappyPath() {
        assertThat(machine.isAllowed(OrderStatus.NEW, OrderStatus.ASSIGNED)).isTrue();
        assertThat(machine.isAllowed(OrderStatus.ASSIGNED, OrderStatus.ACCEPTED)).isTrue();
        assertThat(machine.isAllowed(OrderStatus.ACCEPTED, OrderStatus.ON_THE_WAY)).isTrue();
        assertThat(machine.isAllowed(OrderStatus.ON_THE_WAY, OrderStatus.ARRIVED)).isTrue();
        assertThat(machine.isAllowed(OrderStatus.ARRIVED, OrderStatus.DIAGNOSTICS)).isTrue();
        assertThat(machine.isAllowed(OrderStatus.DIAGNOSTICS, OrderStatus.PRICE_APPROVAL)).isTrue();
        assertThat(machine.isAllowed(OrderStatus.PRICE_APPROVAL, OrderStatus.IN_PROGRESS)).isTrue();
        assertThat(machine.isAllowed(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED)).isTrue();
        assertThat(machine.isAllowed(OrderStatus.COMPLETED, OrderStatus.PAID)).isTrue();
    }

    @Test
    void rejectsSkippingSteps() {
        assertThat(machine.isAllowed(OrderStatus.NEW, OrderStatus.COMPLETED)).isFalse();
        assertThat(machine.isAllowed(OrderStatus.ASSIGNED, OrderStatus.PAID)).isFalse();
        assertThat(machine.isAllowed(OrderStatus.NEW, OrderStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void rejectsMovingBackwardsFromTerminalStates() {
        assertThat(machine.isAllowed(OrderStatus.PAID, OrderStatus.IN_PROGRESS)).isFalse();
        assertThat(machine.isAllowed(OrderStatus.CANCELLED, OrderStatus.NEW)).isFalse();
        assertThat(machine.allowedFrom(OrderStatus.CANCELLED)).isEmpty();
    }

    @Test
    void allowsMasterDeclineThenReassignment() {
        assertThat(machine.isAllowed(OrderStatus.ASSIGNED, OrderStatus.MASTER_DECLINED)).isTrue();
        assertThat(machine.isAllowed(OrderStatus.MASTER_DECLINED, OrderStatus.ASSIGNED)).isTrue();
    }

    @Test
    void assertTransitionAllowedThrowsOnInvalidTransition() {
        assertThatThrownBy(() -> machine.assertTransitionAllowed(OrderStatus.NEW, OrderStatus.COMPLETED))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void warrantyReturnCanBeReworkedLikeANewCycle() {
        assertThat(machine.isAllowed(OrderStatus.COMPLETED, OrderStatus.WARRANTY_RETURN)).isTrue();
        assertThat(machine.isAllowed(OrderStatus.WARRANTY_RETURN, OrderStatus.ASSIGNED)).isTrue();
    }
}
