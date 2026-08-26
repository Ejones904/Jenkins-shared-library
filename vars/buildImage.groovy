#!/usr/bin/env groovy
import com.example.Docker
def call(string imageName){
    return new Docker(this).buildDockerImage(imageName)
    }

