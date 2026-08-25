#!/usr/bin/env groovy
def  call(){
    echo "building the application for branch ${env.GIT_BRANCH}"
    sh 'mvn package'
}
