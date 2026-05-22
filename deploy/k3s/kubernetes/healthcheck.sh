#!/bin/sh

set -x

# Check for "Ready" nodes
if [ -z "$(kubectl get nodes -o jsonpath='{.items[?(@.status.conditions[-1].type=="Ready")].metadata.name}')" ]; then
	rm -f /output/operator.yaml /output/gateway.yaml
	exit 1
fi

# Apply CRDs once.
if ! kubectl get crd hosts.runtime.wasmcloud.dev 2>&1 >/dev/null; then
	kubectl apply -f /crds/
fi

# Regenerate per-service kubeconfigs whenever they're missing. The k3s data
# volume persists across down/up, so a state where the cluster is healthy
# but the kubeconfigs were wiped on the last unready cycle has to be
# recovered here, not just on first run.
if [ ! -f /output/operator.yaml ] || [ ! -f /output/gateway.yaml ]; then
	cp /output/kubeconfig.yaml /output/in-docker.yaml
	KUBECONFIG=/output/in-docker.yaml kubectl config set-cluster default --server=https://kubernetes:6443
	cp /output/in-docker.yaml /output/operator.yaml
	cp /output/in-docker.yaml /output/gateway.yaml
fi

exit 0
