#!/usr/bin/env groovy
/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2021, 2022, 2023, 2024, 2025, 2026  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-monitor-impl.
 *
 * noc-monitor-impl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-monitor-impl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-monitor-impl.  If not, see <https://www.gnu.org/licenses/>.
 */

// Parent, Extensions, Plugins, Direct and BOM Dependencies
def upstreamProjects = [
  // Parent
  '../../oss/parent', // <groupId>com.aoapps</groupId><artifactId>ao-oss-parent</artifactId>
  // Parent Plugin Dependencies (Avoid cyclic dependency)
  '../../oss/pgp-keys-map', // <groupId>com.aoapps</groupId><artifactId>pgp-keys-map</artifactId>
  '../../oss/javadoc-offline', // <groupId>com.aoapps</groupId><artifactId>ao-javadoc-offline</artifactId>
  '../../oss/javadoc-resources', // <groupId>com.aoapps</groupId><artifactId>ao-javadoc-resources</artifactId>
  '../../oss/ant-tasks', // <groupId>com.aoapps</groupId><artifactId>ao-ant-tasks</artifactId>
  '../../oss/checkstyle-config', // <groupId>com.aoapps</groupId><artifactId>ao-checkstyle-config</artifactId>

  // Direct
  '../../oss/collections', // <groupId>com.aoapps</groupId><artifactId>ao-collections</artifactId>
  '../../oss/concurrent', // <groupId>com.aoapps</groupId><artifactId>ao-concurrent</artifactId>
  '../../oss/dbc', // <groupId>com.aoapps</groupId><artifactId>ao-dbc</artifactId>
  '../../oss/hodgepodge', // <groupId>com.aoapps</groupId><artifactId>ao-hodgepodge</artifactId>
  '../../oss/lang', // <groupId>com.aoapps</groupId><artifactId>ao-lang</artifactId>
  '../../oss/net-types', // <groupId>com.aoapps</groupId><artifactId>ao-net-types</artifactId>
  '../../oss/persistence', // <groupId>com.aoapps</groupId><artifactId>ao-persistence</artifactId>
  '../../oss/sql', // <groupId>com.aoapps</groupId><artifactId>ao-sql</artifactId>
  '../../aoserv/client', // <groupId>com.aoindustries</groupId><artifactId>aoserv-client</artifactId>
  '../../aoserv/cluster', // <groupId>com.aoindustries</groupId><artifactId>aoserv-cluster</artifactId>
  // No Jenkins: <groupId>dnsjava</groupId><artifactId>dnsjava</artifactId>
  'api', // <groupId>com.aoindustries</groupId><artifactId>noc-monitor-api</artifactId>
  'portmon', // <groupId>com.aoindustries</groupId><artifactId>noc-monitor-portmon</artifactId>

  // Test Direct
  // No Jenkins: <groupId>junit</groupId><artifactId>junit</artifactId>
]

/******************************************************************************************
 *                                                                                        *
 * Everything below this line is identical for all projects, except the copied matrix     *
 * axes and any "Begin .*custom" / "End .*custom" blocks (see filter_custom script).      *
 *                                                                                        *
 *****************************************************************************************/

// Load ao-jenkins-shared-library
// TODO: Put @Library on import once we have our first library class
// TODO: Replace master with a specific tag version number once working
@Library('ao@master') _
ao.setVariables(binding, currentBuild, scm, params);

