CREATE TABLE orders (
  id BIGSERIAL PRIMARY KEY,
  status VARCHAR(16) NOT NULL,
  total INT NOT NULL
);

INSERT INTO orders (status, total) VALUES ('NEW', 10);
INSERT INTO orders (status, total) VALUES ('PAID', 20);
INSERT INTO orders (status, total) VALUES ('NEW', 30);
