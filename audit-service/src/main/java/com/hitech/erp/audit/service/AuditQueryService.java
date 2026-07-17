package com.hitech.erp.audit.service;

import com.hitech.erp.api.audit.model.AuditActor;
import com.hitech.erp.api.audit.model.AuditFilterOptions;
import com.hitech.erp.api.audit.model.AuditLogPageResponse;
import com.hitech.erp.audit.db.AuditAction;
import com.hitech.erp.audit.db.AuditLogEntity;
import com.hitech.erp.audit.db.AuditLogRepository;
import com.hitech.erp.audit.mapper.AuditMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

  private final AuditLogRepository repository;
  private final AuditMapper mapper;

  @Transactional(readOnly = true)
  public AuditLogPageResponse getLogs(
      int page, int size, Long actorUserId, String action, String entityType, String from, String to, String q) {

    Specification<AuditLogEntity> spec =
        (root, query, cb) -> {
          List<Predicate> p = new ArrayList<>();
          if (actorUserId != null) p.add(cb.equal(root.get("actorUserId"), actorUserId));
          if (StringUtils.hasText(action)) p.add(cb.equal(root.get("action"), parseAction(action)));
          if (StringUtils.hasText(entityType)) p.add(cb.equal(root.get("entityType"), entityType));
          if (StringUtils.hasText(from)) {
            p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), LocalDate.parse(from).atStartOfDay()));
          }
          if (StringUtils.hasText(to)) {
            p.add(cb.lessThanOrEqualTo(root.get("createdAt"), LocalDate.parse(to).atTime(LocalTime.MAX)));
          }
          if (StringUtils.hasText(q)) {
            String like = "%" + q.toLowerCase() + "%";
            p.add(
                cb.or(
                    cb.like(cb.lower(root.get("summary")), like),
                    cb.like(cb.lower(root.get("path")), like),
                    cb.like(cb.lower(root.get("actorName")), like),
                    cb.like(cb.lower(root.get("actorEmail")), like)));
          }
          return cb.and(p.toArray(new Predicate[0]));
        };

    Page<AuditLogEntity> result =
        repository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

    return new AuditLogPageResponse()
        .content(mapper.toResponses(result.getContent()))
        .page(result.getNumber())
        .size(result.getSize())
        .totalElements(result.getTotalElements())
        .totalPages(result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public AuditFilterOptions getFilterOptions() {
    List<AuditActor> actors =
        repository.findDistinctActors().stream()
            .map(
                row ->
                    new AuditActor()
                        .id((Long) row[0])
                        .name((String) row[1])
                        .email((String) row[2]))
            .toList();

    return new AuditFilterOptions()
        .actions(Arrays.stream(AuditAction.values()).map(Enum::name).toList())
        .entityTypes(repository.findDistinctEntityTypes())
        .actors(actors);
  }

  private static AuditAction parseAction(String value) {
    try {
      return AuditAction.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid audit action: " + value);
    }
  }
}
