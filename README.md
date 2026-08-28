# Jenkins Shared Library CI/CD Pipeline with GitHub Webhooks and Automated Versioning

## Project Overview

This project demonstrates the progression of a Jenkins CI/CD pipeline from a manually triggered build into an event-driven and automatically versioned delivery workflow.

The pipeline uses a reusable Jenkins Shared Library to build a Java application with Maven, create a Docker image, authenticate securely to Docker Hub, and publish the resulting image.

GitHub webhooks were then added so that pushing code automatically triggers Jenkins.

The project was further enhanced by adding automated Maven application versioning. Jenkins now increments the application version during the pipeline, uses that version to generate a traceable Docker image tag, and commits the updated `pom.xml` back to GitHub.

Because the Jenkins-generated Git commit also triggers the GitHub webhook, SCM Skip and a `[ci skip]` commit convention were implemented to prevent an infinite CI/CD loop.

The final result is an event-driven CI/CD workflow where a normal developer push can automatically trigger the build, version, containerization, publishing, and source-control update process.

---

# Business / Engineering Problem

A CI/CD pipeline should reduce the number of manual actions required to move a code change through the build process.

Earlier versions of this project still required several manual considerations:

* Jenkins had to be triggered manually.
* Docker image versions had to be managed manually.
* Application versions were not automatically persisted.
* Build logic could become duplicated across Jenkinsfiles.
* Automated Git commits could potentially trigger recursive CI/CD executions.

The goal was to progressively remove those manual dependencies while keeping the pipeline reusable, traceable, and secure.

The resulting workflow addresses these problems through:

* Jenkins Shared Libraries
* GitHub webhooks
* Maven version automation
* Dynamic Docker image tagging
* Jenkins Credentials
* Automated Git commits
* SCM Skip
* `[ci skip]` commit conventions

---

# Final Architecture

```text
Developer
    │
    │ git push
    ▼
GitHub Repository
    │
    │ GitHub Webhook
    ▼
Jenkins
    │
    ├── Check SCM Skip
    │
    ├── Increment Maven Version
    │
    ├── Generate Dynamic Image Version
    │
    ├── Build Java Application
    │
    ├── Build Docker Image
    │
    ├── Authenticate to Docker Hub
    │
    ├── Push Docker Image
    │
    ├── Deploy Stage
    │
    └── Commit Updated pom.xml
             │
             │ HTTPS + Jenkins GitHub Credential
             ▼
        GitHub Repository
             │
             │ ci: version bump [ci skip]
             ▼
        GitHub Webhook
             │
             ▼
           Jenkins
             │
             └── SCM Skip detects [ci skip]
                         │
                         ▼
                  Pipeline Skipped
```

---

# Technology Stack

| Technology                   | Purpose                                         |
| ---------------------------- | ----------------------------------------------- |
| Jenkins                      | CI/CD automation                                |
| Jenkins Declarative Pipeline | Pipeline as Code                                |
| Jenkins Shared Library       | Reusable pipeline logic                         |
| GitHub                       | Source control                                  |
| GitHub Webhooks              | Automatic Jenkins triggering                    |
| Groovy                       | Jenkins pipeline and Shared Library development |
| Maven 3.9                    | Java build and dependency management            |
| Maven Build Helper Plugin    | Maven version parsing                           |
| Maven Versions Plugin        | Application version modification                |
| Java 17                      | Application runtime                             |
| Docker                       | Application containerization                    |
| Docker Hub                   | Container image registry                        |
| Jenkins Credentials          | Secure credential management                    |
| Git                          | Source-control automation                       |
| SCM Skip                     | CI recursion prevention                         |

---

# Repository Structure

