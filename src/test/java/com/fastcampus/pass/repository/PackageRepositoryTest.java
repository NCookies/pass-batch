package com.fastcampus.pass.repository;

import com.fastcampus.pass.config.TestJpaConfig;
import com.fastcampus.pass.repository.packaze.PackageEntity;
import com.fastcampus.pass.repository.packaze.PackageRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@ActiveProfiles("test")
@Import(TestJpaConfig.class)    // 테스트에서는 Auditing 필드가 자동으로 채워지지 않기 때문에 해당 설정을 불러와야 한다.
@DataJpaTest
public class PackageRepositoryTest {

    private final PackageRepository packageRepository;
    private final EntityManager entityManager;

    @Autowired
    public PackageRepositoryTest(PackageRepository packageRepository, EntityManager entityManager) {
        this.packageRepository = packageRepository;
        this.entityManager = entityManager;
    }

    @Test
    void test_save() {
        // Given
        PackageEntity packageEntity = new PackageEntity();
        packageEntity.setPackageName("바디 챌린지 PT 12주");
        packageEntity.setPeriod(84);

        // When
        packageRepository.save(packageEntity);

        // Then
        assertThat(packageEntity.getPackageSeq())
                .isNotNull();

    }

    @Test
    void test_findByCreatedAtAfter() {
        // Given
        LocalDateTime dateTime = LocalDateTime.now().minusMinutes(1);

        PackageEntity packageEntity0 = new PackageEntity();
        packageEntity0.setPackageName("학생 전용 3개월");
        packageEntity0.setPeriod(90);
        packageRepository.save(packageEntity0);

        PackageEntity packageEntity1 = new PackageEntity();
        packageEntity1.setPackageName("학생 전용 6개월");
        packageEntity1.setPeriod(180);
        packageRepository.save(packageEntity1);

        // When
        List<PackageEntity> packageEntities = packageRepository.findByCreatedAtAfter(dateTime, PageRequest.of(0, 10, Sort.by("packageSeq").descending()));

        // Then
        assertThat(packageEntities.size()).isEqualTo(2);
        assertThat(packageEntities.get(0).getPackageSeq()).isEqualTo(packageEntity1.getPackageSeq());
        assertThat(packageEntities.get(1).getPackageSeq()).isEqualTo(packageEntity0.getPackageSeq());
    }

    @Test
    void test_updateCountAndPeriod() {
        // Given
        PackageEntity packageEntity = new PackageEntity();
        packageEntity.setPackageName("바디프로필 이벤트 4개월");
        packageEntity.setPeriod(90);
        packageRepository.save(packageEntity);

        // When
        int updateCount = packageRepository.updateCountAndPeriod(packageEntity.getPackageSeq(), 30, 120);
        
        // 영속성 컨텍스트 초기화
        // @Modifying 쿼리는 직접 데이터베이스에 반영되므로, 영속성 컨텍스트에 존재하는 엔티티와 동기화되지 않는다.
        // 때문에 `updateCountAndPeriod` 메소드로 값을 업데이트한 후,
        // 영속성 컨텍스트에 남아 있는 이전 상태가 `findById` 결과로 반환될 수 있다.
        // 이를 해결하기 위해 영속성 컨텍스트를 초기화 한 후 다시 조회한다.
        entityManager.flush();
        entityManager.clear();

        // 업데이트된 엔티티 재조회
        PackageEntity updatedPackageEntity = packageRepository.findById(packageEntity.getPackageSeq()).get();

        // Then
        assertThat(updateCount).isEqualTo(1);
        assertThat(updatedPackageEntity.getCount()).isEqualTo(30);
        assertThat(updatedPackageEntity.getPeriod()).isEqualTo(120);
    }

    @Test
    void test_delete() {
        // Given
        PackageEntity packageEntity = new PackageEntity();
        packageEntity.setCount(1);
        packageRepository.save(packageEntity);

        long previousCount = packageRepository.count();

        // When
        packageRepository.delete(packageEntity);

        // Then
        assertThat(packageRepository.count()).isEqualTo(previousCount - 1);
    }

}
