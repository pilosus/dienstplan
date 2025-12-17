.PHONY: all build up down cljfmtfix cljfmtcheck eastwood check test cloverage migrate rollback depscheck depsbump vuln deps clean revcount docs

lint: eastwood cljfmtfix
all: build up migrate lint cloverage

build:
	docker compose build

up:
	docker compose up -d

down:
	docker compose down

uberjar:
	clojure -T:build uberjar

repl:
	clojure -M:dev:repl

cljfmtfix:
	docker compose run --rm --no-deps dienstplan clojure -X:dev:cljfmtfix

cljfmtcheck:
	docker compose run --rm --no-deps dienstplan clojure -X:dev:cljfmtcheck

eastwood:
	docker compose run --rm --no-deps dienstplan clojure -M:dev:eastwood

check:
	docker compose run --rm --no-deps dienstplan clojure -M:check

test:
# example with make args:
# make TEST_ARGS=":vars '[dienstplan.db-test/test-rota-update\!]'" test
# or inside container:
# clojure -X:dev:test :vars '[dienstplan.db-test/test-rota-update!]'
# clojure -X:dev:test :nses '[dienstplan.api-test]'
# clojure -X:dev:test :includes '[:integration]'
	docker compose run --rm --no-deps dienstplan clojure -X:dev:test ${TEST_ARGS}

cloverage:
	docker compose run --rm --no-deps dienstplan clojure -X:dev:test:cloverage ${TEST_ARGS}

migrate:
	docker compose run --rm --no-deps dienstplan clojure -X:migrate

rollback:
	docker compose run --rm --no-deps dienstplan clojure -X:rollback

schedule:
	docker compose run --rm --no-deps dienstplan clojure -X:schedule

daemon:
	docker compose run --rm --no-deps dienstplan clojure -X:schedule-daemon

depscheck:
	clojure -T:outdated

depsbump:
	clojure -T:outdated :upgrade true :force true

# Don't forget to install as a tool first
# Set CLJ_WATSON_NVD_API_KEY,
#     CLJ_WATSON_ANALYZER_OSSINDEX_USER,
#     CLJ_WATSON_ANALYZER_OSSINDEX_PASSWORD
vuln:
	clojure -Tclj-watson scan :deps-edn-path deps.edn

deps:
	clojure -X:deps prep

clean:
	clojure -T:build clean

revcount:
	@git rev-list HEAD --count
docs:
	mkdocs serve