```text
jenkins-shared-library/
│
├── Dockerfile
├── pom.xml
├── script.groovy
├── README.md
├── TROUBLESHOOTING-AND-LESSONS-LEARNED.md
│
├── screenshots/
│   ├── build 25 success.png
│   ├── dockerHub-confirmation.png
│   ├── github-webhook-success.png
│   ├── webhook-triggered-jenkins-build.png
│   ├── automatic-version-increment-stage.png
│   ├── scm-skip-pipeline-stage-view.png
│   ├── automatic-versioning-dockerhub.png
│   └── jenkins-automated-version-commit.png
│
├── src/
│   ├── Jenkinsfile
│   ├── com/
│   │   └── example/
│   │       └── Docker.groovy
│   │
│   └── main/
│       └── java/
│           └── com/
│               └── example/
│                   └── Application.java
│
└── vars/
    ├── buildImage.groovy
    ├── buildJar.groovy
    ├── dockerLogin.groovy
    └── dockerPush.groovy
```

---

# Jenkins Shared Library Design

One of the first goals of this project was separating reusable CI/CD logic from the Jenkinsfile.

Instead of putting every Maven and Docker command directly inside the pipeline, reusable functions were created under the Shared Library.

The pipeline loads the library using:

```groovy
@Library('jenkins-shared-library') _
```

Reusable functions include:

```text
buildJar()
buildImage()
dockerLogin()
dockerPush()
```

This makes the Jenkinsfile easier to read and allows common build logic to be reused across future pipelines.

---

# Shared Library Classes

Reusable Docker functionality was also organized under:

```text
src/com/example/Docker.groovy
```

This required learning how Jenkins Shared Libraries expect Groovy source files to be structured.

One of the issues encountered during development was placing `Docker.groovy` under the normal Maven Java source path.

Jenkins could not resolve:

```text
com.example.Docker
```

The file was moved into the Shared Library source structure:

```text
src/com/example/Docker.groovy
```

This allowed Jenkins to correctly load the class.

---

# Maven Build Process

The Java application is built using Maven 3.9.

The build process compiles the Java source and produces the application JAR.

The application source follows the standard Maven structure:

```text
src/main/java/com/example/Application.java
```

Earlier in the project, the source was accidentally located under:

```text
src/src/main/java/
```

This caused Maven to report:

```text
No sources to compile
```

Correcting the directory structure allowed Maven to compile the application successfully.

The compiled class was verified under:

```text
target/classes/com/example/Application.class
```

---

# Docker Build Automation

The Jenkins Shared Library handles Docker image creation instead of requiring Docker commands to be repeated throughout the Jenkinsfile.

The pipeline calls the reusable function with the dynamically generated image tag:

```groovy
buildImage "ejones904/demo-app:${env.IMAGE_NAME}"
```

Docker Hub authentication is handled separately:

```groovy
dockerLogin()
```

The resulting image is then published using:

```groovy
dockerPush "ejones904/demo-app:${env.IMAGE_NAME}"
```

Separating these operations keeps the Shared Library functions focused on individual responsibilities.

---

# Dynamic JAR Execution

Automated Maven versioning means the JAR filename changes as the application version changes.

Hardcoding a specific version into the Dockerfile would therefore break future builds.

The Dockerfile was adjusted to execute the dynamically versioned JAR using:

```dockerfile
CMD java -jar java-maven-app-*.jar
```

This removes the Docker runtime's dependency on a specific Maven version number.

As Maven generates new application versions, the container can continue to execute the resulting JAR without requiring the Dockerfile to be manually updated.

---

# GitHub Webhook Automation

Originally, the pipeline required Jenkins to be started manually after a code change.

The workflow looked like:

```text
Code Change
    ↓
Git Push
    ↓
Manually Start Jenkins
    ↓
Pipeline
```

GitHub webhooks were added to remove this manual step.

The updated workflow became:

```text
Code Change
    ↓
Git Push
    ↓
GitHub
    ↓
Webhook
    ↓
Jenkins Automatically Triggered
    ↓
Pipeline
```

---

# Jenkins Webhook Configuration

