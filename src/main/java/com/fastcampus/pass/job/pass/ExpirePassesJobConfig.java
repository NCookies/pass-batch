package com.fastcampus.pass.job.pass;

import com.fastcampus.pass.repository.pass.PassEntity;
import com.fastcampus.pass.repository.pass.PassStatus;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;

@RequiredArgsConstructor
@Configuration
public class ExpirePassesJobConfig {

    private final int CHUNK_SIZE = 5;

    private final DataSource batchDataSource;
    private final EntityManagerFactory entityManagerFactory;

	@Bean
	public DataSourceTransactionManager transactionManager() {
		return new DataSourceTransactionManager(batchDataSource);
	}

    @Bean
    public Job expirePassesJob(JobRepository jobRepository) {
        return new JobBuilder("expirePassesJob", jobRepository)
                .start(expirePassesStep(jobRepository))
                .build();
    }

    @Bean
    public Step expirePassesStep(JobRepository jobRepository) {
        return new StepBuilder("expirePassesStep", jobRepository)
                .<PassEntity, PassEntity>chunk(CHUNK_SIZE, transactionManager())
                .reader(expirePassesItemReader())
                .processor(expirePassesItemProcessor())
                .writer(expirePassesItemWriter())
                .build();
    }

    /*
     * JpaCursorItemReader: JpaPagingITemReader 만 지원하다가 Spring 4.3에서 추가됐다.
     * 페이징 기법보다 높은 성능을 가졌으며, 데이터 변경에 무관한 무결성 조회가 가능하다.
     * 여기서 페이징 기법을 사용하게 되면 데이터가 누락될 수 있다.
     */
    @Bean
    @StepScope
    public JpaCursorItemReader<PassEntity> expirePassesItemReader() {
        return new JpaCursorItemReaderBuilder<PassEntity>()
                .name("expirePassesItemReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT p from PassEntity p where p.status = :status AND p.endedAt <= :endedAt")
                .parameterValues(Map.of("status", PassStatus.IN_PROGRESS, "endedAt", LocalDateTime.now()))
                .build();
    }

    @Bean
    public ItemProcessor<PassEntity, PassEntity> expirePassesItemProcessor() {
        return passEntity -> {
            passEntity.setStatus(PassStatus.EXPIRED);
            passEntity.setExpiredAt(LocalDateTime.now());
            return passEntity;
        };
    }

    /*
     * JpaItemWriter: JPA의 영속성 관리를 위해 EntityManager를 필수로 설정해줘야 한다.
     */
    @Bean
    public ItemWriter<PassEntity> expirePassesItemWriter() {
        return new JpaItemWriterBuilder<PassEntity>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

}
