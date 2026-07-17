package com.hitech.erp.task.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Attachment metadata. We persist the file name/size/type and an optional data URL (small files
 * inlined from the browser) rather than standing up blob storage — enough for a working attachments
 * feature end-to-end.
 */
@Getter
@Setter
@Entity
@Table(name = "task_attachments")
public class TaskAttachmentEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "task_id", nullable = false)
  private TaskEntity task;

  @Column(name = "uploaded_by", nullable = false)
  private Long uploadedBy;

  @Column(nullable = false, length = 300)
  private String name;

  @Column(name = "size_label", length = 40)
  private String sizeLabel;

  @Column(name = "content_type", length = 120)
  private String contentType;

  @Column(name = "data_url", columnDefinition = "text")
  private String dataUrl;
}