The Jenkins pipeline job was configured with:

```text
GitHub hook trigger for GITScm polling
```

This allows Jenkins to react to webhook events generated by GitHub.

---

# GitHub Webhook Configuration

A GitHub webhook was configured for the repository using the Jenkins webhook endpoint.

The webhook was configured for push events using:

```text
Content-Type: application/json
```

GitHub successfully delivered the initial webhook ping, confirming connectivity between GitHub and Jenkins.

A real Git push was then performed to validate the complete workflow.

Jenkins automatically started the pipeline without manually selecting **Build Now**.

![GitHub Webhook Success](screenshots/github-webhook-success.png)

![Webhook Triggered Jenkins Build](screenshots/webhook-triggered-jenkins-build.png)

---

# Automatic Maven Version Incrementing

After webhook automation was working, the next goal was removing manual application version management.

Before integrating the logic into Jenkins, Maven version incrementing was tested directly from the command line.

The Maven Build Helper and Versions plugins were used to parse the existing version and increment the patch version.

The logic was then moved into the Jenkins pipeline.

A new stage was added:

```groovy
stage('increment version') {
    steps {
        script {
            echo 'incrementing app version...'

            sh '''
                mvn build-helper:parse-version versions:set \
                    -DnewVersion='${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.nextIncrementalVersion}' \
                    versions:commit
            '''

            def matcher = readFile('pom.xml') =~ '<version>(.+)</version>'
            def version = matcher[0][1]

            env.IMAGE_NAME = "${version}-${BUILD_NUMBER}"

            echo "Image name: ${env.IMAGE_NAME}"
        }
    }
}
```

---

# How Automatic Versioning Works

The Maven Build Helper plugin parses the current project version.

For example:

```text
1.1.0
```

is broken into version components including:

```text
majorVersion = 1
minorVersion = 1
incrementalVersion = 0
```

The pipeline uses:

```text
parsedVersion.nextIncrementalVersion
```

to calculate the next patch version.

The result becomes:

```text
1.1.1
```

The Maven Versions plugin then updates `pom.xml` with the new application version.

This means application version management is now part of the CI/CD process rather than a manual developer task.

![Automatic Version Increment Stage](screenshots/automatic-version-increment-stage.png)

---

# Maven and Shell Quoting

One of the more interesting troubleshooting issues occurred because the pipeline combines several different interpretation layers:

```text
Groovy
   ↓
Jenkins
   ↓
Shell
   ↓
Maven
```

The Maven version expression initially caused:

```text
Bad substitution
```

The shell attempted to interpret:

```text
${parsedVersion.majorVersion}
```

as a shell variable.

However, the expression belongs to Maven.

The Maven expression was protected with shell single quotes:

```groovy
-DnewVersion='${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.nextIncrementalVersion}'
```

This allows the expression to reach Maven without `/bin/sh` trying to expand it first.

Another syntax issue involved an accidental trailing quote after:

```text
versions:commit
```

which resulted in:

```text
Syntax error: Unterminated quoted string
```

Removing the extra quote corrected the pipeline syntax.

These issues reinforced how important quoting becomes when Groovy, Jenkins, shell commands, and Maven expressions are all interacting within the same pipeline.

---

# Dynamic Docker Image Versioning

After Maven updates `pom.xml`, Jenkins reads the new application version:

```groovy
def matcher = readFile('pom.xml') =~ '<version>(.+)</version>'
def version = matcher[0][1]
```

The application version is then combined with the Jenkins build number:

```groovy
env.IMAGE_NAME = "${version}-${BUILD_NUMBER}"
```

For example:

```text
Application Version: 1.1.1
Jenkins Build:       32
```

produces:

```text
1.1.1-32
```

The Docker image can therefore be published as:

```text
ejones904/demo-app:1.1.1-32
```

This gives every image a direct relationship to both the application version and the Jenkins execution that created it.

