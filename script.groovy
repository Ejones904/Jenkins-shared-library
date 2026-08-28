

def deployApp() {
    echo 'deploying the application...'
    sh 'mvn clean package'
}

return this

