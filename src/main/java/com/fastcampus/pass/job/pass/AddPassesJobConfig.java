package com.fastcampus.pass.job.pass;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@RequiredArgsConstructor
@Configuration
public class AddPassesJobConfig {
    private final DataSource batchDataSource;
    private final AddPassesTasklet addPassesTasklet;

    @Bean
    public DataSourceTransactionManager transactionManager() {
        return new DataSourceTransactionManager(batchDataSource);
    }

    @Bean
    public Job addPassesJob(JobRepository jobRepository) {
        return new JobBuilder("addPassesJob", jobRepository)
                .start(addPassesStep(jobRepository))
                .build();
    }

    @Bean
    public Step addPassesStep(JobRepository jobRepository) {
        return new StepBuilder("addPassesStep", jobRepository)
                .tasklet(addPassesTasklet, transactionManager())
                .build();
    }

}
