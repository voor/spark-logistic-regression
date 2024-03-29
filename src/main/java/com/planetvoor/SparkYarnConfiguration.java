package com.planetvoor;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.hadoop.batch.scripting.ScriptTasklet;
import org.springframework.data.hadoop.scripting.HdfsScriptRunner;
import org.springframework.scripting.ScriptSource;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.data.hadoop.batch.spark.SparkYarnTasklet;

@Configuration
public class SparkYarnConfiguration {

    @Value("${example.inputDir}")
    String inputDir;
    @Value("${example.inputFileName}")
    String inputFileName;
    @Value("${example.inputLocalDir}")
    String inputLocalDir;
    @Value("${example.outputDir}")
    String outputDir;
    @Value("${example.sparkAssembly}")
    String sparkAssembly;

    @Autowired
    private org.apache.hadoop.conf.Configuration hadoopConfiguration;

    // Job definition
    @Bean
    Job tweetHashtags(JobBuilderFactory jobs, Step initScript, Step sparkTopHashtags)
            throws Exception {
        return jobs.get("TweetTopHashtags").start(initScript).next(sparkTopHashtags)
                .build();
    }

    // Step 1 - Init Script
    @Bean
    Step initScript(StepBuilderFactory steps, Tasklet scriptTasklet) throws Exception {
        return steps.get("initScript")
                .tasklet(scriptTasklet)
                .build();
    }

    @Bean
    ScriptTasklet scriptTasklet(HdfsScriptRunner scriptRunner) {
        ScriptTasklet scriptTasklet = new ScriptTasklet();
        scriptTasklet.setScriptCallback(scriptRunner);
        return scriptTasklet;
    }

    @Bean HdfsScriptRunner scriptRunner() {
        ScriptSource script = new ResourceScriptSource(new ClassPathResource("fileCopy.js"));
        HdfsScriptRunner scriptRunner = new HdfsScriptRunner();
        scriptRunner.setConfiguration(hadoopConfiguration);
        scriptRunner.setLanguage("javascript");
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("source", inputLocalDir);
        arguments.put("file", inputFileName);
        arguments.put("indir", inputDir);
        arguments.put("outdir", outputDir);
        scriptRunner.setArguments(arguments);
        scriptRunner.setScriptSource(script);
        return scriptRunner;
    }

    // Step 2 - Spark Top Hashtags
    @Bean
    Step sparkTopHashtags(StepBuilderFactory steps, Tasklet sparkTopHashtagsTasklet) throws Exception {
        return steps.get("sparkTopHashtags")
                .tasklet(sparkTopHashtagsTasklet)
                .build();
    }

    @Bean
    SparkYarnTasklet sparkTopHashtagsTasklet() throws Exception {
        SparkYarnTasklet sparkTasklet = new SparkYarnTasklet();
        sparkTasklet.setSparkAssemblyJar(sparkAssembly);
        sparkTasklet.setHadoopConfiguration(hadoopConfiguration);
        sparkTasklet.setAppClass("Hashtags");
        File jarFile = new File(System.getProperty("user.dir") + "/app/spark-hashtags_2.10-0.1.0.jar");
        sparkTasklet.setAppJar(jarFile.toURI().toString());
        sparkTasklet.setExecutorMemory("1G");
        sparkTasklet.setNumExecutors(1);
        sparkTasklet.setArguments(new String[]{
                hadoopConfiguration.get("fs.defaultFS") + inputDir + "/" + inputFileName,
                hadoopConfiguration.get("fs.defaultFS") + outputDir});
        return sparkTasklet;
    }
}
