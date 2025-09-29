#!/bin/bash

# First case: decide whether to run Maven build
case "$1" in
    -d)
        echo "Skipping Maven build"
        ;;
    *)
        mvn clean package || { echo "Maven build failed. Exiting."; exit 1; }
        ;;
esac

# Second case: decide whether to run Docker build
case "$1" in
    -b)
        echo "Skipping Docker build"
        ;;
    *)
        docker compose up --build -d
        ;;
esac
