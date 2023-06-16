.PHONY: all build up down cljfmtfix cljfmtcheck eastwood check test cloverage migrate rollback

lint: eastwood cljfmtfix
all: build up migrate lint clovarage

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
	docker compose run --rm --no-deps dienstplan clojure -M:dev:test ${TEST_ARGS}

cloverage:
	docker compose run --rm --no-deps dienstplan clojure -X:dev:test:cloverage

migrate:
	docker compose run --rm --no-deps dienstplan clojure -X:migrate

rollback:
	docker compose run --rm --no-deps dienstplan clojure -X:rollback
