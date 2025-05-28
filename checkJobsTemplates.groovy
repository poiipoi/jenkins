#!/usr/bin/env groovy

@Grab('org.yaml:snakeyaml:1.17')

import org.yaml.snakeyaml.Yaml

templateEngine = new groovy.text.SimpleTemplateEngine()
parser = new Yaml()

def job_to_show

if (args.size() > 0){
    job_to_show = args[0]
} else {
    job_to_show = ''
}

println "Starting testing templates:"
parentDirectory = new File(".")
jobsFolder = new File(parentDirectory, 'jobs')
jobsFiles = new FileNameFinder().getFileNames(jobsFolder.toString(), '**/*.yaml **/*.yml')
jobsFiles.each { jobFile ->
    jobsConfig = parser.load(new File(jobFile).text)

    jobsConfig.jobs.each { job ->
        println "- " + job.name
        if (job.pipeline_vars.containsKey('dc') && job.pipeline_vars.dc != '') {
            job.pipeline_vars.put('slave_name', 'localhost')
            job.pipeline_vars.put('slave_vdc', 'local')
            job.pipeline_vars.put('slave_dc', 'local')
            job.pipeline_vars.put('slave_ip_addr', '127.0.0.1')
        }

        templateText = new File(parentDirectory, job.pipeline_template).text
        job.pipeline_template = templateEngine.createTemplate(templateText).make(job.pipeline_vars).toString()

        if( job.name == job_to_show){
            println("\n\n****[ START: $job_to_show ]***8<***8<***8<***8<***8<***8<***8<***8<***8<***\n")
            println(job.pipeline_template)
            println("\n****[ END: $job_to_show ]***8<***8<***8<***8<***8<***8<***8<***8<***8<***\n\n")
        }
    }
}

println "====== No issue found with templates ======"
System.exit(0)
