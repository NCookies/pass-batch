package com.fastcampus.pass.job.statistics;

import com.fastcampus.pass.repository.booking.BookingEntity;
import com.fastcampus.pass.repository.statistics.StatisticsEntity;
import com.fastcampus.pass.repository.statistics.StatisticsRepository;
import com.fastcampus.pass.util.LocalDateTimeUtils;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class MakeStatisticsJobConfig {
    private final int CHUNK_SIZE = 10;

    private final DataSource batchDataSource;
    private final EntityManagerFactory entityManagerFactory;
    private final StatisticsRepository statisticsRepository;
    private final MakeDailyStatisticsTasklet makeDailyStatisticsTasklet;
    private final MakeWeeklyStatisticsTasklet makeWeeklyStatisticsTasklet;

    @Bean
    public DataSourceTransactionManager transactionManager() {
        return new DataSourceTransactionManager(batchDataSource);
    }

    /*
     * 통계 생성 job은 3개의 step으로 구성되어 있다.
     * 1. 통계 데이터 추출
     * 2. 일간 통계 집계
     * 3. 주간 통계 집계
     *
     * 2, 3번 step은 서로 의존성을 갖지 않고 독립적으로 시행되도 괜찮기 때문에 병렬로 수행한다.
     */
    @Bean
    public Job makeStatisticsJob(JobRepository jobRepository) {
        Flow addStatisticsFlow = new FlowBuilder<Flow>("addStatisticsFlow")
                .start(addStatisticsStep(jobRepository))
                .build();

        Flow makeDailyStatisticsFlow = new FlowBuilder<Flow>("makeDailyStatisticsFlow")
                .start(makeDailyStatisticsStep(jobRepository))
                .build();

        Flow makeWeeklyStatisticsFlow = new FlowBuilder<Flow>("makeWeeklyStatisticsFlow")
                .start(makeWeeklyStatisticsStep(jobRepository))
                .build();

        Flow parallelMakeStatisticsFlow = new FlowBuilder<Flow>("parallelMakeStatisticsFlow")
                .split(new SimpleAsyncTaskExecutor())
                .add(makeDailyStatisticsFlow, makeWeeklyStatisticsFlow)
                .build();

        return new JobBuilder("makeStatisticsJob", jobRepository)
                .start(addStatisticsFlow)
                .next(parallelMakeStatisticsFlow)
                .build()
                .build();
    }

    @Bean
    public Step addStatisticsStep(JobRepository jobRepository) {
        return new StepBuilder("addStatisticsStep", jobRepository)
                .<BookingEntity, BookingEntity>chunk(CHUNK_SIZE, transactionManager())
                .reader(addStatisticsItemReader(null, null))
                .writer(addStatisticsItemWriter())
                .build();
    }

    @Bean
    @StepScope
    public JpaCursorItemReader<BookingEntity> addStatisticsItemReader(@Value("#{jobParameters[from]}") String fromString, @Value("#{jobParameters[to]}") String toString) {
        final LocalDateTime from = LocalDateTimeUtils.parse(fromString);
        final LocalDateTime to = LocalDateTimeUtils.parse(toString);

        return new JpaCursorItemReaderBuilder<BookingEntity>()
                .name("usePassesItemReader")
                .entityManagerFactory(entityManagerFactory)
                // JobParameter를 받아 종료 일시(endedAt) 기준으로 통계 대상 예약(Booking)을 조회합니다.
                .queryString("select b from BookingEntity b where b.endedAt between :from and :to")
                .parameterValues(Map.of("from", from, "to", to))
                .build();
    }

    @Bean
    public ItemWriter<BookingEntity> addStatisticsItemWriter() {
        return bookingEntities -> {
            Map<LocalDateTime, StatisticsEntity> statisticsEntityMap = new LinkedHashMap<>();

            for (BookingEntity bookingEntity : bookingEntities) {
                final LocalDateTime statisticsAt = bookingEntity.getStatisticsAt();
                StatisticsEntity statisticsEntity = statisticsEntityMap.get(statisticsAt);

                if (statisticsEntity == null) {
                    // 기존에 저장되어 있는 통계 데이터가 없다면 새로 생성
                    statisticsEntityMap.put(statisticsAt, StatisticsEntity.create(bookingEntity));

                } else {
                    // 있다면 기존 데이터에 추가로 합산
                    statisticsEntity.add(bookingEntity);

                }

            }
            final List<StatisticsEntity> statisticsEntities = new ArrayList<>(statisticsEntityMap.values());
            statisticsRepository.saveAll(statisticsEntities);
            log.info("### addStatisticsStep 종료");

        };
    }

    @Bean
    public Step makeDailyStatisticsStep(JobRepository jobRepository) {
        return new StepBuilder("makeDailyStatisticsStep", jobRepository)
                .tasklet(makeDailyStatisticsTasklet, transactionManager())
                .build();
    }

    @Bean
    public Step makeWeeklyStatisticsStep(JobRepository jobRepository) {
        return new StepBuilder("makeWeeklyStatisticsStep", jobRepository)
                .tasklet(makeWeeklyStatisticsTasklet, transactionManager())
                .build();
    }

}