```text
Application Version
        +
Jenkins Build Number
        ↓
Docker Image Tag
```

This improves traceability and removes the need to manually change Docker image versions between builds.

![Automatic Versioning Docker Hub](screenshots/automatic-versioning-dockerhub.png)

---

# Allowing Jenkins to Commit Version Changes

Automatically changing the Maven version inside the Jenkins workspace created another problem.

If Jenkins only modified:

```text
pom.xml
```

inside its workspace, the version change would not become part of the source repository.

A future checkout could therefore return to the previous version.

To make the version increment persistent, Jenkins was given the ability to commit the updated `pom.xml` back to GitHub.

The pipeline includes a version commit stage similar to:

```groovy
stage("commit version update") {
    steps {
        script {
            withCredentials([usernamePassword(
                credentialsId: 'Jenkins-Github',
                passwordVariable: 'PASS',
                usernameVariable: 'USER'
            )]) {

                sh 'git config --global user.name "Jenkins CI"'
                sh 'git config --global user.email "jenkins@local"'

                sh 'git status'
                sh 'git branch'
                sh 'git config --list'

                sh 'git remote set-url origin https://${USER}:${PASS}@github.com/Ejones904/Jenkins-shared-library.git'

                sh 'git add pom.xml'
                sh 'git commit -m "ci: version bump [ci skip]"'
                sh 'git push origin HEAD:main'
            }
        }
    }
}
```

Jenkins can now:

```text
Increment Version
       ↓
Modify pom.xml
       ↓
Commit pom.xml
       ↓
Push Version Update
       ↓
GitHub main
```

![Jenkins Automated Version Commit](screenshots/jenkins-automated-version-commit.png)

---

# Jenkins Git Identity

Git requires an author identity before Jenkins can create a commit.

The pipeline configures the automated Git author as:

```bash
git config --global user.name "Jenkins CI"
git config --global user.email "jenkins@local"
```

This identifies automated commits separately from developer commits.

It is important to distinguish Git identity from authentication.

These settings identify **who authored the commit**.

They do not provide Jenkins with permission to push to GitHub.

---

# GitHub Authentication

GitHub authentication is handled through Jenkins Credentials.

The existing credential:

```text
Jenkins-Github
```

stores the GitHub username and Personal Access Token.

The pipeline retrieves the credential using:

```groovy
withCredentials([usernamePassword(
    credentialsId: 'Jenkins-Github',
    passwordVariable: 'PASS',
    usernameVariable: 'USER'
)])
```

The remote can then be configured for the authenticated operation without storing the PAT directly in the Jenkinsfile.

This keeps the secret out of source control.

---

# Jenkins Detached HEAD Troubleshooting

The first automated Git push attempted:

```bash
git push origin main
```

and failed with:

```text
error: src refspec main does not match any
error: failed to push some refs
```

This exposed an important Jenkins/Git behavior.

Jenkins SCM checkout does not necessarily leave the workspace on a normal local `main` branch. The checkout can operate from a detached `HEAD`.

Instead of assuming that a local `main` branch existed, the push was changed to:

```bash
git push origin HEAD:main
```

This explicitly tells Git to take the commit currently checked out by Jenkins and push it to the remote `main` branch.

```text
Jenkins HEAD
     ↓
git push origin HEAD:main
     ↓
GitHub main
```

This resolved the automated Git push.

---

# Preventing an Infinite CI/CD Loop

Giving Jenkins permission to modify the same repository that triggers Jenkins introduced an important architectural problem.

The normal workflow is:

```text
Developer Push
      ↓
GitHub
      ↓
Webhook
      ↓
Jenkins
```

But Jenkins now also pushes its version update:

```text
Developer Push
      ↓
Jenkins Pipeline
      ↓
Increment Version
      ↓
Jenkins Commit
      ↓
Jenkins Push
      ↓
GitHub
      ↓
Webhook
      ↓
Jenkins Again
```

