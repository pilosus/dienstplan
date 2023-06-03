.PHONY: lint test test-all cloverage

all: build up migrate lint test

build:
	docker compose build

up:
	docker compose up -d

down:
	docker compose down

lint:
	docker compose run --rm dienstplan lein cljfmt fix

test:
	docker compose run --rm --no-deps dienstplan lein test ${TEST_ARGS}

cloverage:
	docker compose run --rm dienstplan lein cloverage ${TEST_ARGS}

migrate:
	docker compose run --rm --no-deps dienstplan lein run --mode migrate

rollback:
	docker compose run --rm --no-deps dienstplan lein run --mode rollback
