package com.hitech.erp.project.service;

import com.hitech.erp.api.project.model.ProjectCreateRequest;
import com.hitech.erp.api.project.model.ProjectPageResponse;
import com.hitech.erp.api.project.model.ProjectResponse;
import com.hitech.erp.api.project.model.ProjectUpdateRequest;
import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.project.db.ProjectEntity;
import com.hitech.erp.project.db.ProjectHealth;
import com.hitech.erp.project.db.ProjectRepository;
import com.hitech.erp.project.db.ProjectStatus;
import com.hitech.erp.project.mapper.ProjectMapper;
import com.hitech.erp.task.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
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
public class ProjectService {

  private final ProjectRepository projectRepository;
  private final ProjectMapper mapper;
  private final AccessService accessService;

  @Transactional(readOnly = true)
  public ProjectPageResponse getProjects(int page, int size, String status, String q) {
    Specification<ProjectEntity> spec = buildSpec(status, q);
    Page<ProjectEntity> result =
        projectRepository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

    return new ProjectPageResponse()
        .content(mapper.toResponses(result.getContent()))
        .page(result.getNumber())
        .size(result.getSize())
        .totalElements(result.getTotalElements())
        .totalPages(result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public ProjectResponse getProjectById(Long id) {
    return mapper.toResponse(requireProject(id));
  }

  @Transactional
  public ProjectResponse createProject(ProjectCreateRequest request) {
    ProjectEntity project = new ProjectEntity();
    project.setName(request.getName());
    project.setAddress(request.getAddress());
    project.setCity(request.getCity());
    project.setStatus(ProjectStatus.NOT_STARTED);
    project.setHealth(ProjectHealth.HEALTHY);
    return mapper.toResponse(projectRepository.save(project));
  }

  @Transactional
  public ProjectResponse updateProject(Long id, ProjectUpdateRequest r) {
    ProjectEntity p = requireProject(id);

    if (r.getProjectCode() != null) p.setProjectCode(r.getProjectCode());
    if (r.getName() != null) p.setName(r.getName());
    if (r.getCategory() != null) p.setCategory(r.getCategory());
    if (r.getStage() != null) p.setStage(r.getStage());
    if (r.getStatus() != null) p.setStatus(parseStatus(r.getStatus()));
    if (r.getHealth() != null) p.setHealth(parseHealth(r.getHealth()));
    if (r.getCustomerName() != null) p.setCustomerName(r.getCustomerName());
    if (r.getKeyPersonnel() != null) p.setKeyPersonnel(r.getKeyPersonnel());
    if (r.getAddress() != null) p.setAddress(r.getAddress());
    if (r.getCity() != null) p.setCity(r.getCity());
    if (r.getCompanyBranch() != null) p.setCompanyBranch(r.getCompanyBranch());
    if (r.getStartDate() != null) p.setStartDate(r.getStartDate());
    if (r.getEndDate() != null) p.setEndDate(r.getEndDate());
    if (r.getProgress() != null) p.setProgress(r.getProgress());
    if (r.getAttendanceRadius() != null) p.setAttendanceRadius(r.getAttendanceRadius());
    if (r.getProjectValue() != null) p.setProjectValue(r.getProjectValue());
    if (r.getOrientation() != null) p.setOrientation(r.getOrientation());
    if (r.getDimension() != null) p.setDimension(r.getDimension());
    if (r.getScopeOfWork() != null) p.setScopeOfWork(r.getScopeOfWork());
    if (r.getInAmount() != null) p.setInAmount(r.getInAmount());
    if (r.getOutAmount() != null) p.setOutAmount(r.getOutAmount());
    if (r.getTodoCount() != null) p.setTodoCount(r.getTodoCount());

    return mapper.toResponse(projectRepository.save(p));
  }

  @Transactional
  public void deleteProject(Long id) {
    projectRepository.delete(requireProject(id));
  }

  private ProjectEntity requireProject(Long id) {
    return projectRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Project not found: " + id));
  }

  private Specification<ProjectEntity> buildSpec(String status, String q) {
    // Non-admins only see projects they're a member of. Super Admin sees all.
    var user = CurrentUser.get();
    boolean restricted = !accessService.seesEverything(user);
    List<Long> accessibleIds = restricted ? accessService.accessibleProjectIds(user) : List.of();

    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (restricted) {
        predicates.add(
            accessibleIds.isEmpty() ? cb.disjunction() : root.get("id").in(accessibleIds));
      }
      if (StringUtils.hasText(status)) {
        predicates.add(cb.equal(root.get("status"), parseStatus(status)));
      }
      if (StringUtils.hasText(q)) {
        String like = "%" + q.toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("address")), like)));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private ProjectStatus parseStatus(String value) {
    try {
      return ProjectStatus.valueOf(value.trim().toUpperCase().replace(' ', '_').replace('-', '_'));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid project status: " + value);
    }
  }

  private ProjectHealth parseHealth(String value) {
    try {
      return ProjectHealth.valueOf(value.trim().toUpperCase().replace(' ', '_').replace('-', '_'));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid project health: " + value);
    }
  }
}
