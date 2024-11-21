package com.fastcampus.pass;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@RequiredArgsConstructor
@SpringBootApplication
public class PassBatchApplication {

//	private final DataSource batchDataSource;
//
//	@Bean
//	public DataSourceTransactionManager transactionManager() {
//		return new DataSourceTransactionManager(batchDataSource);
//	}
//
//	@Bean
//	public Step passStep(JobRepository jobRepository) {
//		return new StepBuilder("passStep", jobRepository)
//				.tasklet((contribution, chunkContext) -> {
//					System.out.println("Execute PassStep");
//					return RepeatStatus.FINISHED;
//				}, transactionManager())
//				.build();
//	}
//
//	@Bean
//	public Job passJob(JobRepository jobRepository) {
//		return new JobBuilder("passJob", jobRepository)
//				.start(passStep(jobRepository))
//				.build();
//	}

	public static void main(String[] args) {
		SpringApplication.run(PassBatchApplication.class, args);
	}

}
