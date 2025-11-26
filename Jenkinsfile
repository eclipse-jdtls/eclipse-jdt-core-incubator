pipeline {
	options {
		timeout(time: 90, unit: 'MINUTES')
		buildDiscarder(logRotator(numToKeepStr: (env.BRANCH_NAME == 'master' || env.BRANCH_NAME ==~ 'BETA.*') ? '100':'5', artifactNumToKeepStr: (env.BRANCH_NAME == 'master' || env.BRANCH_NAME ==~ 'BETA.*') ? '15':'2'))
		disableConcurrentBuilds(abortPrevious: true)
		timestamps()
	}
	agent {
		label "ubuntu-latest"
	}
	tools {
		maven 'apache-maven-latest'
		jdk 'openjdk-jdk25-latest'
	}
	stages {
		stage('Fetch and install forked tests') {
			steps {
				dir('forkedTests') {
					checkout scmGit(
						branches: [[name: 'dom-with-javac']],
						extensions: [ cloneOption(shallow: true) ],
						userRemoteConfigs: [[url: 'https://github.com/eclipse-jdtls/eclipse-jdt-core-incubator.git']])
					sh """#!/bin/bash -x
						mkdir -p $WORKSPACE/tmp
						
						unset JAVA_TOOL_OPTIONS
						unset _JAVA_OPTIONS
						# force qualifier to start with `z` so we identify it more easily and it always seem more recent than upstrea
						mvn install -Djava.io.tmpdir=$WORKSPACE/tmp -Dmaven.repo.local=$WORKSPACE/.m2/repository \
							-Pbree-libs \
							-Dtycho.buildqualifier.format="'z'yyyyMMdd-HHmm" \
							-Pp2-repo \
							-Djava.io.tmpdir=$WORKSPACE/tmp -Dproject.build.sourceEncoding=UTF-8 \
							-DskipTests \
							-pl org.eclipse.jdt.core.tests.compiler,org.eclipse.jdt.core.tests.model
						"""
				}
			}
		}
		stage('Build, install, tests Javac-based JDT') {
			steps {
				sh """#!/bin/bash -x
					mkdir -p $WORKSPACE/tmp
					
					unset JAVA_TOOL_OPTIONS
					unset _JAVA_OPTIONS
					# force qualifier to start with `z` so we identify it more easily and it always seem more recent than upstrea
					mvn verify --batch-mode -Djava.io.tmpdir=$WORKSPACE/tmp -Dmaven.repo.local=$WORKSPACE/.m2/repository \
						-Dtycho.buildqualifier.format="'z'yyyyMMdd-HHmm" \
						-Djava.io.tmpdir=$WORKSPACE/tmp -Dproject.build.sourceEncoding=UTF-8 \
						--fail-at-end -Ptest-on-javase-25 -Pbree-libs -DfailIfNoTests=false -DexcludedGroups=org.junit.Ignore -DproviderHint=junit47 \
						-Dmaven.test.failure.ignore=true -Dmaven.test.error.ignore=true
"""
			}
			post {
				always {
					archiveArtifacts artifacts: '*.log,*/target/work/data/.metadata/*.log,*/tests/target/work/data/.metadata/*.log,apiAnalyzer-workspace/.metadata/*.log,repository/target/repository/**,**/target/artifactcomparison/**', allowEmptyArchive: true
					junit 'org.eclipse.jdt.core.tests.javac/target/surefire-reports/*.xml'
					discoverGitReferenceBuild referenceJob: 'jdt-core-incubator/javac'
					//recordIssues ignoreQualityGate:true, tool: junitParser(pattern: 'org.eclipse.jdt.core.tests.javac/target/surefire-reports/*.xml'), qualityGates: [[threshold: 1, type: 'DELTA', unstable: true]]
				}
			}
		}
	}
}
