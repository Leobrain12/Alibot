package com.alibot.api.controller;

import com.alibot.api.dto.LeadResponse;
import com.alibot.api.dto.OrderResponse;
import com.alibot.api.dto.Requests;
import com.alibot.api.security.CurrentActor;
import com.alibot.domain.Lead;
import com.alibot.service.LeadService;
import com.alibot.service.dto.CreateOrderCommand;
import com.alibot.service.dto.SubmitLeadCommand;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ТЗ п.10-11 — Lead: приём сырых заявок от внешних систем (сайт/CRM через X-Internal-Api-Key)
 *  и их обработка администратором (список, конвертация в Order, отклонение). */
@RestController
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;
    private final CurrentActor currentActor;

    @PostMapping("/api/v1/leads")
    public ResponseEntity<LeadResponse> submit(@RequestBody SubmitLeadCommand cmd) {
        Lead lead = leadService.submit(cmd, currentActor.get());
        return ResponseEntity.status(201).body(LeadResponse.from(lead));
    }

    @GetMapping("/api/v1/leads")
    public List<LeadResponse> list(@RequestParam(defaultValue = "pending") String view) {
        List<Lead> leads = "all".equals(view) ? leadService.listAll(currentActor.get()) : leadService.listPending(currentActor.get());
        return leads.stream().map(LeadResponse::from).toList();
    }

    @PostMapping("/api/v1/leads/{id}/convert")
    public ResponseEntity<OrderResponse> convert(@PathVariable UUID id, @RequestBody CreateOrderCommand orderFields) {
        var order = leadService.convertToOrder(id, orderFields, currentActor.get());
        return ResponseEntity.status(201).body(OrderResponse.from(order));
    }

    @PostMapping("/api/v1/leads/{id}/reject")
    public LeadResponse reject(@PathVariable UUID id, @RequestBody Requests.ReasonRequest req) {
        return LeadResponse.from(leadService.reject(id, req.reason(), currentActor.get()));
    }
}