Without a guardrail, that second build could increment the version again, push another commit, fire another webhook, and continue indefinitely.

This meant CI recursion had to be intentionally handled.

---

# SCM Skip Implementation

The Jenkins SCM Skip plugin was implemented to solve the recursive build problem.

A new stage was placed at the beginning of the pipeline:

```groovy
stage('check for ci skip') {
    steps {
        scmSkip(
            skipPattern: '.*\\[ci skip\\].*',
            deleteBuild: false
        )
    }
}
```

The automated Jenkins commit uses:

```text
ci: version bump [ci skip]
```

Normal developer commits do not contain this marker.

Therefore, a normal push proceeds through the entire pipeline:

```text
Developer Commit
      ↓
Webhook
      ↓
Jenkins
      ↓
SCM Skip Check
      ↓
No [ci skip]
      ↓
Continue Pipeline
```

After Jenkins completes the pipeline, it creates:

```text
ci: version bump [ci skip]
```

That commit is pushed to GitHub.

GitHub still sends another webhook.

The resulting Jenkins execution sees:

```text
[ci skip]
```

and does not execute the complete CI/CD workflow.

```text
Jenkins Version Commit
      ↓
GitHub
      ↓
Webhook
      ↓
Jenkins
      ↓
SCM Skip
      ↓
[ci skip] detected
      ↓
Pipeline skipped
```

An important distinction is that SCM Skip does **not** prevent GitHub from sending the webhook.

Instead, Jenkins receives the webhook and determines that the resulting pipeline execution should be skipped.

This prevents an infinite automated version-bump cycle.

---

# SCM Skip Validation

The final behavior was validated through consecutive Jenkins executions.

**Build #39** completed the normal CI/CD pipeline successfully.

The Jenkins-generated version commit then triggered another webhook.

**Build #40** detected the `[ci skip]` marker and skipped the complete pipeline.

This demonstrated both sides of the workflow:

```text
Build #39
Normal Developer Change
      ↓
Full Pipeline
      ↓
SUCCESS

Build #40
Jenkins Version Commit
      ↓
[ci skip]
      ↓
PIPELINE SKIPPED
```

![SCM Skip Pipeline Stage View](screenshots/scm-skip-pipeline-stage-view.png)

---

# Successful Shared Library Pipeline

Before the webhook and automatic versioning enhancements were added, the underlying Shared Library pipeline was successfully validated with Jenkins **Build #25**.

That build confirmed that the reusable Jenkins Shared Library could:

* Build the Java application.
* Package the Maven artifact.
* Build a Docker image.
* Authenticate using Jenkins Credentials.
* Push the image to Docker Hub.

![Jenkins Build 25 Success](screenshots/build%2025%20success.png)

Docker Hub was also checked to verify that the resulting image had actually been published.

![Docker Hub Confirmation](screenshots/dockerHub-confirmation.png)

These earlier milestones provided the foundation for the later webhook and versioning automation.

---

# Selected Troubleshooting

This project involved significantly more troubleshooting than simply writing a Jenkinsfile.

Some of the major issues included:

### Docker Socket Permissions

Jenkins initially could not communicate with Docker through:

```text
/var/run/docker.sock
```

The Jenkins user's Linux group permissions were corrected so the containerized Jenkins instance could access the host Docker daemon.

### Incorrect Docker Hub Repository

An earlier training repository was still referenced in the pipeline, causing:

```text
push access denied
insufficient_scope
```

The image destination was changed to:

```text
ejones904/demo-app
```

### Jenkinsfile Location

Jenkins initially could not locate the pipeline definition because the Jenkinsfile was stored at:

```text
src/Jenkinsfile
```

The SCM Script Path was updated accordingly.

### `master` vs `main`

The Shared Library configuration initially attempted to retrieve:

```text
master
```

while the repository uses:

```text
main
```

Updating the branch configuration corrected the library checkout.

