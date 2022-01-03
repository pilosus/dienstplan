all: build up migrate

build:
	docker-compose build

up:
	docker-compose up -d

down:
	docker-compose down

migrate:
	docker-compose run --rm --no-deps dienstplan java -jar app.jar --mode migrate

rollback:
	docker-compose run --rm --no-deps dienstplan java -jar app.jar --mode rollback
