package com.alibot.service;

import com.alibot.domain.Master;
import com.alibot.repository.MasterRepository;
import com.alibot.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ТЗ п.75 — формулы статистики, зафиксированы однозначно. Единая реализация: используется и
 *  ботом (команда /stats), и REST API (GET /api/v1/stats, GET /api/v1/masters/{id}/stats). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final OrderRepository orderRepository;
    private final MasterRepository masterRepository;
    private final AccessControlService accessControl;

    public MasterStats masterStats(UUID masterId, Instant from, Instant to, AuthenticatedActor actor) {
        if (actor.isMaster() && !masterId.equals(actor.masterId())) {
            throw new com.alibot.service.exception.ForbiddenException("Нельзя смотреть статистику другого мастера");
        }
        if (actor.isAdmin() || masterId.equals(actor.masterId())) {
            Master master = masterRepository.findByIdWithUser(masterId)
                    .orElseThrow(() -> new com.alibot.service.exception.NotFoundException("Мастер не найден"));

            long assigned = orderRepository.countAssigned(masterId, from, to);
            long accepted = orderRepository.countAccepted(masterId, from, to);
            long declined = orderRepository.countDeclined(masterId, from, to);
            long completed = orderRepository.countCompleted(masterId, from, to);
            BigDecimal revenue = orderRepository.sumRevenue(masterId, from, to);
            BigDecimal partsCost = orderRepository.sumPartsCost(masterId, from, to);
            BigDecimal payout = orderRepository.sumMasterPayout(masterId, from, to);
            long warranty = orderRepository.countWarranty(masterId, from, to);
            BigDecimal warrantyCost = orderRepository.sumWarrantyCost(masterId, from, to);

            return new MasterStats(
                    master.getName(),
                    assigned,
                    accepted,
                    declined,
                    completed,
                    rate(completed, accepted),
                    rate(declined, assigned),
                    revenue,
                    completed == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP),
                    partsCost,
                    payout,
                    warranty,
                    ratePercent(warranty, completed),
                    warrantyCost
            );
        }
        throw new com.alibot.service.exception.ForbiddenException("Недостаточно прав");
    }

    public OverallStats overallStats(Instant from, Instant to, AuthenticatedActor actor) {
        accessControl.assertIsAdmin(actor);
        long created = orderRepository.countCreatedAll(from, to);
        long completed = orderRepository.countCompletedAll(from, to);
        BigDecimal revenue = orderRepository.sumRevenueAll(from, to);
        long waitingPart = orderRepository.countWaitingPartAll(from, to);
        long customerCancelled = orderRepository.countCustomerCancelledAll(from, to);
        long warranty = orderRepository.countWarrantyAll(from, to);
        BigDecimal warrantyCost = orderRepository.sumWarrantyCostAll(from, to);

        List<MasterStats> perMaster = masterRepository.findAll().stream()
                .filter(Master::isActive)
                .map(m -> masterStats(m.getId(), from, to, actor))
                .toList();

        return new OverallStats(created, completed, revenue,
                completed == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP),
                waitingPart, customerCancelled, warranty, ratePercent(warranty, completed), warrantyCost, perMaster);
    }

    private static Double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return null; // ТЗ п.75: 0 или N/A, если знаменатель 0 — здесь null трактуется как N/A на транспортном слое.
        }
        return (double) numerator / denominator;
    }

    /** ТЗ п.65 — warranty_rate: доля гарантийных возвратов от числа завершённых заказов за период,
     *  в процентах (0 при отсутствии завершённых — не N/A, т.к. "0% гарантий" осмысленное значение,
     *  в отличие от completion/decline rate, где 0 в знаменателе значит "ещё нечего считать"). */
    private static BigDecimal ratePercent(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    public record MasterStats(
            String masterName,
            long assignedOrders,
            long acceptedOrders,
            long declinedOrders,
            long completedOrders,
            Double completionRate,
            Double declineRate,
            BigDecimal revenue,
            BigDecimal avgCheck,
            BigDecimal partsCost,
            BigDecimal masterPayout,
            long warrantyOrders,
            BigDecimal warrantyRate,
            BigDecimal warrantyCost
    ) {
    }

    public record OverallStats(
            long ordersCreated,
            long completedOrders,
            BigDecimal revenue,
            BigDecimal avgCheck,
            long waitingPart,
            long customerCancellations,
            long warrantyOrders,
            BigDecimal warrantyRate,
            BigDecimal warrantyCost,
            List<MasterStats> perMaster
    ) {
    }
}
