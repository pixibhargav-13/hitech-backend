package com.hitech.erp.project.service;

import com.hitech.erp.project.db.ProjectMemberEntity;
import com.hitech.erp.project.db.ProjectMemberRepository;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

  private final ProjectMemberRepository memberRepository;

  @Transactional(readOnly = true)
  public List<Long> getMemberIds(Long projectId) {
    return memberRepository.findUserIdsByProjectId(projectId);
  }

  /** Replace the full member set for a project. */
  @Transactional
  public List<Long> setMembers(Long projectId, List<Long> userIds) {
    memberRepository.deleteByProjectId(projectId);
    LinkedHashSet<Long> unique = new LinkedHashSet<>(userIds);
    for (Long userId : unique) {
      ProjectMemberEntity m = new ProjectMemberEntity();
      m.setProjectId(projectId);
      m.setUserId(userId);
      memberRepository.save(m);
    }
    return List.copyOf(unique);
  }
}
