@Grab('org.yaml:snakeyaml:1.17')

import org.yaml.snakeyaml.Yaml
import hudson.model.Computer.ListPossibleNames
import jenkins.model.Jenkins

stagingEnv = ['dco-staging']

def createPipelineJob(jobInfo) {
    envVars = Jenkins.instance.getGlobalNodeProperties()[0].getEnvVars()
    Boolean oldVersion = true
    if (Jenkins.instance.getVersion().toString() > '2.176.1') {
        oldVersion = false
    }
    pipelineJob(jobInfo.name) {
        if (jobInfo.containsKey('disabled')) {
            if (jobInfo.disabled) {
                disabled()
            }
        }
        description(jobInfo.description)
        // Cron trigger is used only when enabled specifically in environment variables
        if (jobInfo.containsKey('parameters')) {
            parameters {
                jobInfo.parameters.each { parameterInfo ->
                    if (parameterInfo.type == 'bool') {
                        booleanParam(parameterInfo.name.toString(), parameterInfo.default.toBoolean(), parameterInfo.description.toString())
                    }
                    if (parameterInfo.type == 'string') {
                        stringParam(parameterInfo.name.toString(), parameterInfo.value.toString(), parameterInfo.description.toString())
                    }
                    if (parameterInfo.type == 'choice') {
                        choiceParam(parameterInfo.name.toString(), parameterInfo.choices, parameterInfo.description.toString())
                    }
                }
            }
        }
        if (jobInfo.containsKey('concurrent')) {
            if (jobInfo['concurrent'] instanceof Boolean) {
                if (oldVersion) {
                    concurrentBuild(allowConcurrentBuild = jobInfo['concurrent'])
                } else {
                    properties {
                        disableConcurrentBuilds()
                    }
                }
            }
        }
        if (jobInfo.containsKey('quiet_period')) {
            if (jobInfo['quiet_period'] instanceof Integer) {
                quietPeriod(jobInfo['quiet_period'])
            }
        }
        if (jobInfo.containsKey('copy_artifact_permission')) {
            if (!oldVersion) {
                configure { project ->
                    project / 'properties' / 'hudson.plugins.copyartifact.CopyArtifactPermissionProperty' / 'projectNameList' {
                        'string' jobInfo['copy_artifact_permission']
                    }
                }
            }
        }
        definition {
            cps {
                script(jobInfo.pipeline_template)
                if (jobInfo.containsKey('sandbox')) {
                    if (jobInfo.sandbox) {
                        sandbox()
                    }
                }
            }
        }
    }
}

parentDirectory = new File(__FILE__).getParentFile()
templateEngine = new groovy.text.SimpleTemplateEngine()

parser = new Yaml()

jobsFolder = new File(parentDirectory, 'jobs')
jobsFiles = new FileNameFinder().getFileNames(jobsFolder.toString(), '**/*.yaml **/*.yml')

isFailed = false

jobsFiles.each { jobFile ->
    jobsConfig = parser.load(new File(jobFile).text)

    jobsConfig.jobs.each { job ->
        println "Processing job: " + job.name
        if (job.pipeline_vars.containsKey('dc') && job.pipeline_vars.dc != '') {
            jenkins.model.Jenkins.instance.computers.each { c ->
                if (c.node.labelString.split(' ').contains(job.pipeline_vars.dc)) {
                    nodeName = c.node.getNodeName()
                    nodeVDC = nodeName.split('-').first()
                    nodeDC = nodeName.split('\\.').last()

                    c.node.getChannel().call(new ListPossibleNames()).each { ipAddress ->
                        if (Jenkins.getInstance().getComputer(c.node.getNodeName()).getLog().contains(ipAddress)) {
                            nodeIpAddres = ipAddress
                        }
                    }

                    if (!job.pipeline_vars.containsKey('slave_name')) {
                        job.pipeline_vars.put('slave_name', nodeName)
                    }
                    if (!job.pipeline_vars.containsKey('slave_vdc')) {
                        job.pipeline_vars.put('slave_vdc', nodeVDC)
                    }
                    if (!job.pipeline_vars.containsKey('slave_dc')) {
                        job.pipeline_vars.put('slave_dc', nodeDC)
                    }
                    if (!job.pipeline_vars.containsKey('slave_ip_addr')) {
                        job.pipeline_vars.put('slave_ip_addr', nodeIpAddres)
                    }
                }
            }
        }
        try {
            templateText = new File(parentDirectory, job.pipeline_template).text
            job.pipeline_template = templateEngine.createTemplate(templateText).make(job.pipeline_vars).toString()
            createPipelineJob(job)
        } catch (Exception ex) {
            println "ERROR! There are failures with creating " + job.name + " job"
            println ex
            isFailed = true
        }
    }
}

if (isFailed) {
    println "====== Some jobs haven't been processed! ======"
} else {
    println "====== All jobs processed ======"
}
