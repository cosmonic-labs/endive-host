SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := help

COMPOSE := docker compose -f deploy/k3s/docker-compose.yml
KUBECONFIG_FILE := deploy/k3s/tmp/kubeconfig.yaml
KUBECTL := kubectl --kubeconfig=$(KUBECONFIG_FILE) --insecure-skip-tls-verify

GATEWAY_PORT ?= 8000
ENDIVE_HOST_PORT ?= 8081
REGISTRY_PORT ?= 5050

export GATEWAY_PORT ENDIVE_HOST_PORT REGISTRY_PORT

.PHONY: help build demo down

help: ## List targets
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  %-8s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build: ## Build host + vertx-demo (mvn package, -DskipTests)
	mvn -B -DskipTests package

demo: build ## Bring up the stack, push hello.wasm, apply the Workload, curl it
	$(COMPOSE) up -d --build
	@echo "==> waiting for kubeconfig from k3s"
	@for i in $$(seq 1 60); do [ -f $(KUBECONFIG_FILE) ] && break; sleep 2; done
	@[ -f $(KUBECONFIG_FILE) ] || { echo "kubeconfig never appeared; check '$(COMPOSE) logs kubernetes'"; exit 1; }
	@echo "==> pushing hello.wasm to localhost:$(REGISTRY_PORT)"
	cd examples && oras push --plain-http localhost:$(REGISTRY_PORT)/hello:demo \
		hello.wasm:application/vnd.wasm.content.layer.v1+wasm
	@echo "==> applying examples/workload.yaml"
	$(KUBECTL) apply -f examples/workload.yaml
	@echo "==> waiting for Workload Ready"
	$(KUBECTL) wait --for=condition=Ready workload/hello --timeout=120s
	@echo "==> GET http://localhost:$(ENDIVE_HOST_PORT)/hi"
	@curl -sS http://localhost:$(ENDIVE_HOST_PORT)/hi

down: ## Tear down stack and wipe k3s state
	-$(COMPOSE) down -v
	rm -rf deploy/k3s/tmp