### Shared Library Annotation

The Shared Library required:

```groovy
@Library('jenkins-shared-library') _
```

and configuration as a trusted Global Pipeline Library.

### Maven Project Structure

Incorrect Java source placement resulted in:

```text
No sources to compile
```

Correcting the project to the standard Maven structure resolved the problem.

### Shared Library Classpath

`Docker.groovy` was originally placed under the Maven source path rather than the Jenkins Shared Library source path.

Moving it to:

```text
src/com/example/Docker.groovy
```

allowed Jenkins to resolve the class.

### Docker Image Tagging

At one point Jenkins attempted to push a Docker tag that had never been built.

The build and push responsibilities were separated and the same dynamic image name was passed through both operations.

### Missing Dockerfile

A later build progressed far enough to reveal:

```text
failed to read dockerfile: open Dockerfile: no such file or directory
```

Adding the Dockerfile at the repository root allowed the pipeline to progress.

### Maven/Shell Variable Expansion

Maven `${parsedVersion...}` expressions were initially interpreted by `/bin/sh`, producing:

```text
Bad substitution
```

Correct shell quoting allowed Maven to receive the expressions literally.

### Jenkins Detached HEAD

The automated version push initially failed because Jenkins did not have a normal local `main` branch.

Changing:

```bash
git push origin main
```

to:

```bash
git push origin HEAD:main
```

resolved the issue.

### Recursive CI/CD Execution

Allowing Jenkins to push to the same repository that triggers the pipeline introduced the possibility of an infinite webhook loop.

SCM Skip and:

```text
[ci skip]
```

were implemented as the guardrail.

More detailed troubleshooting and lessons learned are documented in:

```text
TROUBLESHOOTING-AND-LESSONS-LEARNED.md
```

---

# An Important Lesson From the Build History

One of the biggest lessons from this project was learning to treat changing errors as progress.

During troubleshooting, errors moved through several layers:

```text
Docker Permission Error
        ↓
Repository Permission Error
        ↓
Jenkinsfile / Shared Library Issues
        ↓
Maven Structure Issues
        ↓
Docker Tag Issues
        ↓
Missing Dockerfile
        ↓
Maven/Shell Quoting
        ↓
Git Push / Detached HEAD
        ↓
CI Recursion
        ↓
Successful Automated Pipeline
```

A new error did not always mean the previous change failed.

In many cases, it meant Jenkins had successfully moved farther through the pipeline and reached the next problem.

That changed how I approached troubleshooting: solve the current failure, understand why it happened, and use the next error as information about how far the system progressed.

---

# Security Considerations

Credentials are not hardcoded into the Jenkinsfile or Shared Library.

Docker Hub authentication uses Jenkins Credentials.

GitHub authentication for automated pushes also uses Jenkins Credentials and a GitHub Personal Access Token.

The repository contains only the Jenkins credential IDs required to reference those secrets.

Sensitive values such as:

* Docker Hub passwords/tokens
* GitHub Personal Access Tokens
* Jenkins credential values

should never be committed to the repository or exposed in screenshots.

---

# Key Achievements

This project now demonstrates the ability to:

* Build a reusable Jenkins Shared Library.
* Create Jenkins Declarative Pipelines.
* Automatically trigger Jenkins from GitHub pushes.
* Build Java applications using Maven.
* Automatically increment Maven application versions.
* Read application versions programmatically from `pom.xml`.
* Generate traceable Docker image tags.
* Build Docker images from Jenkins.
* Publish versioned images to Docker Hub.
* Securely handle Docker Hub credentials.
* Securely authenticate Jenkins to GitHub.
* Create Git commits from a CI/CD pipeline.
* Push automated source changes back to GitHub.
* Troubleshoot Jenkins detached HEAD behavior.
* Recognize and prevent recursive CI/CD execution.
* Implement SCM Skip as a CI/CD guardrail.
* Troubleshoot Groovy, shell, Maven, Git, Docker, and Jenkins integration issues.

