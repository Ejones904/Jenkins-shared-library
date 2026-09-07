# Jenkins Shared Library and Automated CI/CD

An event-driven Jenkins CI/CD implementation that uses a **Jenkins Shared Library** to centralize reusable pipeline logic while automating application builds, versioning, Docker image creation, registry publishing, and source-control updates.

The project also implements a CI guardrail to prevent Jenkins-generated Git commits from recursively triggering the full pipeline.

---

## Project Overview

The objective of this project was to reduce manual work and duplication across a Jenkins delivery workflow.

The implementation evolved from a basic application build into an automated pipeline that performs:

* GitHub webhook-triggered execution
* automatic Maven version increments
* Java application packaging
* reusable Jenkins Shared Library functions
* dynamic Docker image tagging
* Docker image creation
* Docker Hub authentication and publishing
* automated Git commits
* automated version updates back to GitHub
* recursive-build prevention with SCM Skip

The result is a repeatable workflow initiated by a developer `git push`.

---

## Architecture

```text
Developer
    |
    | git push
    v
GitHub
    |
    | Webhook
    v
Jenkins Pipeline
    |
    +--> Check [ci skip]
    |
    +--> Increment Maven Version
    |
    +--> Build Application
    |
    +--> Jenkins Shared Library
    |       |
    |       +--> buildJar()
    |       +--> buildImage()
    |       +--> dockerLogin()
    |       +--> dockerPush()
    |
    +--> Build Versioned Docker Image
    |
    +--> Push Image to Docker Hub
    |
    +--> Commit Updated pom.xml
    |
    v
GitHub
    |
    | Webhook
    v
Jenkins
    |
    +--> Detect [ci skip]
    |
    v
Pipeline Skipped
```

---

## Technology Stack

| Technology             | Purpose                             |
| ---------------------- | ----------------------------------- |
| Jenkins                | CI/CD orchestration                 |
| Jenkins Shared Library | Reusable pipeline logic             |
| Groovy                 | Pipeline and library implementation |
| GitHub                 | Source control                      |
| GitHub Webhooks        | Event-driven pipeline triggering    |
| Maven 3.9              | Build and application versioning    |
| Java 17                | Application runtime                 |
| Docker                 | Containerization                    |
| Docker Hub             | Container registry                  |
| Jenkins Credentials    | Secret management                   |
| Git                    | Automated version commits           |
| SCM Skip               | Recursive pipeline prevention       |
| Linux                  | Jenkins runtime environment         |

---

## Engineering Decisions

### Reusable Pipeline Logic

As the pipeline grew, repeated build and Docker operations were moved out of the Jenkinsfile and into a Jenkins Shared Library.

The library is loaded using:

```groovy
@Library('jenkins-shared-library') _
```

Reusable functions include:

```groovy
buildJar()
buildImage()
dockerLogin()
dockerPush()
```

This separates application-specific workflow orchestration from common delivery operations.

The result is a cleaner pipeline and a foundation for reusing the same CI/CD functions across additional projects.

---

### Event-Driven Execution

The workflow initially depended on manually selecting **Build Now** in Jenkins.

GitHub webhooks were introduced so that source-control changes could automatically trigger Jenkins.

```text
Developer Commit
      ↓
GitHub Push
      ↓
Webhook
      ↓
Jenkins Pipeline
```

This moves the workflow from manually initiated automation to event-driven CI.

![GitHub Webhook Success](screenshots/github-webhook-success.png)

---

### Automated Application Versioning

The Maven application version is incremented as part of the pipeline.

The version is read from `pom.xml`, incremented, and then used to generate a traceable Docker image tag.

Example:

```text
Application Version: 1.1.1
Jenkins Build:       32

Docker Image:        1.1.1-32
```

This links the container image directly to both the application version and Jenkins execution.

```text
Maven Version
      +
Jenkins Build Number
      ↓
Docker Image Tag
```

![Automatic Version Increment](screenshots/automatic-version-increment-stage.png)