pipeline {
  agent any
  options {
    ansiColor('xterm')
    disableConcurrentBuilds(abortPrevious: true)
    quietPeriod(quietPeriod)
    skipDefaultCheckout()
    timeout(time: PIPELINE_TIMEOUT, unit: TIMEOUT_UNIT)
    // Only allowed to copy build artifacts from self
    // See https://plugins.jenkins.io/copyartifact/
    copyArtifactPermission("/${JOB_NAME}")
  }
  parameters {
    string(
      name: 'BuildPriority',
      defaultValue: "$buildPriority",
      description: """Specify the priority of this build.
Must be between 1 and 30, with lower values built first.
Defaults to project's depth in the upstream project graph."""
    )
    booleanParam(
      name: 'abortOnUnreadyDependency',
      defaultValue: true,
      description: """Aborts the build when any dependency is queued, building, or unsuccessful.
Defaults to true and will typically only be false to push a new version of a project out immediately.
May also want to set BuildPriority to \"1\" to put at the top of the build queue."""
    )
    booleanParam(
      name: 'requireLastBuild',
      defaultValue: true,
      description: """Is the last build required for the zip-timestamp-merge Ant task?
Defaults to true and will typically only be false for either the first build
or any build that adds or removes build artifacts."""
    )
    booleanParam(
      name: 'mavenDebug',
      defaultValue: false,
      description: """Enables Maven -X debug output.
Defaults to false and will typically only be true when debugging the build process itself."""
    )
  }
  triggers {
    upstream(
      threshold: hudson.model.Result.SUCCESS,
      upstreamProjects: "${prunedUpstreamProjects.join(', ')}"
    )
  }
  stages {
    stage('Setup') {
      steps {
        script {
          // Additional setup that cannot be done in options inside declarative pipeline
          ao.setupBuildDiscarder()
        }
      }
    }
    stage('Check Ready') {
      when {
        expression {
          return (params.abortOnUnreadyDependency == null) ? true : params.abortOnUnreadyDependency
        }
      }
      steps {
        script {
          ao.checkReadySteps()
        }
      }
    }
    stage('Workaround Git #27287') {
      when {
        expression {
          ao.continueCurrentBuild() && projectDir != '.' && fileExists('.gitmodules')
        }
      }
      steps {
        script {
          ao.workaroundGit27287Steps(scmUrl, scmBranch, scmBrowser, sparseCheckoutPaths, disableSubmodules)
        }
      }
    }
    stage('Checkout SCM') {
      when {
        expression {
          ao.continueCurrentBuild()
        }
      }
      steps {
        script {
          ao.checkoutScmSteps(projectDir, niceCmd, scmUrl, scmBranch, scmBrowser, sparseCheckoutPaths, disableSubmodules)
        }
      }
    }
    stage('Builds') {
      matrix {
        when {
          expression {
            ao.continueCurrentBuild()
          }
        }
        axes {
          axis {
            name 'jdk'
            values '11', '17', '21' // buildJdks
          }
        }
        stages {
          stage('Build') {
            steps {
              script {
                ao.buildSteps(projectDir, niceCmd, maven, deployJdk, mavenOpts, mvnCommon, jdk, buildPhases, testWhenExpression, testJdks)
              }
            }
          }
        }
      }
    }
    stage('Tests') {
      matrix {
        when {
          expression {
            ao.continueCurrentBuild() && testWhenExpression.call()
          }
        }
        axes {
          axis {
            name 'jdk'
            values '11', '17', '21' // buildJdks
          }
          axis {
            name 'testJdk'
            values '11', '17', '21' // testJdks
          }
        }
        stages {
          stage('Test') {
            steps {
              script {
                ao.testSteps(projectDir, niceCmd, deployJdk, maven, mavenOpts, mvnCommon, jdk, testJdk)
              }
            }
          }
        }
      }
    }
    stage('Deploy') {
      when {
        expression {
          ao.continueCurrentBuild()
        }
      }
      steps {
        script {
          ao.deploySteps(projectDir, niceCmd, deployJdk, maven, mavenOpts, mvnCommon)
        }
      }
    }
    stage('SonarQube analysis') {
      when {
        expression {
          ao.continueCurrentBuild() && sonarqubeWhenExpression.call()
        }
      }
      steps {
        script {
          ao.sonarQubeAnalysisSteps(projectDir, niceCmd, deployJdk, maven, mavenOpts, mvnCommon)
        }
      }
    }
    stage('Quality Gate') {
      when {
        expression {
          ao.continueCurrentBuild() && sonarqubeWhenExpression.call()
        }
      }
      steps {
        script {
          ao.qualityGateSteps()
        }
      }
    }
    stage('Analysis') {
      when {
        expression {
          ao.continueCurrentBuild()
        }
      }
      steps {
        script {
          ao.analysisSteps()
        }
      }
    }
  }
  post {
    failure {
      script {
        ao.postFailure(failureEmailTo)
      }
    }
  }
}
