package com.fastcampus.pass.repository.pass;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PassModelMapperTest {

    @DisplayName("PassModelMapper 동작 확인")
    @Test
    void test_toPassEntity() {
        // Given
        final LocalDateTime now = LocalDateTime.now();
        final String userId = "A1000000";

        BulkPassEntity bulkPassEntity = new BulkPassEntity();
        bulkPassEntity.setPackageSeq(1);
        bulkPassEntity.setUserGroupId("GROUP");
        bulkPassEntity.setStatus(BulkPassStatus.COMPLETED);
        bulkPassEntity.setCount(10);
        bulkPassEntity.setStartedAt(now.minusDays(60));
        bulkPassEntity.setEndedAt(now);

        // When
        final PassEntity passEntity = PassModelMapper.INSTANCE.toPassEntity(bulkPassEntity, userId);

        // Then
        assertThat(passEntity.getPackageSeq()).isEqualTo(1);
        assertThat(passEntity.getStatus()).isEqualTo(PassStatus.READY);
        assertThat(passEntity.getRemainingCount()).isEqualTo(10);
        assertThat(passEntity.getStartedAt()).isEqualTo(now.minusDays(60));
        assertThat(passEntity.getEndedAt()).isEqualTo(now);
    }

}