---

### Dynamic Docker Artifact Naming

The pipeline generates the image identifier dynamically:

```groovy
env.IMAGE_NAME = "${version}-${BUILD_NUMBER}"
```

That value is passed into the shared-library Docker functions.

This avoids manually choosing image tags and improves artifact traceability.

![Docker Hub Versioned Image](screenshots/automatic-versioning-dockerhub.png)

---

### Source-Control Version Synchronization

Incrementing the application version only inside the Jenkins workspace would leave GitHub with an outdated `pom.xml`.

The pipeline therefore commits the updated version back to source control.

The automated commit uses:

```text
ci: version bump [ci skip]
```

This keeps the repository synchronized with the version actually produced by Jenkins.

![Jenkins Automated Version Commit](screenshots/jenkins-automated-version-commit.png)

---

## CI/CD Workflow

A normal developer change follows this path:

```text
git push
   ↓
GitHub Webhook
   ↓
Jenkins
   ↓
SCM Skip Check
   ↓
Increment Version
   ↓
Maven Build
   ↓
Dynamic Image Tag
   ↓
Docker Build
   ↓
Docker Hub Login
   ↓
Docker Push
   ↓
Commit pom.xml
   ↓
Push Version Update
```

---

## Shared Library Responsibilities

The shared library centralizes reusable delivery operations.

### Application Build

```groovy
buildJar()
```

Handles Java application packaging.

### Docker Image Creation

```groovy
buildImage(...)
```

Creates the application container image.

### Registry Authentication

```groovy
dockerLogin()
```

Uses Jenkins-managed credentials to authenticate with Docker Hub.

### Registry Publishing

```groovy
dockerPush(...)
```

Publishes the generated image to Docker Hub.

This keeps common CI/CD operations out of individual Jenkinsfiles.

---

## Troubleshooting

### Jenkins Detached HEAD During Automated Push

The first Git push attempted:

```bash
git push origin main
```

and failed.

Jenkins SCM checkout was operating from a detached HEAD rather than a normal local `main` branch.

The push was changed to:

```bash
git push origin HEAD:main
```

This explicitly pushes the currently checked-out Jenkins commit to the remote `main` branch.

---

### Recursive Pipeline Execution

Automating the Git version update introduced a new systems problem.

GitHub was configured to trigger Jenkins on every push.

Jenkins was now also performing a push.

Without a guardrail:

```text
Jenkins
   ↓
Version Commit
   ↓
GitHub
   ↓
Webhook
   ↓
Jenkins
   ↓
Another Version Commit
   ↓
...
```

Each individual component was behaving correctly, but the integrated workflow could create an infinite loop.

---

## Recursive Build Prevention

The Jenkins-generated commit includes:

```text
[ci skip]
```

The first pipeline stage checks the commit message using SCM Skip.

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

The resulting behavior is:

```text
Developer Commit
      ↓
No [ci skip]
      ↓
Full Pipeline


Jenkins Version Commit
      ↓
[ci skip]
      ↓
Pipeline Skipped
```

This provides a control mechanism around the automation rather than allowing the pipeline to continuously trigger itself.

---

## Validation

### Shared Library Workflow

The reusable pipeline functions were successfully validated through Jenkins execution.

![Jenkins Build 25 Success](screenshots/build%2025%20success.png)

The resulting container image was also confirmed in Docker Hub.

![Docker Hub Confirmation](screenshots/dockerHub-confirmation.png)

---

### Webhook Trigger

A GitHub code change automatically initiated Jenkins without manually starting the build.

![Webhook Triggered Jenkins Build](screenshots/webhook-triggered-jenkins-build.png)

---

### Versioning

The pipeline successfully incremented the Maven application version and generated a corresponding Docker image version.

![Automatic Version Increment](screenshots/automatic-version-increment-stage.png)

---

### CI Loop Protection

The completed behavior was validated across two Jenkins executions.

**Build #39**

Executed the complete workflow:

