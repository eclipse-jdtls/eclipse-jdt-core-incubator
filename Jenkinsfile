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
		jdk 'openjdk-jdk26-latest'
	}
	stages {
		stage('Build and install forked tests') {
			steps {
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
					-pl org.eclipse.jdt.core.tests.compiler,org.eclipse.jdt.core.tests.model,org.eclipse.jdt.compiler.apt.tests,org.eclipse.jdt.core.tests.builder,org.eclipse.jdt.core.tests.builder.mockcompiler,repository
				"""
			}
		}
		stage('Create composite repo') {
			steps {
				dir('repository/target/repository') {
					writeFile file: 'compositeContent.xml', text: """
						<?xml version='1.0' encoding='UTF-8'?>
						<?compositeMetadataRepository version='1.0.0'?>
						<repository name='Proxy JDT over Javac p2 repository' type='org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository' version='1'>
							<properties size='3'>
								<property name='p2.timestamp' value='1764168641397'/>
								<property name='p2.compressed' value='true'/>
								<property name='p2.atomic.composite.loading' value='true'/>
							</properties>
							<children size='1'>
								<child location='https://ci.eclipse.org/ls/job/eclipse.jdt.javac/job/main/lastSuccessfulBuild/artifact/repository/target/repository/'/>
							</children>
						</repository>
					"""
					writeFile file: 'compositeArtiacts.xml', text: """
					<?xml version='1.0' encoding='UTF-8'?>
					<?compositeArtifactRepository version='1.0.0'?>
					<repository name='Proxy JDT over Javac p2 repository' type='org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository' version='1'>
						<properties size='3'>
							<property name='p2.timestamp' value='1764168641397'/>
							<property name='p2.compressed' value='true'/>
							<property name='p2.atomic.composite.loading' value='true'/>
						</properties>
						<children size='1'>
							<child location='https://ci.eclipse.org/ls/job/eclipse.jdt.javac/job/main/lastSuccessfulBuild/artifact/repository/target/repository/'/>
						</children>
					</repository>
					"""
				}
			}
			post {
				always {
					archiveArtifacts artifacts: 'repository/target/repository/**'
				}
			}
		}
		stage('Fetch and tests Javac-based JDT') {
			steps {
				dir('eclipse.jdt.javac') {
					checkout scmGit(
						branches: [[name: 'main']],
						extensions: [ cloneOption(shallow: true) ],
						userRemoteConfigs: [[url: 'https://github.com/eclipse-jdtls/eclipse.jdt.javac.git']])
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
			}
			post {
				always {
					archiveArtifacts artifacts: '*.log,eclipse.jdt.javac/*/target/work/data/.metadata/*.log,*/tests/target/work/data/.metadata/*.log,apiAnalyzer-workspace/.metadata/*.log,repository/target/repository/**,**/target/artifactcomparison/**', allowEmptyArchive: true
					junit 'eclipse.jdt.javac/org.eclipse.jdt.core.tests.javac/target/surefire-reports/*.xml'
					discoverGitReferenceBuild referenceJob: 'jdt-core-incubator/dom-with-javac'
					//recordIssues ignoreQualityGate:true, tool: junitParser(pattern: 'org.eclipse.jdt.core.tests.javac/target/surefire-reports/*.xml'), qualityGates: [[threshold: 1, type: 'DELTA', unstable: true]]
				}
			}
		}
	}
}
