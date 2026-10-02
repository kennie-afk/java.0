# Demo shortcuts. See README -> "Demo in ten minutes".
.PHONY: demo demo-reset demo-seed demo-down demo-status demo-observability

demo:               ## build what is missing, start the lean stack, seed it, print the logins
	./scripts/demo.sh

demo-reset:         ## discard the demo database and start again from empty
	./scripts/demo.sh reset

demo-seed:          ## re-run only the seed against a running stack (idempotent)
	./scripts/demo.sh seed

demo-down:          ## stop the stack, keep the data
	./scripts/demo.sh down

demo-status:        ## what is running and how much memory it uses
	./scripts/demo.sh status

demo-observability: ## same, plus Prometheus, Grafana, Loki and kafka-ui
	OBSERVABILITY=1 ./scripts/demo.sh