```text
GitHub Push
    ↓
Jenkins
    ↓
Version Increment
    ↓
Maven Build
    ↓
Docker Build
    ↓
Docker Push
    ↓
Git Version Commit
    ↓
SUCCESS
```

**Build #40**

The Jenkins-generated Git commit triggered another webhook, but its `[ci skip]` marker caused SCM Skip to stop the full pipeline.

```text
Build #39
Full Pipeline
    ↓
GitHub Version Commit
    ↓
Webhook
    ↓
Build #40
    ↓
SCM Skip
    ↓
Pipeline Skipped
```

![SCM Skip Validation](screenshots/scm-skip-pipeline-stage-view.png)

This validated both the automated workflow and its recursion guardrail.

---

## Security Considerations

Credentials are managed through Jenkins rather than stored directly in source control.

Jenkins Credentials are used for:

* Docker Hub authentication
* GitHub authentication

Security practices include:

* no hard-coded Docker Hub credentials
* no hard-coded GitHub Personal Access Token
* temporary credential injection
* credentials excluded from screenshots
* credentials excluded from committed build evidence

Production improvements could include:

* scoped access tokens
* automated credential rotation
* least-privilege registry permissions
* dedicated Jenkins agents
* centralized secrets management
* restricted Jenkins administration
* stronger network isolation

---

## Operational Considerations

Automating more steps also creates additional dependencies between systems.

This project required understanding interactions between:

```text
Git
GitHub
Webhooks
Jenkins
Shared Libraries
Groovy
Maven
Docker
Docker Hub
Credentials
```

The recursive-build issue is a good example: no individual service was malfunctioning. The problem only appeared because multiple successful automation components interacted in an unintended way.

---

## What This Project Demonstrates

This project demonstrates practical experience with:

* Jenkins Declarative Pipelines
* Jenkins Shared Libraries
* reusable CI/CD logic
* Pipeline as Code
* GitHub webhooks
* event-driven automation
* Maven build automation
* automated application versioning
* Docker image versioning
* Docker Hub publishing
* Jenkins credential management
* automated Git operations
* detached HEAD troubleshooting
* SCM Skip
* CI recursion prevention
* Groovy
* shell scripting
* artifact traceability
* cross-system CI/CD troubleshooting

---

## Relationship to Other Projects

This project represents a later stage of the CI/CD progression:

```text
Manual Deployment
       ↓
Jenkins Freestyle
       ↓
Jenkins Pipeline
       ↓
Reusable Shared Library
       ↓
Webhook Automation
       ↓
Automated Versioning
       ↓
CI Guardrails
       ↓
Multibranch + AWS Deployment
```

The next stage extends these patterns into the AWS multibranch deployment project.

---

## Current Limitations

This implementation does not represent a complete production delivery platform.

Areas not yet implemented here include:

* automated infrastructure provisioning
* multiple deployment environments
* rollback
* approval gates
* container vulnerability scanning
* automated production health checks
* dedicated Jenkins agents
* deployment to AWS from this specific pipeline

These capabilities can be layered onto the reusable CI foundation established here.

---

## Repository Documentation

* [`README.md`](README.md) — engineering overview and architecture
* [`IMPLEMENTATION.md`](IMPLEMENTATION.md) — detailed chronological implementation record
* [`TROUBLESHOOTING-AND-LESSONS-LEARNED.md`](TROUBLESHOOTING-AND-LESSONS-LEARNED.md) — detailed troubleshooting record

---

## Engineering Outcome

The project evolved beyond simply automating a Jenkins build.

It created an event-driven delivery workflow where a developer action:

```bash
git push
```

can initiate:

```text
Webhook
   ↓
Version Management
   ↓
Application Build
   ↓
Reusable Shared Library Logic
   ↓
Container Build
   ↓
Registry Publishing
   ↓
Source-Control Synchronization
   ↓
CI Loop Protection
```

The most important result is not only that the pipeline performs more work automatically, but that the automation includes controls to prevent unintended behavior as additional systems become connected.
