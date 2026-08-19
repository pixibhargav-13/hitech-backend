package com.hitech.erp.procurement.api;

import com.hitech.erp.procurement.dto.ProcurementDtos.PublicQuoteRequest;
import com.hitech.erp.procurement.dto.ProcurementDtos.PublicRfqResponse;
import com.hitech.erp.procurement.service.RfqService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The supplier's side of an enquiry. No login.
 *
 * <p>Suppliers are not users of this system and never will be — a yard in Rajkot is not going to
 * create an account to send us a rate for cement. The long random token in the URL is the
 * credential, and it resolves to exactly one supplier on one enquiry, so a link that leaks exposes
 * that supplier's own quote and nothing else: not the budget rate, not a rival's prices.
 *
 * <p>This is the endpoint that decides whether procurement here replaces the tool the client is
 * paying for. Without it, every price has to be re-typed by the buyer.
 */
@RestController
@RequestMapping("/api/v1/public/rfq")
@RequiredArgsConstructor
public class PublicQuoteController {

  private final RfqService service;

  @GetMapping("/{token}")
  public ResponseEntity<PublicRfqResponse> view(@PathVariable("token") String token) {
    return ResponseEntity.ok(service.publicView(token));
  }

  @PostMapping("/{token}/quote")
  public ResponseEntity<PublicRfqResponse> submit(
      @PathVariable("token") String token, @RequestBody PublicQuoteRequest r) {
    return ResponseEntity.ok(service.publicSubmit(token, r));
  }
}
