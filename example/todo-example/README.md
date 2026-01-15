# Todo Example (Spring Boot)

A minimal Todo REST API built with Spring Boot 3 and Lumen ORM.

## Run

```bash
mvn -pl example/todo-example -am spring-boot:run
```

The API will start at `http://localhost:8080`. Open `http://localhost:8080/` for the UI.

## Endpoints

- `POST /todos` create a todo
- `GET /todos/{id}` get detail
- `PUT /todos/{id}` update
- `DELETE /todos/{id}` delete
- `GET /todos?page=1&pageSize=20&completed=true` list with pagination
- `GET /` simple UI page

## Sample Requests

```bash
curl -X POST http://localhost:8080/todos \
  -H 'Content-Type: application/json' \
  -d '{"title":"Ship v1","description":"release","completed":false}'

curl "http://localhost:8080/todos?page=1&pageSize=10"
```
