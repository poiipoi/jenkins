pipeline {
    agent any
    stages {
        stage('Seeding jobs') {
            steps {
                jobDsl targets: ['createJobs.groovy'].join('\n'),
                    removedJobAction: 'DELETE',
                    removedViewAction: 'DELETE',
                    lookupStrategy: 'SEED_JOB'
                }
        }
    }
}