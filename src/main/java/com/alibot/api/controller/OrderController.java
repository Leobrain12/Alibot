package com.alibot.api.controller;

import com.alibot.api.dto.OrderHistoryResponse;
import com.alibot.api.dto.OrderMediaResponse;
import com.alibot.api.dto.OrderResponse;
import com.alibot.api.dto.Requests.MasterIdRequest;
import com.alibot.api.dto.Requests.PaymentRequest;
import com.alibot.api.dto.Requests.ReasonRequest;
import com.alibot.api.dto.Requests.UpdateOrderRequest;
import com.alibot.api.security.CurrentActor;
import com.alibot.domain.MediaStage;
import com.alibot.domain.MediaType;
import com.alibot.domain.Order;
import com.alibot.domain.OrderMedia;
import com.alibot.domain.OrderStatus;
import com.alibot.domain.OrderStatusHistory;
import com.alibot.domain.User;
import com.alibot.repository.UserRepository;
import com.alibot.service.AuthenticatedActor;
import com.alibot.service.ContactAttemptService;
import com.alibot.service.MediaService;
import com.alibot.service.OrderService;
import com.alibot.service.PaymentService;
import com.alibot.service.VisitService;
import com.alibot.service.WorkReportService;
import com.alibot.service.dto.CreateOrderCommand;
import com.alibot.service.dto.MediaUploadCommand;
import com.alibot.service.dto.PriceApprovalCommand;
import com.alibot.service.dto.RescheduleCommand;
import com.alibot.service.dto.WaitingPartCommand;
import com.alibot.service.dto.WarrantyCommand;
import com.alibot.service.dto.WorkReportCommand;
import com.alibot.service.exception.ValidationException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * ТЗ п.88 — Internal API. Тот же самый OrderService, которым пользуется Telegram-бот: каждый
 * метод здесь — это ровно один вызов сервиса плюс маппинг в DTO, никакой бизнес-логики.
 */
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final WorkReportService workReportService;
    private final PaymentService paymentService;
    private final MediaService mediaService;
    private final VisitService visitService;
    private final ContactAttemptService contactAttemptService;
    private final UserRepository userRepository;
    private final CurrentActor currentActor;

    @GetMapping("/api/v1/orders")
    public List<OrderResponse> list(@RequestParam(defaultValue = "active") String view) {
        AuthenticatedActor actor = currentActor.get();
        List<Order> orders = switch (view) {
            case "history" -> orderService.listHistory(actor);
            case "unassigned" -> orderService.listUnassigned(actor);
            default -> orderService.listActive(actor);
        };
        return orders.stream().map(OrderResponse::from).toList();
    }

    /** ТЗ п.72 — по номеру/телефону/имени/адресу (q), дополнительно сужаемо по мастеру и дате визита. */
    @GetMapping("/api/v1/orders/search")
    public List<OrderResponse> search(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) UUID masterId,
                                       @RequestParam(required = false) LocalDate date) {
        return orderService.search(q, masterId, date, currentActor.get()).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/api/v1/orders/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orderService.getById(id, currentActor.get()));
    }

    /** ТЗ Figma #11 — Timeline: история статусов заказа для отображения в интерфейсе. */
    @GetMapping("/api/v1/orders/{id}/history")
    public List<OrderHistoryResponse> history(@PathVariable UUID id) {
        List<OrderStatusHistory> history = orderService.getHistory(id, currentActor.get());
        Map<UUID, String> namesById = new HashMap<>();
        return history.stream()
                .map(h -> OrderHistoryResponse.from(h, resolveName(h.getChangedByUserId(), namesById)))
                .toList();
    }

    private String resolveName(UUID userId, Map<UUID, String> cache) {
        return cache.computeIfAbsent(userId, id -> userRepository.findById(id).map(User::getName).orElse("—"));
    }

    @PostMapping("/api/v1/orders")
    public ResponseEntity<OrderResponse> create(@RequestBody CreateOrderCommand cmd) {
        Order order = orderService.create(cmd, currentActor.get());
        return ResponseEntity.status(201).body(OrderResponse.from(order));
    }

    @PatchMapping("/api/v1/orders/{id}")
    public OrderResponse update(@PathVariable UUID id, @RequestBody UpdateOrderRequest req) {
        return OrderResponse.from(orderService.updateDetails(id, req.adminComment(), req.description(), currentActor.get()));
    }

    @PostMapping("/api/v1/orders/{id}/assign")
    public OrderResponse assign(@PathVariable UUID id, @RequestBody MasterIdRequest req) {
        return OrderResponse.from(orderService.assign(id, req.masterId(), currentActor.get()));
    }

    @PostMapping("/api/v1/orders/{id}/change-master")
    public OrderResponse changeMaster(@PathVariable UUID id, @RequestBody MasterIdRequest req) {
        return OrderResponse.from(orderService.changeMaster(id, req.masterId(), currentActor.get()));
    }

    /** ТЗ п.89 — универсальный переход для шагов без дополнительных обязательных полей. */
    @PostMapping("/api/v1/orders/{id}/transition")
    public OrderResponse transition(@PathVariable UUID id, @RequestBody TransitionRequest req) {
        AuthenticatedActor actor = currentActor.get();
        OrderStatus target = OrderStatus.valueOf(req.targetStatus());
        Order order = switch (target) {
            case ACCEPTED -> orderService.acceptByMaster(id, actor);
            case ON_THE_WAY -> orderService.markOnTheWay(id, actor);
            case ARRIVED -> orderService.markArrived(id, actor);
            case DIAGNOSTICS -> orderService.startDiagnostics(id, actor);
            case NO_CONTACT -> orderService.markNoContact(id, actor);
            default -> orderService.transitionSimple(id, target, actor, req.comment());
        };
        return OrderResponse.from(order);
    }

    public record TransitionRequest(String targetStatus, String comment) {
    }

    @PostMapping("/api/v1/orders/{id}/decline")
    public OrderResponse decline(@PathVariable UUID id, @RequestBody ReasonRequest req) {
        return OrderResponse.from(orderService.declineByMaster(id, req.reason(), currentActor.get()));
    }

    @PostMapping("/api/v1/orders/{id}/diagnosis/price-approval")
    public OrderResponse diagnosisRepairable(@PathVariable UUID id, @RequestBody PriceApprovalCommand cmd) {
        return OrderResponse.from(orderService.startPriceApproval(id, cmd, currentActor.get()));
    }

    @PostMapping("/api/v1/orders/{id}/diagnosis/waiting-part")
    public OrderResponse diagnosisWaitingPart(@PathVariable UUID id, @RequestBody WaitingPartCommand cmd) {
        return OrderResponse.from(orderService.markWaitingPart(id, cmd, currentActor.get()));
    }

    @PostMapping("/api/v1/orders/{id}/diagnosis/unrepairable")
    public OrderResponse diagnosisUnrepairable(@PathVariable UUID id, @RequestBody ReasonRequest req) {
        return OrderResponse.from(orderService.markUnrepairable(id, req.reason(), currentActor.get()));
    }

    @PostMapping("/api/v1/orders/{id}/price-approval/accept")
    public OrderResponse priceAccept(@PathVariable UUID id) {
        return OrderResponse.from(orderService.approvePriceByCustomer(id, currentActor.get()));
    }

    @PostMapping("/api/v1/orders/{id}/price-approval/decline")
    public OrderResponse priceDecline(@PathVariable UUID id, @RequestBody ReasonRequest req) {
        return OrderResponse.from(orderService.declineByCustomer(id, req.reason(), currentActor.get()));
    }

    @PostMapping("/api/v1/orders/{id}/reschedule")
    public OrderResponse reschedule(@PathVariable UUID id, @RequestBody RescheduleCommand cmd) {
        return OrderResponse.from(orderService.reschedule(id, cmd, currentActor.get()));
    }

    @PostMapping("/api/v1/orders/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id, @RequestBody ReasonRequest req) {
        return OrderResponse.from(orderService.cancel(id, req.reason(), currentActor.get()));
    }

    @PostMapping("/api/v1/orders/{id}/warranty")
    public OrderResponse warranty(@PathVariable UUID id, @RequestBody WarrantyCommand cmdIn) {
        WarrantyCommand cmd = new WarrantyCommand(id, cmdIn.problem(), cmdIn.visitDate(), cmdIn.timeFrom(),
                cmdIn.timeTo(), cmdIn.masterId(), cmdIn.comment());
        return OrderResponse.from(orderService.createWarrantyOrder(cmd, currentActor.get()));
    }

    @PostMapping("/api/v1/orders/{id}/visits")
    public OrderResponse repeatVisit(@PathVariable UUID id) {
        return OrderResponse.from(visitService.startRepeatVisit(id, currentActor.get()));
    }

    @PostMapping("/api/v1/orders/{id}/contact-attempts")
    public ResponseEntity<Void> contactAttempt(@PathVariable UUID id, @RequestBody ReasonRequest req) {
        contactAttemptService.recordAttempt(id, com.alibot.domain.ContactResult.NO_ANSWER, req.reason(), currentActor.get());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/orders/{id}/work-report")
    public ResponseEntity<?> workReport(@PathVariable UUID id, @RequestBody WorkReportCommand cmd) {
        return ResponseEntity.status(201).body(workReportService.submit(id, cmd, currentActor.get()));
    }

    @GetMapping("/api/v1/orders/{id}/media")
    public List<OrderMediaResponse> listMedia(@PathVariable UUID id) {
        return mediaService.list(id, currentActor.get()).stream().map(OrderMediaResponse::from).toList();
    }

    @PostMapping("/api/v1/orders/{id}/media")
    public ResponseEntity<OrderMediaResponse> uploadMedia(@PathVariable UUID id,
                                                            @RequestPart("file") MultipartFile file,
                                                            @RequestParam MediaType mediaType,
                                                            @RequestParam MediaStage stage) {
        try {
            byte[] content = file.getBytes();
            MediaUploadCommand cmd = new MediaUploadCommand(mediaType, stage, "web-upload-" + UUID.randomUUID(),
                    null, file.getContentType(), file.getOriginalFilename(), content.length, null, null, null, content);
            OrderMedia saved = mediaService.upload(id, cmd, currentActor.get());
            return ResponseEntity.status(201).body(OrderMediaResponse.from(saved));
        } catch (java.io.IOException e) {
            throw new ValidationException("Не удалось прочитать файл");
        }
    }

    @PostMapping("/api/v1/orders/{id}/payments")
    public ResponseEntity<?> pay(@PathVariable UUID id, @RequestBody PaymentRequest req) {
        var payment = paymentService.registerPayment(id, new com.alibot.service.dto.PaymentCommand(req.amount(), req.paymentType()), currentActor.get());
        return ResponseEntity.status(201).body(payment);
    }

    /** ТЗ п.61 — «Оплачено полностью»: сумма долга считается сервером, клиенту не нужно её знать. */
    @PostMapping("/api/v1/orders/{id}/payments/full")
    public ResponseEntity<?> payFull(@PathVariable UUID id) {
        return ResponseEntity.status(201).body(paymentService.payFull(id, currentActor.get()));
    }

    @GetMapping("/api/v1/orders/{id}/payments")
    public List<?> payments(@PathVariable UUID id) {
        return paymentService.history(id, currentActor.get());
    }
}
