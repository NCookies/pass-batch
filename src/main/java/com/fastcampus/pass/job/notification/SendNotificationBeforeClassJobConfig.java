package com.fastcampus.pass.job.notification;

import com.fastcampus.pass.repository.booking.BookingEntity;
import com.fastcampus.pass.repository.booking.BookingStatus;
import com.fastcampus.pass.repository.notification.NotificationEntity;
import com.fastcampus.pass.repository.notification.NotificationEvent;
import com.fastcampus.pass.repository.notification.NotificationModelMapper;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.support.SynchronizedItemStreamReader;
import org.springframework.batch.item.support.builder.SynchronizedItemStreamReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;

@RequiredArgsConstructor
@Configuration
public class SendNotificationBeforeClassJobConfig {

    private final int CHUNK_SIZE = 10;

    private final DataSource batchDataSource;
    private final EntityManagerFactory entityManagerFactory;
    private final SendNotificationItemWriter sendNotificationItemWriter;

    @Bean
    public DataSourceTransactionManager transactionManager() {
        return new DataSourceTransactionManager(batchDataSource);
    }

    @Bean
    public Job sendNotificationBeforeClassJob(JobRepository jobRepository) {
        return new JobBuilder("sendNotificationBeforeClassJob", jobRepository)
                .start(addNotificationStep(jobRepository))
                .next(sendNotificationStep(jobRepository))
                .build();
    }

    @Bean
    public Step addNotificationStep(JobRepository jobRepository) {
        return new StepBuilder("addNotificationStep", jobRepository)
                .<BookingEntity, NotificationEntity>chunk(CHUNK_SIZE, transactionManager())
                .reader(addNotificationItemReader())
                .processor(addNotificationItemProcessor())
                .writer(addNotificationItemWriter())
                .build();
    }

    /*
     * JpaPagingItemReader: JPA에서 사용하는 페이징 기법
     * 쿼리 당 pageSize만큼 가져오며 다른 PagingItemReader와 마찬가지로 Thread-safe 하다.
     * 여기서는 item을 조회만 하기 때문에 cursor가 아닌 page 방식을 사용할 수 있다.
     */
    @Bean
    public JpaPagingItemReader<BookingEntity> addNotificationItemReader() {
        return new JpaPagingItemReaderBuilder<BookingEntity>()
                .name("addNotificationItemReader")
                .entityManagerFactory(entityManagerFactory)
                // pageSize: 한 번에 조회할 row 수
                .pageSize(CHUNK_SIZE)
                // 상태(status)가 준비중이며, 시작일시(startedAt)이 10분 후 시작하는 예약이 알람 대상이 됩니다.
                .queryString("select b from BookingEntity b join fetch b.userEntity " +
                        "where b.status = :status and b.startedAt <= :startedAt order by b.bookingSeq")
                .parameterValues(Map.of("status", BookingStatus.READY, "startedAt", LocalDateTime.now().plusMinutes(10)))
                .build();
    }

    @Bean
    public ItemProcessor<BookingEntity, NotificationEntity> addNotificationItemProcessor() {
        // notification event가 BEFORE_CLASS(수업 전)인 booking entity들을 notification entity로 매핑
        return bookingEntity -> NotificationModelMapper.INSTANCE.toNotificationEntity(bookingEntity, NotificationEvent.BEFORE_CLASS);
    }

    @Bean
    public JpaItemWriter<NotificationEntity> addNotificationItemWriter() {
        return new JpaItemWriterBuilder<NotificationEntity>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    public Step sendNotificationStep(JobRepository jobRepository) {
        return new StepBuilder("sendNotificationStep", jobRepository)
                .<NotificationEntity, NotificationEntity>chunk(CHUNK_SIZE, transactionManager())
                .reader(sendNotificationItemReader())
                .writer(sendNotificationItemWriter)
                .taskExecutor(new SimpleAsyncTaskExecutor())
                .build();
    }

    /*
     * SynchronizedItemStreamReader: multi-thread 환경에서 reader와 writer는 thread-safe 해야한다.
     * Cursor 기법의 ItemReader는 thread-safe하지 않아 Paging 기법을 사용하거나 synchronized 를 선언하여 순차적으로 수행해야한다.
     *
     * 여기서는 데이터를 조회하고 알림을 보내고 나면 해당 데이터의 `sent` 필드를 업데이트 해야 한다.
     * 이 경우에 paging 기법을 사용하면 데이터가 누락될 수 있으므로 SynchronizedItemStreamReader를 사용한다.
     */
    @Bean
    public SynchronizedItemStreamReader<NotificationEntity> sendNotificationItemReader() {
        JpaCursorItemReader<NotificationEntity> itemReader = new JpaCursorItemReaderBuilder<NotificationEntity>()
                .name("sendNotificationItemReader")
                .entityManagerFactory(entityManagerFactory)
                // 이벤트(event)가 수업 전이며, 발송 여부(sent)가 미발송인 알람이 조회 대상이 됩니다.
                .queryString("select n from NotificationEntity n where n.event = :event and n.sent = :sent")
                .parameterValues(Map.of("event", NotificationEvent.BEFORE_CLASS, "sent", false))
                .build();

        return new SynchronizedItemStreamReaderBuilder<NotificationEntity>()
                .delegate(itemReader)
                .build();

    }
}
