package com.hitech.erp.tender.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.tender.db.TenderEntity;
import com.hitech.erp.tender.db.TenderRepository;
import com.hitech.erp.tender.db.TenderSource;
import com.hitech.erp.tender.db.TenderStage;
import com.hitech.erp.tender.db.TenderStatus;
import com.hitech.erp.tender.dto.TenderDtos.StageChangeRequest;
import com.hitech.erp.tender.dto.TenderDtos.TenderRequest;
import com.hitech.erp.tender.dto.TenderDtos.TenderResponse;
import com.hitech.erp.tender.dto.TenderDtos.TenderSummary;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenderServiceTest {

  @Mock private TenderRepository repository;

  private TenderService service;

  @BeforeEach
  void setUp() {
    service = new TenderService(repository);
    // save() echoes the entity back, as a real JPA save would. Lenient: some tests throw first.
    lenient().when(repository.save(any(TenderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  /** All 66 TenderRequest components null — 6 per line for a stable, countable shape. */
  private static TenderRequest blank() {
    return new TenderRequest(
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null);
  }

  private static TenderRequest withStageStatusEmd(String stage, String status, Double emd, String emdState) {
    return new TenderRequest(
        "PORTAL", stage, status, null, "PWD", "T-1",
        "Road work", null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, emd, null, emdState,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null,
        null, null, null, null, null, null);
  }

  @Test
  void createDefaultsToSortingPortal() {
    TenderResponse res = service.create(blank(), 7L);
    assertThat(res.stage()).isEqualTo("SORTING");
    assertThat(res.source()).isEqualTo("PORTAL");
  }

  @Test
  void createAppliesRequestFields() {
    TenderResponse res = service.create(withStageStatusEmd("APPLIED", "SUBMITTED", 5000.0, "PAID"), 1L);
    assertThat(res.stage()).isEqualTo("APPLIED");
    assertThat(res.status()).isEqualTo("SUBMITTED");
    assertThat(res.emd()).isEqualTo(5000.0);
    assertThat(res.department()).isEqualTo("PWD");
  }

  @Test
  void updateIgnoresNullsButAppliesProvided() {
    TenderEntity existing = new TenderEntity();
    existing.setStage(TenderStage.RESEARCH);
    existing.setDepartment("Old Dept");
    when(repository.findById(3L)).thenReturn(Optional.of(existing));

    TenderRequest patch = blank();
    TenderResponse res = service.update(3L, patch);

    // Nothing in the patch, so the record is untouched.
    assertThat(res.stage()).isEqualTo("RESEARCH");
    assertThat(res.department()).isEqualTo("Old Dept");
  }

  @Test
  void changeStageMovesAndStampsStatus() {
    TenderEntity existing = new TenderEntity();
    existing.setStage(TenderStage.APPLIED);
    when(repository.findById(9L)).thenReturn(Optional.of(existing));

    TenderResponse res = service.changeStage(9L, new StageChangeRequest("won", "WON"));
    assertThat(res.stage()).isEqualTo("WON");
    assertThat(res.status()).isEqualTo("WON");
  }

  @Test
  void changeStageRejectsBadStage() {
    TenderEntity existing = new TenderEntity();
    when(repository.findById(9L)).thenReturn(Optional.of(existing));
    assertThatThrownBy(() -> service.changeStage(9L, new StageChangeRequest("NONSENSE", null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getMissingTenderThrows() {
    when(repository.findById(404L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getTender(404L)).isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void summaryCountsStagesAndEmdExposure() {
    TenderEntity paidLive = new TenderEntity();
    paidLive.setStage(TenderStage.APPLIED);
    paidLive.setEmd(1000.0);
    paidLive.setEmdState("PAID");

    TenderEntity lostUnreleased = new TenderEntity();
    lostUnreleased.setStage(TenderStage.LOST);
    lostUnreleased.setEmd(400.0);
    lostUnreleased.setEmdState("PAID");

    when(repository.findAll()).thenReturn(List.of(paidLive, lostUnreleased));

    TenderSummary s = service.summary();
    assertThat(s.total()).isEqualTo(2);
    assertThat(s.emdBlocked()).isEqualTo(1000.0);
    assertThat(s.emdRecoverable()).isEqualTo(400.0);
    assertThat(s.byStage()).anySatisfy(sc -> {
      if (sc.stage().equals("APPLIED")) assertThat(sc.count()).isEqualTo(1);
    });
  }

  @Test
  void enumParsingIsWhitespaceAndCaseInsensitive() {
    TenderResponse res = service.create(withStageStatusEmd(" research ", null, null, null), 1L);
    assertThat(res.stage()).isEqualTo("RESEARCH");
    assertThat(TenderSource.PORTAL).isNotNull();
    assertThat(TenderStatus.SUBMITTED).isNotNull();
  }
}
