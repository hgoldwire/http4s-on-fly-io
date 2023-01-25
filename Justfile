docker_image_name := "hello-server"
docker_image_tag  := "0.1.0"
base_image        := "eclipse-temurin:11.0.17_8-jre-alpine"

_default:
  @just --list --unsorted

# Runs the app on localhost:8080
run:
  BASE_URL="https://hello.toniogela.dev" scala-cli run .

# Build the docker image
build:
  scala-cli package server.scala --docker \
    --docker-image-repository {{docker_image_name}} \
    --docker-image-tag {{docker_image_tag}} \
    --docker-from {{base_image}}

# Deploys on fly.io
deploy: build
    flyctl deploy --local-only

# Changes the TITLE secret on fly.io
title label="Hello":
    flyctl secrets set TITLE="{{label}}"

# Opens the web UI of fly.io
open:
    open "https://fly.io/apps/hello-toniogela/"