---

# Skills Demonstrated

```text
Jenkins
Jenkins Declarative Pipelines
Jenkins Shared Libraries
Groovy
Git
GitHub
GitHub Webhooks
SCM
SCM Skip
CI/CD
Pipeline as Code
Maven
Maven Build Helper Plugin
Maven Versions Plugin
Semantic Versioning
Java
Docker
Docker Hub
Jenkins Credentials
GitHub Personal Access Tokens
Automated Git Commits
Automated Git Pushes
Dynamic Docker Image Tagging
Environment Variables
Linux Permissions
Shell Scripting
Credential Management
CI Recursion Prevention
Troubleshooting
Build Traceability
```

---

# Project Evolution

This project was intentionally built in stages.

```text
Jenkins Pipeline
      ↓
Reusable Shared Library
      ↓
Maven Build Automation
      ↓
Docker Build Automation
      ↓
Secure Registry Authentication
      ↓
Docker Hub Publishing
      ↓
Dynamic Build Tags
      ↓
GitHub Webhook Automation
      ↓
Automatic Maven Versioning
      ↓
Version-Based Docker Tags
      ↓
Automated Git Commits
      ↓
Automated Git Pushes
      ↓
SCM Skip / Loop Prevention
```

Each enhancement removed another manual step or addressed a problem introduced by greater automation.

---

# What I Learned

The biggest takeaway from this project was that CI/CD is much more than getting a successful build.

As I continued automating the workflow, each improvement created new engineering considerations.

Automatically triggering Jenkins removed the need to manually start builds, but meant I needed to understand webhook behavior.

Automatically incrementing the application version removed another manual step, but meant the new version needed to persist outside the Jenkins workspace.

Allowing Jenkins to push the version back to GitHub solved that problem, but introduced the possibility of Jenkins triggering itself indefinitely.

That led to implementing SCM Skip and `[ci skip]`.

I also gained a much better understanding of how several tools interact across boundaries:

```text
GitHub
   ↓
Jenkins
   ↓
Groovy
   ↓
Shell
   ↓
Maven
   ↓
Java
   ↓
Docker
   ↓
Docker Hub
   ↓
Git
   ↓
GitHub
```

The most valuable part of the project was not simply reaching a successful build. It was understanding why each failure occurred, what component was responsible, and how changes in one part of the pipeline affected everything downstream.

---

# Future Improvements

Potential future improvements include:

* Automated unit and integration testing stages.
* Static code analysis.
* Container vulnerability scanning.
* Automated deployment to a cloud environment.
* Environment-specific deployment stages.
* Git tagging for formal application releases.
* Release notes generated from source-control history.
* Artifact retention policies.
* Pipeline notifications.
* Additional approval gates for production deployment.
* Infrastructure as Code for the Jenkins environment.
* Monitoring and observability for deployed applications.

---

# Final Result

The final project progressed from a basic Jenkins pipeline into an event-driven CI/CD workflow with reusable pipeline components, secure credentials, automatic application versioning, dynamic Docker image tagging, automated source-control updates, and protection against recursive CI execution.

A normal development workflow can now begin with:

```bash
git push
```

and automatically progress through:

```text
GitHub Webhook
      ↓
Jenkins
      ↓
SCM Skip Check
      ↓
Maven Version Increment
      ↓
Java Build
      ↓
Dynamic Image Version
      ↓
Docker Build
      ↓
Docker Hub Push
      ↓
Git Version Commit
      ↓
GitHub Push
      ↓
Webhook
      ↓
SCM Skip
```

The project demonstrates not only how to automate a CI/CD pipeline, but also how to handle the operational problems that appear as more of the software delivery lifecycle becomes automated.

---

## Author

**Ethan Jones**

Cloud & DevOps Portfolio

GitHub: Ejones904

